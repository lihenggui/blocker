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

package com.merxury.blocker.core.ui.applist

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.merxury.blocker.core.designsystem.component.BlockerWarningAlertDialog
import com.merxury.blocker.core.model.system.AppAction
import com.merxury.blocker.core.model.system.AppActionFailure
import com.merxury.blocker.core.ui.R.string
import com.merxury.blocker.core.ui.control.withAccessHint
import com.merxury.blocker.core.ui.control.withShizukuShellHint
import com.merxury.blocker.core.ui.profile.ProfileTab
import com.merxury.blocker.core.ui.profile.text

/** An action on [appLabel] that failed, to tell once. */
data class AppActionFailed(
    val action: AppAction,
    val appLabel: String,
    val reason: AppActionFailure,
)

/** Whether [this] action asks before it runs. */
val AppAction.needsConfirmation: Boolean
    get() = this == AppAction.CLEAR_DATA || this == AppAction.UNINSTALL

/**
 * Asks before clearing data or uninstalling. With more than one user, uninstalling names the
 * profile it applies to.
 *
 * @param profile the profile whose app this is, when the list switches between several
 */
@Composable
fun AppActionConfirmDialog(
    action: AppAction,
    appLabel: String,
    profile: ProfileTab?,
    onConfirm: () -> Unit,
    onDismissRequest: () -> Unit,
) {
    val text = when {
        action == AppAction.CLEAR_DATA -> stringResource(string.core_ui_do_you_want_to_clear_data_of_this_app)
        profile != null -> stringResource(string.core_ui_uninstall_for_profile_confirm, profile.label.text())
        else -> stringResource(string.core_ui_do_you_want_to_uninstall_this_app)
    }
    BlockerWarningAlertDialog(
        title = appLabel,
        text = text,
        onDismissRequest = onDismissRequest,
        onConfirmRequest = onConfirm,
    )
}

/** Whether the failure came from missing root or Shizuku access, which the user can grant. */
val AppActionFailed.needsAccess: Boolean get() = reason == AppActionFailure.NoAccess

/**
 * What a snackbar says about [this] failure: which action failed, and how to get access when its
 * lack was the cause. A Shizuku shell refusal explains how to start Shizuku with root.
 */
@Composable
fun AppActionFailed.text(): String = withAccessHint(
    withShizukuShellHint(stringResource(action.failureTitle(), appLabel), reason == AppActionFailure.NeedsRoot),
    needsAccess,
)

private fun AppAction.failureTitle(): Int = when (this) {
    AppAction.ENABLE -> string.core_ui_enable_app_failed
    AppAction.DISABLE -> string.core_ui_disable_app_failed
    AppAction.FORCE_STOP -> string.core_ui_force_stop_app_failed
    AppAction.CLEAR_CACHE -> string.core_ui_clear_cache_failed
    AppAction.CLEAR_DATA -> string.core_ui_clear_data_failed
    AppAction.UNINSTALL -> string.core_ui_uninstall_app_failed
}
