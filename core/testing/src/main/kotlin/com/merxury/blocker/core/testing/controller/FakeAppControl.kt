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

package com.merxury.blocker.core.testing.controller

import com.merxury.blocker.core.appcontrol.AppControl
import com.merxury.blocker.core.model.component.AppKey
import com.merxury.blocker.core.model.component.ComponentKey
import com.merxury.blocker.core.model.component.UserId
import com.merxury.blocker.core.model.privileged.CallFailure
import com.merxury.blocker.core.model.privileged.CallResult
import com.merxury.blocker.core.model.system.AppEnabledSetting

/**
 * [AppControl] that changes nothing on a device. Each operation succeeds unless [failures] holds a
 * failure for its name (`setEnabled`, `forceStop`, `clearCache`, `clearData`, `uninstall`,
 * `runningPackages` or `startActivity`). [forceStop] and [clearData] remove the app from
 * [running], as the system stops it; [runningPackages] answers from [running].
 */
class FakeAppControl : AppControl {
    /** Package names with a running process, per user. */
    val running = mutableMapOf<UserId, MutableSet<String>>()

    /** The failure each operation answers with, by operation name. */
    val failures = mutableMapOf<String, CallFailure>()

    /** Every call, as `<operation> <key or user>`. */
    val calls = mutableListOf<String>()

    override suspend fun setEnabled(app: AppKey, enabled: Boolean): CallResult<AppEnabledSetting> = call("setEnabled", app) {
        if (enabled) AppEnabledSetting.ENABLED else AppEnabledSetting.DISABLED_USER
    }

    override suspend fun forceStop(app: AppKey): CallResult<Unit> = call("forceStop", app) {
        running[app.userId]?.remove(app.packageName)
    }

    override suspend fun clearCache(app: AppKey): CallResult<Unit> = call("clearCache", app) {}

    override suspend fun clearData(app: AppKey): CallResult<Unit> = call("clearData", app) {
        running.values.forEach { it.remove(app.packageName) }
    }

    override suspend fun uninstall(app: AppKey): CallResult<Unit> = call("uninstall", app) {}

    override suspend fun runningPackages(user: UserId): CallResult<Set<String>> = call("runningPackages", user.value) {
        running[user].orEmpty().toSet()
    }

    override suspend fun startActivity(key: ComponentKey): CallResult<Unit> = call("startActivity", key) {}

    private inline fun <R> call(operation: String, target: Any, action: () -> R): CallResult<R> {
        calls += "$operation $target"
        failures[operation]?.let { return CallResult.Failure(it) }
        return CallResult.Success(action())
    }
}
