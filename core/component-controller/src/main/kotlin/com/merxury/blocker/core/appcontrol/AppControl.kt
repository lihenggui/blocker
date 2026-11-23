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

import com.merxury.blocker.core.model.component.AppKey
import com.merxury.blocker.core.model.component.UserId
import com.merxury.blocker.core.model.privileged.CallFailure
import com.merxury.blocker.core.model.privileged.CallResult
import com.merxury.blocker.core.model.system.AppEnabledSetting

/**
 * Acts on whole apps through the privileged channel, each for the user in its [AppKey].
 *
 * Every operation is something the user asked for, so it connects the channel when needed, which
 * may show the su or Shizuku prompt. None throws except for cancellation. Common failures:
 * [CallFailure.ChannelUnavailable] when no privileged channel can be had,
 * [CallFailure.NotPermitted] when the system refuses the identity the channel runs as, and
 * [CallFailure.TargetNotFound] when the app is not installed for that user or the user does not
 * exist. An operation that the system finishes later waits for its report, at most 30 seconds,
 * and fails with [CallFailure.SystemError] when that report is a failure or does not come.
 */
interface AppControl {
    /**
     * Enables or disables the app for its user. Disabling stores `DISABLED_USER`, as system
     * settings and `pm disable-user` do; enabling stores `ENABLED`. Root and the shell may both.
     *
     * @return the setting read back after the change; [CallFailure.SystemError] when it does not
     * show the change
     */
    suspend fun setEnabled(app: AppKey, enabled: Boolean): CallResult<AppEnabledSetting>

    /**
     * Stops every process of the app in its user, as "Force stop" does. Root and the shell may.
     * An app that is not installed for that user succeeds: nothing of it runs there.
     */
    suspend fun forceStop(app: AppKey): CallResult<Unit>

    /**
     * Deletes the app's cache and code cache for its user only. Root and the system uid may; the
     * shell may up to Android 8.1. From Android 9 the system ignores the shell's request without
     * answering, so for the shell this is [CallFailure.NotPermitted] without asking the system.
     */
    suspend fun clearCache(app: AppKey): CallResult<Unit>

    /**
     * Clears the app's data for its user, as "Clear storage" does: the system also removes its
     * jobs, alarms, notifications and URI grants there. While clearing, the system stops the app
     * in every user, not only this one; only the data of this user is removed. Root and the shell
     * may. A
     * package whose data the system protects, such as an active device admin, is
     * [CallFailure.NotPermitted].
     */
    suspend fun clearData(app: AppKey): CallResult<Unit>

    /**
     * Uninstalls the app for its user only. An app that came with the system stays on the system
     * image and can be restored for that user; any other app's APK is removed from the device
     * once no user has the app. Root and the shell may. An uninstall the system blocks, for
     * example for a device admin, or wants the user to confirm, is [CallFailure.NotPermitted].
     */
    suspend fun uninstall(app: AppKey): CallResult<Unit>

    /**
     * Package names with a running process in [user]. The shell and the system uid may list any
     * user; root only user 0, and [CallFailure.NotPermitted] for any other, because the system
     * answers uid 0 with user 0 alone.
     */
    suspend fun runningPackages(user: UserId): CallResult<Set<String>>
}
