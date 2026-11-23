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

import androidx.work.WorkInfo

/**
 * What a screen tells the user about a rule work.
 *
 * @property result a [RuleWorkResult] code
 * @property failedCount items the work could not do, reported by a work that finished
 */
data class RuleWorkEvent(
    val type: RuleWorkType,
    val result: Int,
    val failedCount: Int = 0,
)

/**
 * The event for [workInfo] of a work of [type]: [RuleWorkResult.STARTED] once enqueued, the
 * failure code of a failed work, or -1 when it has none, [RuleWorkResult.FINISHED] with the
 * failed count of a work that succeeded, and [RuleWorkResult.CANCELLED]. Null while the work runs
 * or is blocked.
 */
fun ruleWorkEventOf(type: RuleWorkType, workInfo: WorkInfo): RuleWorkEvent? {
    val output = workInfo.outputData
    return when (workInfo.state) {
        WorkInfo.State.ENQUEUED -> RuleWorkEvent(type, RuleWorkResult.STARTED)
        WorkInfo.State.FAILED -> RuleWorkEvent(type, output.getInt(RuleWorkResult.PARAM_WORK_RESULT, -1))
        WorkInfo.State.SUCCEEDED -> RuleWorkEvent(type, RuleWorkResult.FINISHED, output.getInt(RuleWorkResult.PARAM_FAILED_COUNT, 0))
        WorkInfo.State.CANCELLED -> RuleWorkEvent(type, RuleWorkResult.CANCELLED)
        WorkInfo.State.RUNNING, WorkInfo.State.BLOCKED -> null
    }
}
