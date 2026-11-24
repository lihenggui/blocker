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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.merxury.blocker.core.model.component.ControlLayer
import com.merxury.blocker.core.model.control.ControlFailure
import com.merxury.blocker.core.model.control.ControlGoal
import com.merxury.blocker.core.model.privileged.PrivilegedIdentity
import com.merxury.blocker.core.model.system.ReadFailure
import com.merxury.blocker.core.ui.profile.ProfileLabel
import com.merxury.blocker.feature.appdetail.api.R.plurals
import com.merxury.blocker.feature.appdetail.api.R.string
import com.merxury.blocker.feature.appdetail.impl.ActionFailure
import com.merxury.blocker.feature.appdetail.impl.ControlMessage
import com.merxury.blocker.feature.appdetail.impl.LayerFailure
import com.merxury.blocker.feature.appdetail.impl.ListMessage

/** What a snackbar says about [this] message. */
@Composable
internal fun ListMessage.text(): String = when (this) {
    is ListMessage.RefreshFailed -> lines(stringResource(string.feature_appdetail_api_refresh_failed), reason.text())
    is ListMessage.LaunchFailed -> lines(stringResource(string.feature_appdetail_api_launch_failed, name), reason.text())
    is ListMessage.LaunchBlockedByRule -> stringResource(string.feature_appdetail_api_launch_blocked, name)
    is ListMessage.LaunchMayBeBlocked -> stringResource(string.feature_appdetail_api_launch_conditional, name)
    is ListMessage.StopFailed -> lines(stringResource(string.feature_appdetail_api_stop_failed, name), reason.text())
    ListMessage.GrantFailed -> stringResource(string.feature_appdetail_api_grant_failed)
}

/** What a snackbar says about [this] outcome: the layers that failed and why, or a batch summary. */
@Composable
internal fun ControlMessage.text(): String = when (this) {
    is ControlMessage.ComponentNotChanged -> {
        val title = when {
            goal == ControlGoal.BLOCK && partial -> string.feature_appdetail_api_block_partial
            goal == ControlGoal.BLOCK -> string.feature_appdetail_api_block_failed
            partial -> string.feature_appdetail_api_enable_partial
            else -> string.feature_appdetail_api_enable_failed
        }
        lines(stringResource(title, name), *failures.map { it.text(identity) }.toTypedArray())
    }

    is ControlMessage.BatchDone -> {
        val changedText = pluralStringResource(plurals.feature_appdetail_api_batch_changed, changed, changed)
        val summary = when {
            notChanged != 0 -> {
                val notChangedText = pluralStringResource(plurals.feature_appdetail_api_batch_not_changed, notChanged, notChanged)
                stringResource(string.feature_appdetail_api_batch_summary, changedText, notChangedText)
            }

            changed == 0 -> stringResource(string.feature_appdetail_api_batch_nothing_needed)

            else -> changedText
        }
        val reason = mostCommon?.let { stringResource(string.feature_appdetail_api_batch_most_common, it.text(identity)) }
        lines(summary, reason)
    }
}

@Composable
private fun LayerFailure.text(identity: PrivilegedIdentity?): String = stringResource(string.feature_appdetail_api_layer_reason, stringResource(layer.label()), failure.text(identity))

/** The name of a control layer, as the reason tags of a row name it. */
internal fun ControlLayer.label(): Int = when (this) {
    ControlLayer.PACKAGE_MANAGER -> string.feature_appdetail_api_blocked_by_package_manager
    ControlLayer.INTENT_FIREWALL -> string.feature_appdetail_api_blocked_by_intent_firewall
}

/**
 * Why a layer failed. A refusal needs root only when the channel runs as the shell; as root or
 * the system user it is the system protecting the app, so its message is told.
 */
@Composable
private fun ControlFailure.text(identity: PrivilegedIdentity?): String = when (this) {
    ControlFailure.ChannelUnavailable -> stringResource(string.feature_appdetail_api_reason_no_access)

    is ControlFailure.NotPermitted -> if (identity == PrivilegedIdentity.SHELL) {
        stringResource(string.feature_appdetail_api_reason_needs_root)
    } else {
        message?.takeIf { it.isNotBlank() }?.let { stringResource(string.feature_appdetail_api_reason_not_allowed_detail, it) }
            ?: stringResource(string.feature_appdetail_api_reason_not_allowed)
    }

    is ControlFailure.TargetNotFound -> stringResource(string.feature_appdetail_api_reason_not_found)

    ControlFailure.RulesUnreadable -> stringResource(string.feature_appdetail_api_reason_rules_unreadable)

    is ControlFailure.WriteFailed -> message?.let { stringResource(string.feature_appdetail_api_reason_write_failed, it) }
        ?: stringResource(string.feature_appdetail_api_reason_write_failed_unknown)

    is ControlFailure.SystemError -> systemError(message)

    ControlFailure.StateNotConfirmed -> stringResource(string.feature_appdetail_api_reason_not_confirmed)
}

@Composable
private fun ActionFailure.text(): String = when (this) {
    ActionFailure.NoAccess -> stringResource(string.feature_appdetail_api_reason_no_access)
    ActionFailure.NotPermitted -> stringResource(string.feature_appdetail_api_reason_not_allowed)
    ActionFailure.NotFound -> stringResource(string.feature_appdetail_api_reason_not_found)
    is ActionFailure.SystemError -> systemError(message)
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

@Composable
private fun systemError(message: String?): String = message?.let { stringResource(string.feature_appdetail_api_reason_system_error, it) }
    ?: stringResource(string.feature_appdetail_api_reason_system_error_unknown)

private fun lines(vararg lines: String?): String = lines.filterNotNull().joinToString("\n")
