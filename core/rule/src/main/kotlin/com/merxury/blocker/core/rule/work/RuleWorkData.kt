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

package com.merxury.blocker.core.rule.work

import android.content.Context
import android.os.Process
import androidx.work.Data
import androidx.work.ListenableWorker
import androidx.work.workDataOf
import com.merxury.blocker.core.model.component.UserId
import com.merxury.blocker.core.rule.backup.BackupSet
import com.merxury.blocker.core.rule.backup.BackupStorage
import com.merxury.blocker.core.rule.backup.FailureReason
import com.merxury.blocker.core.rule.backup.RuleRunResult
import com.merxury.blocker.core.rule.backup.RuleRunSummary
import com.merxury.blocker.core.rule.entity.RuleWorkResult
import com.merxury.blocker.core.rule.util.DocumentTreeBackupStorage
import kotlinx.coroutines.CoroutineDispatcher

/** Input: the id of the user whose state a work reads or changes; absent means the user Blocker runs in. */
internal const val PARAM_TARGET_USER = "param_target_user"

/** Input: the user id of the backup set an import reads; absent means the target user's own set. */
internal const val PARAM_SOURCE_SET_USER = "param_source_set_user"

// Android gives each user a range of this many uids.
private const val UIDS_PER_USER = 100_000

/** The user Blocker runs in. */
internal fun ownUser(): UserId = UserId(Process.myUid() / UIDS_PER_USER)

/** The user stored under [key], or [default] when there is none or it is not a real user id. */
internal fun Data.userOr(key: String, default: UserId): UserId {
    val value = getInt(key, -1)
    return if (value >= 0) UserId(value) else default
}

/** The backup set an import reads: the one given, or else [target]'s own. */
internal fun Data.sourceSetFor(target: UserId): BackupSet = BackupSet(userOr(PARAM_SOURCE_SET_USER, target))

/** Input data with every non-null value of [pairs]; null values are left out, so they read as absent. */
internal fun inputOf(vararg pairs: Pair<String, Any?>): Data = workDataOf(*pairs.filter { it.second != null }.toTypedArray())

/** The backup folder a work was given, or the result code that says why it cannot be used. */
internal sealed interface BackupFolder {
    data class Ready(val storage: BackupStorage) : BackupFolder

    data class Unusable(val code: Int) : BackupFolder
}

/** Opens [path], the backup folder from the settings, as earlier versions checked it. */
internal fun openBackupFolder(context: Context, path: String?, ioDispatcher: CoroutineDispatcher): BackupFolder {
    if (path.isNullOrEmpty()) return BackupFolder.Unusable(RuleWorkResult.FOLDER_NOT_DEFINED)
    val storage = DocumentTreeBackupStorage.open(context, path, ioDispatcher) ?: return BackupFolder.Unusable(RuleWorkResult.MISSING_STORAGE_PERMISSION)
    return BackupFolder.Ready(storage)
}

/** A failed work's result with only [code]. */
internal fun failureOf(code: Int): ListenableWorker.Result = ListenableWorker.Result.failure(workDataOf(RuleWorkResult.PARAM_WORK_RESULT to code))

/**
 * The work result for [this] run. A run that could not start fails with the code for its reason.
 * A completed run succeeds when it applied something or nothing failed, so a partly failed run
 * reports its counts as a success; when nothing was applied and something failed it fails with
 * [RuleWorkResult.MISSING_ROOT_PERMISSION] if every failure was a missing privilege, else with
 * [RuleWorkResult.UNEXPECTED_EXCEPTION]. Every completed run's output holds [countKey] with
 * [count] of its summary and the counts of [RuleWorkResult].
 */
internal fun RuleRunResult.toWorkResult(countKey: String, count: (RuleRunSummary) -> Int): ListenableWorker.Result = when (this) {
    is RuleRunResult.Failed -> failureOf(reason.toWorkCode())

    is RuleRunResult.Completed -> {
        val output = workDataOf(
            countKey to count(summary),
            RuleWorkResult.PARAM_FAILED_COUNT to summary.failedCount,
            RuleWorkResult.PARAM_SKIPPED_COUNT to summary.skippedCount,
            RuleWorkResult.PARAM_UNCHANGED_COUNT to summary.unchanged,
            RuleWorkResult.PARAM_UNREADABLE_APP_COUNT to (summary.failed[FailureReason.STATE_UNREADABLE] ?: 0),
            RuleWorkResult.PARAM_FIREWALL_UNAVAILABLE to summary.firewallUnavailable,
        )
        if (summary.applied > 0 || summary.failedCount == 0) {
            ListenableWorker.Result.success(output)
        } else {
            val code = if (summary.failed.keys == setOf(FailureReason.NO_PRIVILEGE)) RuleWorkResult.MISSING_ROOT_PERMISSION else RuleWorkResult.UNEXPECTED_EXCEPTION
            ListenableWorker.Result.failure(Data.Builder().putAll(output).putInt(RuleWorkResult.PARAM_WORK_RESULT, code).build())
        }
    }
}

private fun FailureReason.toWorkCode(): Int = when (this) {
    FailureReason.STORAGE_UNAVAILABLE -> RuleWorkResult.MISSING_STORAGE_PERMISSION
    FailureReason.NO_PRIVILEGE -> RuleWorkResult.MISSING_ROOT_PERMISSION
    else -> RuleWorkResult.UNEXPECTED_EXCEPTION
}
