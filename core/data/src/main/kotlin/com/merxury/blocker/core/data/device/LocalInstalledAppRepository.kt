/*
 * Copyright 2026 Blocker
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

package com.merxury.blocker.core.data.device

import com.merxury.blocker.core.appcontrol.AppControl
import com.merxury.blocker.core.database.cache.AppEntity
import com.merxury.blocker.core.database.cache.ComponentCacheDao
import com.merxury.blocker.core.database.cache.asEntity
import com.merxury.blocker.core.database.cache.asExternalModel
import com.merxury.blocker.core.model.component.AppKey
import com.merxury.blocker.core.model.component.UserId
import com.merxury.blocker.core.model.privileged.CallResult
import com.merxury.blocker.core.model.system.AppEnabledSetting
import com.merxury.blocker.core.model.system.InstalledApp
import com.merxury.blocker.core.model.system.ReadFailure
import com.merxury.blocker.core.model.system.ReadResult
import com.merxury.blocker.core.system.SystemPackageReader
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject

internal class LocalInstalledAppRepository @Inject constructor(
    private val dao: ComponentCacheDao,
    private val reader: SystemPackageReader,
    private val users: DeviceUserRepository,
    private val appControl: AppControl,
    private val pmBlocks: PmBlockRecords,
) : InstalledAppRepository {

    override fun apps(user: UserId): Flow<List<InstalledApp>> = dao.observeApps(user.value).map(List<AppEntity>::asApps)

    override fun app(key: AppKey): Flow<InstalledApp?> = dao.observeApp(key.packageName, key.userId.value).map { it?.asExternalModel() }

    override fun search(user: UserId, keyword: String): Flow<List<InstalledApp>> = dao.searchApps(user.value, keyword).map(List<AppEntity>::asApps)

    override suspend fun refresh(user: UserId): ReadResult<Unit> {
        ensureUserCached(user)?.let { return ReadResult.Failure(it) }
        return when (val read = reader.apps(user)) {
            is ReadResult.Success -> {
                val removed = dao.replaceApps(user.value, read.value.map { it.asEntity() })
                // The system drops a user's settings of an app with the app, so Blocker's blocks go too.
                removed.forEach { pmBlocks.forgetApp(AppKey(it, user)) }
                ReadResult.Success(Unit)
            }

            is ReadResult.Failure -> {
                Timber.w("Cannot read the apps of user ${user.value}, keep the cached ones: ${read.reason}")
                read
            }
        }
    }

    override suspend fun refreshApp(key: AppKey): ReadResult<InstalledApp?> {
        ensureUserCached(key.userId)?.let { return ReadResult.Failure(it) }
        return when (val read = reader.app(key)) {
            is ReadResult.Success -> {
                dao.upsertApp(read.value.asEntity())
                read
            }

            is ReadResult.Failure -> if (read.reason is ReadFailure.PackageNotFound) {
                Timber.i("$key is not installed, remove it from the cache")
                dao.deleteApp(key.packageName, key.userId.value)
                pmBlocks.forgetApp(key)
                ReadResult.Success(null)
            } else {
                Timber.w("Cannot read $key, keep the cached app: ${read.reason}")
                read
            }
        }
    }

    override suspend fun setEnabled(key: AppKey, enabled: Boolean): CallResult<AppEnabledSetting> {
        val result = appControl.setEnabled(key, enabled)
        if (result is CallResult.Failure) Timber.w("Cannot set $key enabled=$enabled: ${result.reason}")
        rereadAfterChange(key)
        return result
    }

    override suspend fun uninstall(key: AppKey): CallResult<Unit> {
        val result = appControl.uninstall(key)
        if (result is CallResult.Failure) Timber.w("Cannot uninstall $key: ${result.reason}")
        rereadAfterChange(key)
        return result
    }

    /** Mirrors what a change did, also when the caller is cancelled once the call has returned. */
    private suspend fun rereadAfterChange(key: AppKey) = withContext(NonCancellable) {
        val read = refreshApp(key)
        if (read is ReadResult.Failure) Timber.w("Cannot read $key after changing it; the cache may be behind: ${read.reason}")
    }

    /** Null when [user] is cached, after refreshing the users if needed; otherwise why it is not. */
    private suspend fun ensureUserCached(user: UserId): ReadFailure? {
        if (users.users.first().any { it.id == user }) return null
        return when (val read = users.refresh()) {
            is ReadResult.Failure -> read.reason

            is ReadResult.Success -> if (read.value.any { it.id == user }) {
                null
            } else {
                Timber.w("User ${user.value} is not among the users the system shows")
                ReadFailure.UserNotReachable(user)
            }
        }
    }
}

private fun List<AppEntity>.asApps(): List<InstalledApp> = map { it.asExternalModel() }
