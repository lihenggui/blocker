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
import com.merxury.blocker.core.model.control.ControlGoal
import com.merxury.blocker.core.ui.R.plurals
import com.merxury.blocker.core.ui.R.string

/** What a snackbar says about [this] outcome; it offers to grant access when that would help. */
@Composable
fun ControlMessage.text(): String {
    val title = when (this) {
        is ControlMessage.ComponentNotChanged -> stringResource(
            if (goal == ControlGoal.BLOCK) string.core_ui_block_component_failed else string.core_ui_enable_component_failed,
            name,
        )

        is ControlMessage.ComponentPartlyChanged -> stringResource(string.core_ui_component_partly_changed, name)

        ControlMessage.FirewallSelfBlockRefused -> stringResource(string.core_ui_firewall_self_block_refused)

        ControlMessage.NoComponents -> stringResource(string.core_ui_no_components)

        is ControlMessage.BatchDone -> batchText()
    }
    val needsRoot = when (this) {
        is ControlMessage.ComponentNotChanged -> needsRoot
        is ControlMessage.ComponentPartlyChanged -> needsRoot
        is ControlMessage.BatchDone -> needsRoot
        else -> false
    }
    return withAccessHint(withShizukuShellHint(title, needsRoot), needsAccess)
}

@Composable
private fun ControlMessage.BatchDone.batchText(): String {
    val summary = when {
        partlyChanged > 0 && notChanged > 0 -> stringResource(string.core_ui_batch_partly_changed_with_failures, partlyChanged, notChanged)
        partlyChanged > 0 -> stringResource(string.core_ui_batch_partly_changed, partlyChanged)
        notChanged > 0 -> pluralStringResource(plurals.core_ui_operation_completed_with_failures, notChanged, notChanged)
        firewallSelfBlockRefused -> ""
        else -> stringResource(string.core_ui_operation_completed)
    }
    if (!firewallSelfBlockRefused) return summary
    val refusal = stringResource(string.core_ui_firewall_self_block_refused)
    return if (summary.isEmpty()) refusal else "$summary\n$refusal"
}

/** [title] and, when missing access caused it, the line that says how to get it. */
@Composable
fun withAccessHint(title: String, needsAccess: Boolean): String = if (needsAccess) {
    "$title\n${stringResource(string.core_ui_reason_no_access)}"
} else {
    title
}

@Composable
internal fun withShizukuShellHint(title: String, needsRoot: Boolean): String = if (needsRoot) {
    "$title\n${stringResource(string.core_ui_reason_shizuku_shell)}"
} else {
    title
}
