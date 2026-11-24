/*
 * Copyright 2025 Blocker
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
import androidx.hilt.work.HiltWorker
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkerParameters
import com.merxury.blocker.core.model.component.UserId
import com.merxury.blocker.core.rule.R
import com.merxury.blocker.core.rule.backup.FailureReason
import com.merxury.blocker.core.rule.backup.PackageManagerReset
import com.merxury.blocker.core.rule.backup.RuleRunResult
import com.merxury.blocker.core.rule.entity.RuleWorkResult
import com.merxury.blocker.core.rule.entity.RuleWorkType
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import timber.log.Timber

/** Clears the PackageManager settings that disable components of one user through [PackageManagerReset]. */
@HiltWorker
class ResetPmWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val reset: PackageManagerReset,
) : RuleNotificationWorker(context, params) {

    override fun getNotificationTitle(): Int = R.string.core_rule_reset_pm_please_wait

    override suspend fun doWork(): Result {
        val user = inputData.userOr(PARAM_TARGET_USER, ownUser())
        Timber.i("Reset the PM settings of user ${user.value}")
        val result = reset.reset(user) { current, done, total -> setForeground(updateNotification(current, done, total)) }
        Timber.i("Reset PM settings: $result")
        if (result == RuleRunResult.Failed(FailureReason.STATE_UNREADABLE)) {
            return failureOf(RuleWorkResult.UNEXPECTED_EXCEPTION)
        }
        return result.toWorkResult(PARAM_RESTORED_COUNT) { it.changedCount(RuleWorkType.RESET_PM) }
    }

    companion object {
        /** Output: the components whose setting was cleared. */
        const val PARAM_RESTORED_COUNT = "param_restored_count"

        /** Resets [targetUser], by default the user Blocker runs in. */
        fun resetPmWork(targetUser: UserId? = null) = OneTimeWorkRequestBuilder<ResetPmWorker>()
            .setInputData(inputOf(PARAM_TARGET_USER to targetUser?.value))
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .build()
    }
}
