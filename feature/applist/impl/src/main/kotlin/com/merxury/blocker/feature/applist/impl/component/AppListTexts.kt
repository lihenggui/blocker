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

package com.merxury.blocker.feature.applist.impl.component

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.merxury.blocker.core.designsystem.component.BlockerWarningAlertDialog
import com.merxury.blocker.feature.applist.api.R.string
import com.merxury.blocker.feature.applist.impl.AppAction
import com.merxury.blocker.feature.applist.impl.AppListMessage
import com.merxury.blocker.feature.applist.impl.FailureReason
import com.merxury.blocker.feature.applist.impl.ProfileTab
import com.merxury.blocker.core.ui.R.string as uiString

/** Whether [this] action asks before it runs. */
internal val AppAction.needsConfirmation: Boolean
    get() = this == AppAction.CLEAR_DATA || this == AppAction.UNINSTALL

/**
 * Asks before clearing data or uninstalling. Clearing data stops the app in every user, which the
 * question says. With more than one user, uninstalling names the profile it applies to.
 *
 * @param profile the profile whose app this is, when the list switches between several
 */
@Composable
internal fun AppActionConfirmDialog(
    action: AppAction,
    appLabel: String,
    profile: ProfileTab?,
    onConfirm: () -> Unit,
    onDismissRequest: () -> Unit,
) {
    val text = when {
        action == AppAction.CLEAR_DATA -> stringResource(string.feature_applist_api_clear_data_confirm)
        profile != null -> stringResource(string.feature_applist_api_uninstall_for_profile_confirm, profile.label.text())
        else -> stringResource(uiString.core_ui_do_you_want_to_uninstall_this_app)
    }
    BlockerWarningAlertDialog(
        title = appLabel,
        text = text,
        onDismissRequest = onDismissRequest,
        onConfirmRequest = onConfirm,
    )
}

/** What a snackbar says about [this] message. */
@Composable
internal fun AppListMessage.text(): String = when (this) {
    is AppListMessage.ActionFailed -> {
        val title = stringResource(action.failureTitle(), appLabel)
        "$title\n${reason.text()}"
    }
}

private fun AppAction.failureTitle(): Int = when (this) {
    AppAction.ENABLE -> string.feature_applist_api_enable_failed
    AppAction.DISABLE -> string.feature_applist_api_disable_failed
    AppAction.FORCE_STOP -> string.feature_applist_api_force_stop_failed
    AppAction.CLEAR_CACHE -> string.feature_applist_api_clear_cache_failed
    AppAction.CLEAR_DATA -> string.feature_applist_api_clear_data_failed
    AppAction.UNINSTALL -> string.feature_applist_api_uninstall_failed
}

@Composable
private fun FailureReason.text(): String = when (this) {
    FailureReason.NoAccess -> stringResource(string.feature_applist_api_reason_no_access)

    FailureReason.NeedsRoot -> stringResource(string.feature_applist_api_reason_needs_root)

    FailureReason.NotPermitted -> stringResource(string.feature_applist_api_reason_not_permitted)

    FailureReason.NotFound -> stringResource(string.feature_applist_api_reason_not_found)

    is FailureReason.SystemError -> message?.let { stringResource(string.feature_applist_api_reason_system_error, it) }
        ?: stringResource(string.feature_applist_api_reason_system_error_unknown)
}
