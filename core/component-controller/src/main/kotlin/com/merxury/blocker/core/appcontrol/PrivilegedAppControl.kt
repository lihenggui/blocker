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

package com.merxury.blocker.core.appcontrol

import android.content.ComponentName
import android.content.pm.PackageManager
import com.merxury.blocker.core.model.component.AppKey
import com.merxury.blocker.core.model.component.ComponentKey
import com.merxury.blocker.core.model.component.UserId
import com.merxury.blocker.core.model.privileged.CallFailure
import com.merxury.blocker.core.model.privileged.CallResult
import com.merxury.blocker.core.model.system.AppEnabledSetting
import com.merxury.blocker.core.privileged.ActivePrivilegedChannel
import com.merxury.blocker.core.privileged.PrivilegedChannel
import com.merxury.blocker.core.privileged.PrivilegedExecutor
import com.merxury.blocker.core.privileged.calls.ApplicationEnabledSettings
import com.merxury.blocker.core.privileged.calls.ClearAppCache
import com.merxury.blocker.core.privileged.calls.ClearAppData
import com.merxury.blocker.core.privileged.calls.ForceStop
import com.merxury.blocker.core.privileged.calls.RunningPackages
import com.merxury.blocker.core.privileged.calls.SetApplicationEnabledSetting
import com.merxury.blocker.core.privileged.calls.StartActivity
import com.merxury.blocker.core.privileged.calls.UninstallApp
import com.merxury.blocker.core.privileged.calls.startOutcome
import com.merxury.blocker.core.system.appEnabledSettingOf
import timber.log.Timber
import javax.inject.Inject

/** [AppControl] on the privileged channel; each operation is one or two privileged calls. */
internal class PrivilegedAppControl(
    private val channel: PrivilegedChannel,
    private val executor: PrivilegedExecutor,
) : AppControl {

    @Inject constructor(channel: ActivePrivilegedChannel) : this(channel as PrivilegedChannel, channel as PrivilegedExecutor)

    override suspend fun setEnabled(app: AppKey, enabled: Boolean): CallResult<AppEnabledSetting> {
        val state = if (enabled) PackageManager.COMPONENT_ENABLED_STATE_ENABLED else PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER
        val set = executor.execute(SetApplicationEnabledSetting(app.packageName, state, app.userId))
        if (set is CallResult.Failure) return set
        return executor.execute(ApplicationEnabledSettings(listOf(app.packageName), app.userId)).map { states ->
            val readBack = appEnabledSettingOf(states.single())
            val expected = appEnabledSettingOf(state)
            if (readBack != expected) {
                Timber.e("Set $app to $expected, but it reads back as $readBack")
                return CallResult.Failure(CallFailure.SystemError("$app reads back as $readBack after setting $expected"))
            }
            readBack
        }
    }

    override suspend fun forceStop(app: AppKey): CallResult<Unit> = executor.execute(ForceStop(app.packageName, app.userId))

    override suspend fun clearCache(app: AppKey): CallResult<Unit> {
        channel.cacheClearingRefusal()?.let { return CallResult.Failure(it) }
        return executor.execute(ClearAppCache(app.packageName, app.userId))
    }

    override suspend fun clearData(app: AppKey): CallResult<Unit> = executor.execute(ClearAppData(app.packageName, app.userId))

    override suspend fun uninstall(app: AppKey): CallResult<Unit> = executor.execute(UninstallApp(app.packageName, app.userId))

    override suspend fun runningPackages(user: UserId): CallResult<Set<String>> {
        channel.runningListRefusal(user)?.let { return CallResult.Failure(it) }
        return executor.execute(RunningPackages(user)).map { it.toSet() }
    }

    override suspend fun startActivity(key: ComponentKey): CallResult<Unit> {
        val component = ComponentName(key.id.packageName, key.id.className)
        return when (val result = executor.execute(StartActivity(component, key.userId))) {
            is CallResult.Success -> startOutcome(result.value).also { outcome ->
                if (outcome is CallResult.Failure) Timber.w("Cannot start $key: ${outcome.reason}")
            }

            is CallResult.Failure -> result
        }
    }
}
