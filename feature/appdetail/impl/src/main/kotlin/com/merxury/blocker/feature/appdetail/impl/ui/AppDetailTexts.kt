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

package com.merxury.blocker.feature.appdetail.impl.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.merxury.blocker.core.model.system.ReadFailure
import com.merxury.blocker.core.ui.profile.ProfileLabel
import com.merxury.blocker.feature.appdetail.api.R.string
import com.merxury.blocker.feature.appdetail.impl.ActionFailure
import com.merxury.blocker.feature.appdetail.impl.ListMessage
import com.merxury.blocker.core.ui.R.string as uiString

/** What a snackbar says about [this] message. */
@Composable
internal fun ListMessage.text(): String = when (this) {
    is ListMessage.RefreshFailed -> lines(stringResource(string.feature_appdetail_api_refresh_failed), reason.text())
    is ListMessage.LaunchFailed -> lines(stringResource(string.feature_appdetail_api_launch_failed, name), reason.text())
    is ListMessage.LaunchBlockedByRule -> stringResource(string.feature_appdetail_api_launch_blocked, name)
    is ListMessage.LaunchMayBeBlocked -> stringResource(string.feature_appdetail_api_launch_conditional, name)
    is ListMessage.StopFailed -> lines(stringResource(string.feature_appdetail_api_stop_failed, name), reason.text())
    ListMessage.GrantFailed -> stringResource(uiString.core_ui_grant_failed)
    ListMessage.AccessCheckFailed -> stringResource(string.feature_appdetail_api_access_not_checked)
}

@Composable
private fun ActionFailure.text(): String = when (this) {
    ActionFailure.NoAccess -> stringResource(uiString.core_ui_reason_no_access)

    ActionFailure.NotPermitted -> stringResource(uiString.core_ui_reason_not_permitted)

    ActionFailure.NotFound -> stringResource(uiString.core_ui_reason_component_not_found)

    is ActionFailure.SystemError -> message?.let { stringResource(uiString.core_ui_reason_system_error, it) }
        ?: stringResource(uiString.core_ui_reason_system_error_unknown)
}

/** Why the app could not be read, as the user is told. */
@Composable
internal fun ReadFailure.text(): String = when (this) {
    is ReadFailure.PackageNotFound -> stringResource(string.feature_appdetail_api_not_installed)
    is ReadFailure.UserNotReachable -> stringResource(string.feature_appdetail_api_read_user_not_reachable)
    is ReadFailure.ManifestUnreadable -> stringResource(string.feature_appdetail_api_read_manifest_unreadable)
    ReadFailure.ChannelUnavailable -> stringResource(string.feature_appdetail_api_read_no_answer)
    is ReadFailure.RulesUnreadable -> stringResource(string.feature_appdetail_api_read_rules_unreadable)
}

/** The profile an app belongs to, as the header names it. */
@Composable
internal fun ProfileLabel.headerText(): String = when (this) {
    ProfileLabel.Personal -> stringResource(string.feature_appdetail_api_profile_personal)
    ProfileLabel.Work -> stringResource(string.feature_appdetail_api_profile_work)
    is ProfileLabel.Named -> name
    is ProfileLabel.Numbered -> stringResource(string.feature_appdetail_api_profile_user, id)
}

private fun lines(vararg lines: String?): String = lines.filterNotNull().joinToString("\n")
