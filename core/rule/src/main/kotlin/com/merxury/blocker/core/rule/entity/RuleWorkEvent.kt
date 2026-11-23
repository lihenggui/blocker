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

package com.merxury.blocker.core.rule.entity

import android.content.res.Resources
import androidx.work.WorkInfo
import com.merxury.blocker.core.rule.R

/**
 * What a screen tells the user about a rule work.
 *
 * @property result a [RuleWorkResult] code
 * @property changed items a work that finished changed, such as backup files written
 * @property skipped items it left alone on purpose
 * @property failed items it could not do
 * @property firewallUnavailable whether it could not reach the Intent Firewall rules at all
 */
data class RuleWorkEvent(
    val type: RuleWorkType,
    val result: Int,
    val changed: Int = 0,
    val skipped: Int = 0,
    val failed: Int = 0,
    val firewallUnavailable: Boolean = false,
)

/**
 * The event for [workInfo] of a work of [type]: [RuleWorkResult.STARTED] once enqueued, the
 * failure code of a failed work, or -1 when it has none, [RuleWorkResult.FINISHED] with the counts
 * of a work that succeeded, and [RuleWorkResult.CANCELLED]. Null while the work runs or is blocked.
 */
fun ruleWorkEventOf(type: RuleWorkType, workInfo: WorkInfo): RuleWorkEvent? {
    val output = workInfo.outputData
    return when (workInfo.state) {
        WorkInfo.State.ENQUEUED -> RuleWorkEvent(type, RuleWorkResult.STARTED)

        WorkInfo.State.FAILED -> RuleWorkEvent(type, output.getInt(RuleWorkResult.PARAM_WORK_RESULT, -1))

        WorkInfo.State.SUCCEEDED -> RuleWorkEvent(
            type = type,
            result = RuleWorkResult.FINISHED,
            changed = output.getInt(RuleWorkResult.PARAM_CHANGED_COUNT, 0),
            skipped = output.getInt(RuleWorkResult.PARAM_SKIPPED_COUNT, 0),
            failed = output.getInt(RuleWorkResult.PARAM_FAILED_COUNT, 0),
            firewallUnavailable = output.getBoolean(RuleWorkResult.PARAM_FIREWALL_UNAVAILABLE, false),
        )

        WorkInfo.State.CANCELLED -> RuleWorkEvent(type, RuleWorkResult.CANCELLED)

        WorkInfo.State.RUNNING, WorkInfo.State.BLOCKED -> null
    }
}

/**
 * What the screens show for this event once the work has ended, or null while it is
 * [RuleWorkResult.STARTED], whose message each screen chooses. A work that finished says how many
 * items it changed and, when any, how many it skipped and how many failed, and whether it had to
 * leave the Intent Firewall rules out. A folder that is set but cannot be reached is told apart
 * from one that is not set; the MAT import, which reads only the file the user picked, says that
 * file cannot be read, and no other work that does not use the backup folder mentions it.
 */
fun RuleWorkEvent.resultMessage(resources: Resources): String? = when (result) {
    RuleWorkResult.STARTED -> null

    RuleWorkResult.FINISHED -> buildString {
        append(resources.getQuantityString(R.plurals.core_rule_result_changed, changed, changed))
        if (skipped > 0) append(resources.getQuantityString(R.plurals.core_rule_result_skipped, skipped, skipped))
        if (failed > 0) append(resources.getQuantityString(R.plurals.core_rule_result_failed, failed, failed))
        if (firewallUnavailable) append(resources.getString(R.string.core_rule_result_firewall_unavailable))
    }

    RuleWorkResult.FOLDER_NOT_DEFINED, RuleWorkResult.MISSING_STORAGE_PERMISSION -> resources.getString(storageMessage())

    RuleWorkResult.MISSING_ROOT_PERMISSION -> resources.getString(R.string.core_rule_error_msg_missing_root_permission)

    RuleWorkResult.CANCELLED -> resources.getString(R.string.core_rule_task_cancelled)

    else -> resources.getString(R.string.core_rule_error_msg_unexpected_exception)
}

/** The message for a storage failure of this work, which only the backup works blame on the folder. */
private fun RuleWorkEvent.storageMessage(): Int = when (type) {
    RuleWorkType.IMPORT_MAT_RULES -> R.string.core_rule_error_msg_mat_file_unreadable

    RuleWorkType.EXPORT_BLOCKER_RULES,
    RuleWorkType.IMPORT_BLOCKER_RULES,
    RuleWorkType.EXPORT_IFW_RULES,
    RuleWorkType.IMPORT_IFW_RULES,
    -> if (result == RuleWorkResult.FOLDER_NOT_DEFINED) R.string.core_rule_error_msg_folder_not_defined else R.string.core_rule_error_msg_folder_unreachable

    RuleWorkType.RESET_IFW, RuleWorkType.RESET_PM -> R.string.core_rule_error_msg_unexpected_exception
}
