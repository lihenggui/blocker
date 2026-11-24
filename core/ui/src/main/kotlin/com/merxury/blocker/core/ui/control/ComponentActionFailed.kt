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

package com.merxury.blocker.core.ui.control

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.merxury.blocker.core.model.privileged.CallFailure
import com.merxury.blocker.core.ui.R.string

/**
 * Launching an activity or stopping a service, called [name], failed.
 *
 * @property needsAccess whether there was no root or Shizuku access, so the message offers to
 * grant it
 */
data class ComponentActionFailed(
    val action: ComponentAction,
    val name: String,
    val needsAccess: Boolean,
) {
    companion object {
        /** The failure of [action] on the component called [name] for the reason the system gave. */
        fun of(action: ComponentAction, name: String, reason: CallFailure) = ComponentActionFailed(action, name, needsAccess = reason == CallFailure.ChannelUnavailable)
    }
}

/** What can be done to one running component from its menu. */
enum class ComponentAction {
    LAUNCH_ACTIVITY,
    STOP_SERVICE,
}

/**
 * What a snackbar says about [this] failure, in the words the app detail uses, with how to get
 * access when its lack was the cause.
 */
@Composable
fun ComponentActionFailed.text(): String {
    val title = when (action) {
        ComponentAction.LAUNCH_ACTIVITY -> stringResource(string.core_ui_launch_failed, name)
        ComponentAction.STOP_SERVICE -> stringResource(string.core_ui_stop_failed, name)
    }
    return withAccessHint(title, needsAccess)
}

/**
 * Shows [failure] in a snackbar for its whole duration, with an action to grant access when
 * missing access caused it, then reports it as shown, so it is told once.
 *
 * @param onShow called once the failure is no longer shown
 */
@Composable
fun ComponentActionFailedSnackbar(
    failure: ComponentActionFailed?,
    snackbarHostState: SnackbarHostState,
    onShow: () -> Unit,
    onGrantAccess: () -> Unit,
) {
    AccessMessageSnackbar(
        key = failure,
        text = failure?.text(),
        needsAccess = failure?.needsAccess == true,
        snackbarHostState = snackbarHostState,
        onShow = onShow,
        onGrantAccess = onGrantAccess,
    )
}
