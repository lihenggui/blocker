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

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.merxury.blocker.core.model.component.BlockReason
import com.merxury.blocker.core.model.component.ControlLayer
import com.merxury.blocker.core.model.control.ControlFailure
import com.merxury.blocker.core.model.control.ControlGoal
import com.merxury.blocker.core.model.privileged.PrivilegedIdentity
import com.merxury.blocker.core.ui.R.plurals
import com.merxury.blocker.core.ui.R.string

/** What a snackbar says about [this] outcome: the layers that failed and why, or a batch summary. */
@Composable
fun ControlMessage.text(): String = when (this) {
    is ControlMessage.ComponentNotChanged -> {
        val title = when {
            goal == ControlGoal.BLOCK && partial -> string.core_ui_block_component_partial
            goal == ControlGoal.BLOCK -> string.core_ui_block_component_failed
            partial -> string.core_ui_enable_component_partial
            else -> string.core_ui_enable_component_failed
        }
        lines(stringResource(title, name), *failures.map { it.text(identity) }.toTypedArray())
    }

    is ControlMessage.BatchDone -> {
        val changedText = pluralStringResource(plurals.core_ui_batch_changed, changed, changed)
        // Unblocked but still off: the app ships them disabled, which only a switch overrides.
        val staysOff = staysOffAsShipped.takeIf { it > 0 }?.let { pluralStringResource(plurals.core_ui_batch_stays_off_as_shipped, it, it) }
        val summary = when {
            notChanged != 0 -> {
                val notChangedText = pluralStringResource(plurals.core_ui_batch_not_changed, notChanged, notChanged)
                stringResource(string.core_ui_batch_summary, changedText, notChangedText)
            }

            changed == 0 -> if (staysOff == null) stringResource(string.core_ui_batch_nothing_needed) else null

            else -> changedText
        }
        val reason = mostCommon?.let { stringResource(string.core_ui_batch_most_common, it.text(identity)) }
        lines(summary, staysOff, reason)
    }
}

@Composable
private fun LayerFailure.text(identity: PrivilegedIdentity?): String = stringResource(string.core_ui_layer_reason, stringResource(layer.label()), failure.text(identity))

/** The name of a control layer, as the reason tags of a component name it. */
fun ControlLayer.label(): Int = when (this) {
    ControlLayer.PACKAGE_MANAGER -> string.core_ui_blocked_by_package_manager
    ControlLayer.INTENT_FIREWALL -> string.core_ui_blocked_by_intent_firewall
}

/** The name of the mechanism that blocks a component. */
fun BlockReason.label(): Int = when (this) {
    BlockReason.PM_SETTING -> string.core_ui_blocked_by_package_manager
    BlockReason.IFW_RULE -> string.core_ui_blocked_by_intent_firewall
    BlockReason.MANIFEST_DEFAULT -> string.core_ui_disabled_by_app
}

/**
 * Why a layer failed. A refusal needs root only when the channel runs as the shell; as root or
 * the system user it is the system protecting the app, so its message is told.
 */
@Composable
private fun ControlFailure.text(identity: PrivilegedIdentity?): String = when (this) {
    ControlFailure.ChannelUnavailable -> stringResource(string.core_ui_reason_no_access)

    is ControlFailure.NotPermitted -> if (identity == PrivilegedIdentity.SHELL) {
        stringResource(string.core_ui_reason_component_needs_root)
    } else {
        message?.takeIf { it.isNotBlank() }?.let { stringResource(string.core_ui_reason_not_permitted_detail, it) }
            ?: stringResource(string.core_ui_reason_not_permitted)
    }

    is ControlFailure.TargetNotFound -> stringResource(string.core_ui_reason_component_not_found)

    ControlFailure.RulesUnreadable -> stringResource(string.core_ui_reason_rules_unreadable)

    is ControlFailure.WriteFailed -> message?.let { stringResource(string.core_ui_reason_write_failed, it) }
        ?: stringResource(string.core_ui_reason_write_failed_unknown)

    is ControlFailure.SystemError -> message?.let { stringResource(string.core_ui_reason_system_error, it) }
        ?: stringResource(string.core_ui_reason_system_error_unknown)

    ControlFailure.StateNotConfirmed -> stringResource(string.core_ui_reason_not_confirmed)
}

private fun lines(vararg lines: String?): String = lines.filterNotNull().joinToString("\n")
