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

package com.merxury.blocker.core.domain.applist

import com.merxury.blocker.core.appcontrol.AppControl
import com.merxury.blocker.core.data.device.InstalledAppRepository
import com.merxury.blocker.core.model.component.AppKey
import com.merxury.blocker.core.model.privileged.CallFailure
import com.merxury.blocker.core.model.privileged.CallResult
import com.merxury.blocker.core.model.privileged.ChannelState
import com.merxury.blocker.core.model.privileged.PrivilegedIdentity
import com.merxury.blocker.core.model.system.AppAction
import com.merxury.blocker.core.model.system.AppActionFailure
import com.merxury.blocker.core.privileged.PrivilegedChannel
import timber.log.Timber
import javax.inject.Inject

/**
 * Runs an action on a whole app for its user, as a list of apps offers it. Enabling, disabling and
 * uninstalling go through the installed apps, which read the app again afterwards; the others go
 * to AppControl. Each may connect the privileged channel, which may show the su or Shizuku prompt.
 */
class PerformAppActionUseCase @Inject constructor(
    private val installedApps: InstalledAppRepository,
    private val appControl: AppControl,
    private val channel: PrivilegedChannel,
) {
    /** Runs [action] on [app]; null when it succeeded, otherwise why it did not, as the user is told. */
    suspend operator fun invoke(app: AppKey, action: AppAction): AppActionFailure? {
        val result: CallResult<*> = when (action) {
            AppAction.ENABLE -> installedApps.setEnabled(app, enabled = true)
            AppAction.DISABLE -> installedApps.setEnabled(app, enabled = false)
            AppAction.FORCE_STOP -> appControl.forceStop(app)
            AppAction.CLEAR_CACHE -> appControl.clearCache(app)
            AppAction.CLEAR_DATA -> appControl.clearData(app)
            AppAction.UNINSTALL -> installedApps.uninstall(app)
        }
        if (result !is CallResult.Failure) return null
        Timber.w("$action of $app failed: ${result.reason}")
        val asShell = (channel.state.value as? ChannelState.Ready)?.identity == PrivilegedIdentity.SHELL
        return result.reason.toAppActionFailure(asShell)
    }
}

/**
 * How a failed action is explained. A refusal while Shizuku runs as the shell is what root would
 * be allowed to do, so it says so.
 */
internal fun CallFailure.toAppActionFailure(runsAsShell: Boolean): AppActionFailure = when (this) {
    CallFailure.ChannelUnavailable -> AppActionFailure.NoAccess
    is CallFailure.NotPermitted -> if (runsAsShell) AppActionFailure.NeedsRoot else AppActionFailure.NotPermitted
    is CallFailure.TargetNotFound -> AppActionFailure.NotFound
    is CallFailure.SystemError -> AppActionFailure.SystemError(message)
}
