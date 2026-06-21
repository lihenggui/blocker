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
import androidx.work.workDataOf
import com.merxury.blocker.core.data.respository.component.ComponentRepository
import com.merxury.blocker.core.dispatchers.BlockerDispatchers.IO
import com.merxury.blocker.core.dispatchers.Dispatcher
import com.merxury.blocker.core.rule.R
import com.merxury.blocker.core.rule.entity.RuleWorkResult.PARAM_WORK_RESULT
import com.merxury.blocker.core.rule.entity.RuleWorkResult.UNEXPECTED_EXCEPTION
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.collectIndexed
import kotlinx.coroutines.withContext
import timber.log.Timber

@HiltWorker
class ResetPmWorker @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted params: WorkerParameters,
    private val componentRepository: ComponentRepository,
    @Dispatcher(IO) private val ioDispatcher: CoroutineDispatcher,
) : RuleNotificationWorker(context, params) {

    override fun getNotificationTitle(): Int = R.string.core_rule_reset_pm_please_wait

    // Privileges (root/Shizuku) are enforced by the underlying controller; any failure here
    // surfaces as UNEXPECTED_EXCEPTION, matching the user-facing error flow for IFW reset.
    override suspend fun doWork(): Result = withContext(ioDispatcher) {
        Timber.i("Start restoring PM-blocked components to default state")
        var count = 0
        try {
            componentRepository.restorePmBlockedComponents()
                .collectIndexed { index, component ->
                    updateNotification(component.name, index + 1, 0)
                    count = index + 1
                }
        } catch (e: RuntimeException) {
            Timber.e(e, "Failed to restore PM-blocked components")
            return@withContext Result.failure(
                workDataOf(PARAM_WORK_RESULT to UNEXPECTED_EXCEPTION),
            )
        }
        Timber.i("Restored $count PM-blocked components")
        Result.success(workDataOf(PARAM_RESTORED_COUNT to count))
    }

    companion object {
        const val PARAM_RESTORED_COUNT = "param_restored_count"

        fun resetPmWork() = OneTimeWorkRequestBuilder<ResetPmWorker>()
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .build()
    }
}
