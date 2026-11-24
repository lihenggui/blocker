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
import com.merxury.blocker.core.ui.coil.ApkIcon
import com.merxury.blocker.core.ui.component.toRow
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import javax.inject.Inject
import kotlin.time.Instant

/** Joins the cached app and component state with the manifest classifications used by the debloater. */
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

    @OptIn(ExperimentalCoroutinesApi::class)
    fun forUser(user: UserId): Flow<List<MatchedTarget>> = flow {
        val memo = ManifestMatches()
        val apps = combine(
            installedApps.apps(user).readAtMostEvery(CACHE_READ_PERIOD_MILLIS),
            userData.userData.map { it.showSystemApps }.distinctUntilChanged(),
        ) { cached, showSystemApps ->
            cached.filter { it.key != self && (showSystemApps || !it.isSystem) }
        }.distinctUntilChanged().mapLatest { memo.classify(user, it) }
        val statuses = componentStatuses.allComponents(user).readAtMostEvery(CACHE_READ_PERIOD_MILLIS)
        emitAll(combine(apps, statuses, ::attachStates).distinctUntilChanged())
    }.flowOn(cpuDispatcher)

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

    /** Keeps the classification for a package until its APK changes. */
    private inner class ManifestMatches {
        private var packageNames = emptySet<String>()
        private var packageOrder = emptyList<String>()
        private val matches = mutableMapOf<AppKey, ClassifiedApk>()

        suspend fun classify(user: UserId, apps: List<InstalledApp>): List<ClassifiedApp> {
            val byName = apps.associateBy { it.key.packageName }
            if (packageNames != byName.keys) {
                packageOrder = metadata.packageOrder(user).distinct()
                packageNames = byName.keys
            }
            matches.keys.retainAll(apps.mapTo(mutableSetOf()) { it.key })
            return packageOrder.mapNotNull { name ->
                val app = byName[name] ?: return@mapNotNull null
                currentCoroutineContext().ensureActive()
                ClassifiedApp(app, componentsOf(app))
            }
        }

        private suspend fun componentsOf(app: InstalledApp): List<ClassifiedComponent> {
            val apk = ApkVersion(app.apkPath, app.versionCode, app.lastUpdateTime)
            val known = matches[app.key]
            if (known?.version == apk) return known.components
            val source = metadata.read(app) ?: return emptyList()
            val components = classifyDebloatableComponents(app.key.packageName, source.manifest, source.requestedPermissions)
            matches[app.key] = ClassifiedApk(apk, components)
            return components
        }
    }

    private data class ClassifiedApp(val app: InstalledApp, val matches: List<ClassifiedComponent>)
    private data class ClassifiedApk(val version: ApkVersion, val components: List<ClassifiedComponent>)
    private data class ApkVersion(val path: String?, val versionCode: Long, val lastUpdateTime: Instant)

    private companion object {
        const val CACHE_READ_PERIOD_MILLIS = 300L
    }
}
