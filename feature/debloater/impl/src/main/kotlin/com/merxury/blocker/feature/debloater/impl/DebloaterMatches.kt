/*
 * Copyright 2025 Blocker
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.merxury.blocker.feature.debloater.impl

import com.merxury.blocker.core.data.device.ComponentStatusRepository
import com.merxury.blocker.core.data.device.DebloaterApk
import com.merxury.blocker.core.data.device.DebloaterMetadataRepository
import com.merxury.blocker.core.data.device.InstalledAppRepository
import com.merxury.blocker.core.data.device.SelectedUserRepository
import com.merxury.blocker.core.data.respository.userdata.UserDataRepository
import com.merxury.blocker.core.di.AppPackageName
import com.merxury.blocker.core.dispatchers.BlockerDispatchers.DEFAULT
import com.merxury.blocker.core.dispatchers.Dispatcher
import com.merxury.blocker.core.domain.readAtMostEvery
import com.merxury.blocker.core.model.component.AppKey
import com.merxury.blocker.core.model.component.ComponentKey
import com.merxury.blocker.core.model.component.ComponentStatus
import com.merxury.blocker.core.model.component.UserId
import com.merxury.blocker.core.model.system.InstalledApp
import com.merxury.blocker.core.result.Result
import com.merxury.blocker.core.ui.coil.ApkIcon
import com.merxury.blocker.core.ui.component.toRow
import dagger.hilt.android.scopes.ViewModelScoped
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import timber.log.Timber
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject

/** Joins the cached app and component state with the manifest classifications used by the debloater. */
@ViewModelScoped
class DebloaterMatches @Inject constructor(
    private val installedApps: InstalledAppRepository,
    private val componentStatuses: ComponentStatusRepository,
    private val metadata: DebloaterMetadataRepository,
    private val userData: UserDataRepository,
    selectedUsers: SelectedUserRepository,
    @AppPackageName appPackageName: String,
    @Dispatcher(DEFAULT) private val cpuDispatcher: CoroutineDispatcher,
) {
    private val self = AppKey(appPackageName, selectedUsers.ownUser)

    private val manifests = ConcurrentHashMap<UserId, ManifestMatches>()

    @OptIn(ExperimentalCoroutinesApi::class)
    fun forUser(user: UserId): Flow<Result<List<MatchedTarget>>> {
        val memo = manifests.getOrPut(user) { ManifestMatches() }
        val apps = combine(
            installedApps.apps(user).readAtMostEvery(CACHE_READ_PERIOD_MILLIS),
            userData.userData.map { it.showSystemApps }.distinctUntilChanged(),
            ::Pair,
        ).distinctUntilChanged().mapLatest { (cached, showSystemApps) ->
            memo.classify(user, cached, showSystemApps)
        }
        val statuses = componentStatuses.allComponents(user).readAtMostEvery(CACHE_READ_PERIOD_MILLIS)
        return combine(apps, statuses) { classified, components ->
            val rows = attachStates(classified.apps, components)
            val failure = classified.failure
            if (rows.isEmpty() && failure != null) Result.Error(failure) else Result.Success(rows)
        }.distinctUntilChanged().flowOn(cpuDispatcher)
    }

    private suspend fun attachStates(apps: List<ClassifiedApp>, statuses: List<ComponentStatus>): List<MatchedTarget> {
        val byId = statuses.associateBy { it.declaration.id }
        return apps.mapNotNull appRows@{ (app, matches) ->
            currentCoroutineContext().ensureActive()
            val rows = matches.mapNotNull { match ->
                if (ComponentClassification.LAUNCHER in match.kinds) return@mapNotNull null
                val status = byId[match.id]?.takeIf { it.declaration.type == match.type } ?: return@mapNotNull null
                DebloatableComponentUiItem(ComponentKey(match.id, app.key.userId), status.toRow(), match.displayName, match.kinds, match.className)
            }
            if (rows.isEmpty()) return@appRows null
            MatchedTarget(app.key, app.label, ApkIcon(app.key.packageName, app.apkPath, app.versionCode, app.uid), rows)
        }
    }

    /** Keeps each app's classification, including an unreadable manifest, for this APK revision. */
    private inner class ManifestMatches {
        private val matches = mutableMapOf<AppKey, ClassifiedApk>()

        suspend fun classify(user: UserId, cached: List<InstalledApp>, showSystemApps: Boolean): Classifications {
            val byName = cached.filter { it.key != self }.associateBy { it.key.packageName }
            matches.keys.retainAll(byName.values.mapTo(mutableSetOf()) { it.key })
            val visible = byName.filterValues { showSystemApps || !it.isSystem }
            if (visible.isEmpty()) return Classifications(emptyList())

            var failure: Exception? = null
            val order = try {
                metadata.packageOrder(user).distinct()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.w(e, "Cannot read the package order for user ${user.value}")
                failure = e
                visible.keys
            }
            val apps = order.mapNotNull { name ->
                val app = visible[name] ?: return@mapNotNull null
                currentCoroutineContext().ensureActive()
                val classification = try {
                    classifyApk(app.key)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Timber.w(e, "Cannot read the debloater package info of ${app.key}")
                    if (failure == null) failure = e
                    matches[app.key]
                }
                classification?.let { ClassifiedApp(app, it.components) }
            }
            return Classifications(apps, failure)
        }

        private suspend fun classifyApk(app: AppKey): ClassifiedApk? {
            val apk = metadata.apk(app)
            if (apk == null) {
                matches.remove(app)
                return null
            }
            val known = matches[app]
            if (known?.apk == apk) return known
            val components = metadata.read(apk)?.let { manifest ->
                classifyDebloatableComponents(app.packageName, manifest, apk.requestedPermissions)
            }.orEmpty()
            return ClassifiedApk(apk, components).also { matches[app] = it }
        }
    }

    private data class Classifications(val apps: List<ClassifiedApp>, val failure: Exception? = null)
    private data class ClassifiedApp(val app: InstalledApp, val matches: List<ClassifiedComponent>)
    private data class ClassifiedApk(val apk: DebloaterApk, val components: List<ClassifiedComponent>)

    private companion object {
        const val CACHE_READ_PERIOD_MILLIS = 300L
    }
}
