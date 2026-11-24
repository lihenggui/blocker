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
import com.merxury.blocker.core.rule.backup.SkipReason
import com.merxury.blocker.core.rule.entity.RuleWorkResult
import com.merxury.blocker.core.rule.entity.RuleWorkType
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
 * What a work of [type] reports as changed: the backup files a backup wrote, the components an
 * import, a MAT import or a reset changed, the rules an Intent Firewall import added.
 */
internal fun RuleRunSummary.changedCount(type: RuleWorkType): Int = when (type) {
    RuleWorkType.EXPORT_BLOCKER_RULES, RuleWorkType.EXPORT_IFW_RULES -> apps

    RuleWorkType.IMPORT_BLOCKER_RULES,
    RuleWorkType.IMPORT_IFW_RULES,
    RuleWorkType.IMPORT_MAT_RULES,
    RuleWorkType.RESET_PM,
    RuleWorkType.RESET_IFW,
    -> applied
}

/**
 * The work result for [this] run. A run that could not start for want of the backup folder or of
 * privilege fails with [RuleWorkResult.MISSING_STORAGE_PERMISSION] or
 * [RuleWorkResult.MISSING_ROOT_PERMISSION]. A reset that could not recover the pre-update list
 * fails with [RuleWorkResult.LEGACY_PM_RECORDS_UNREADABLE]. A completed run succeeds with its counts,
 * including item failures such as an unreadable backup file. Only a completed run that changed
 * nothing and failed solely for privilege
 * fails, with [RuleWorkResult.MISSING_ROOT_PERMISSION] and its counts. A run that could not start
 * for another reason succeeds with one failure. [RuleWorkResult.UNEXPECTED_EXCEPTION] is left to
 * throwables nothing expected, which fail the work without a code.
 *
 * The output holds [countKey] and [RuleWorkResult.PARAM_CHANGED_COUNT] with [count] of the summary,
 * and the other counts of [RuleWorkResult]. The skipped count leaves out the system apps the
 * settings exclude, which every run of a whole user would otherwise report.
 */
internal fun RuleRunResult.toWorkResult(countKey: String, count: (RuleRunSummary) -> Int): ListenableWorker.Result = when (this) {
    is RuleRunResult.Failed -> when (reason) {
        FailureReason.STORAGE_UNAVAILABLE -> failureOf(RuleWorkResult.MISSING_STORAGE_PERMISSION)
        FailureReason.NO_PRIVILEGE -> failureOf(RuleWorkResult.MISSING_ROOT_PERMISSION)
        FailureReason.LEGACY_PM_RECORDS_UNREADABLE -> failureOf(RuleWorkResult.LEGACY_PM_RECORDS_UNREADABLE)
        else -> RuleRunResult.Completed(RuleRunSummary(failed = mapOf(reason to 1))).toWorkResult(countKey, count)
    }

    is RuleRunResult.Completed -> {
        val output = workDataOf(
            countKey to count(summary),
            RuleWorkResult.PARAM_CHANGED_COUNT to count(summary),
            RuleWorkResult.PARAM_FAILED_COUNT to summary.failedCount,
            RuleWorkResult.PARAM_SKIPPED_COUNT to summary.skipped.filterKeys { it != SkipReason.SYSTEM_APP }.values.sum(),
            RuleWorkResult.PARAM_UNCHANGED_COUNT to summary.unchanged,
            RuleWorkResult.PARAM_UNREADABLE_APP_COUNT to (summary.failed[FailureReason.STATE_UNREADABLE] ?: 0),
            RuleWorkResult.PARAM_FIREWALL_UNAVAILABLE to summary.firewallUnavailable,
        )
        if (summary.applied == 0 && summary.failedCount > 0 && summary.failed.keys == setOf(FailureReason.NO_PRIVILEGE)) {
            ListenableWorker.Result.failure(Data.Builder().putAll(output).putInt(RuleWorkResult.PARAM_WORK_RESULT, RuleWorkResult.MISSING_ROOT_PERMISSION).build())
        } else {
            ListenableWorker.Result.success(output)
        }
    }
}
