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

package com.merxury.blocker.core.testing.repository.device

import com.merxury.blocker.core.data.device.InstalledAppRepository
import com.merxury.blocker.core.model.component.AppKey
import com.merxury.blocker.core.model.component.UserId
import com.merxury.blocker.core.model.privileged.CallFailure
import com.merxury.blocker.core.model.privileged.CallResult
import com.merxury.blocker.core.model.system.AppEnabledSetting
import com.merxury.blocker.core.model.system.InstalledApp
import com.merxury.blocker.core.model.system.ReadFailure
import com.merxury.blocker.core.model.system.ReadResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import javax.inject.Inject

/**
 * In-memory [InstalledAppRepository]. Tests set the cached apps with [sendApps]; refreshes succeed
 * and change nothing unless a failure is set, and app actions change the cached app as the system
 * would when they succeed.
 */
class FakeInstalledAppRepository @Inject constructor() : InstalledAppRepository {
    private val cached = MutableStateFlow<Map<AppKey, InstalledApp>>(emptyMap())

    /** When set, [refresh] and [refreshApp] fail with it. */
    var refreshFailure: ReadFailure? = null

    /** When set, [setEnabled] and [uninstall] fail with it and change nothing. */
    var callFailure: CallFailure? = null

    /** Every refreshed user or app and every action, as `refresh <user>`, `refreshApp <key>`, `setEnabled <key> <enabled>` or `uninstall <key>`. */
    val calls = mutableListOf<String>()

    override fun apps(user: UserId): Flow<List<InstalledApp>> = cached.map { apps -> apps.values.filter { it.key.userId == user }.sortedBy { it.key.packageName } }

    override fun app(key: AppKey): Flow<InstalledApp?> = cached.map { it[key] }

    override fun search(user: UserId, keyword: String): Flow<List<InstalledApp>> = apps(user).map { apps ->
        apps.filter { it.label.contains(keyword, ignoreCase = true) || it.key.packageName.contains(keyword, ignoreCase = true) }
    }

    override suspend fun refresh(user: UserId): ReadResult<Unit> {
        calls += "refresh ${user.value}"
        return refreshFailure?.let { ReadResult.Failure(it) } ?: ReadResult.Success(Unit)
    }

    override suspend fun refreshApp(key: AppKey): ReadResult<InstalledApp?> {
        calls += "refreshApp $key"
        return refreshFailure?.let { ReadResult.Failure(it) } ?: ReadResult.Success(cached.value[key])
    }

    override suspend fun setEnabled(key: AppKey, enabled: Boolean): CallResult<AppEnabledSetting> {
        calls += "setEnabled $key $enabled"
        callFailure?.let { return CallResult.Failure(it) }
        val app = cached.value[key] ?: return CallResult.Failure(CallFailure.TargetNotFound("$key is not installed"))
        val setting = if (enabled) AppEnabledSetting.ENABLED else AppEnabledSetting.DISABLED_USER
        cached.update { it + (key to app.copy(enabled = enabled, enabledSetting = setting)) }
        return CallResult.Success(setting)
    }

    override suspend fun uninstall(key: AppKey): CallResult<Unit> {
        calls += "uninstall $key"
        callFailure?.let { return CallResult.Failure(it) }
        if (key !in cached.value) return CallResult.Failure(CallFailure.TargetNotFound("$key is not installed"))
        cached.update { it - key }
        return CallResult.Success(Unit)
    }

    /** Replaces the cached apps of every user they belong to. */
    fun sendApps(apps: List<InstalledApp>) {
        cached.value = apps.associateBy { it.key }
    }
}
