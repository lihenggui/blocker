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
import com.merxury.blocker.core.data.wake.FirewallResetRepository
import com.merxury.blocker.core.dispatchers.BlockerDispatchers.IO
import com.merxury.blocker.core.dispatchers.Dispatcher
import com.merxury.blocker.core.rule.IFW_EXTENSION
import com.merxury.blocker.core.rule.R
import com.merxury.blocker.core.rule.entity.RuleWorkResult
import com.merxury.blocker.core.rule.entity.RuleWorkResult.MISSING_ROOT_PERMISSION
import com.merxury.blocker.core.rule.entity.RuleWorkResult.PARAM_WORK_RESULT
import com.merxury.blocker.core.rule.entity.RuleWorkResult.UNEXPECTED_EXCEPTION
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import timber.log.Timber

@HiltWorker
class ResetIfwWorker @AssistedInject constructor(
    @Assisted context: Context,
    private val repository: FirewallResetRepository,
    @Assisted params: WorkerParameters,
    @Dispatcher(IO) private val ioDispatcher: CoroutineDispatcher,
) : RuleNotificationWorker(context, params) {

    override fun getNotificationTitle(): Int = R.string.core_rule_reset_ifw_please_wait

    override suspend fun doWork(): Result = withContext(ioDispatcher) {
        Timber.i("Clear IFW rules")
        val packageName = inputData.getString(PARAM_RESET_PACKAGE_NAME)?.takeIf { it.isNotEmpty() }
        val count: Int
        try {
            count = repository.reset(packageName) { filename, index, total ->
                setForegroundSafely(updateNotification(filename + IFW_EXTENSION, index, total))
            } ?: return@withContext Result.failure(workDataOf(PARAM_WORK_RESULT to MISSING_ROOT_PERMISSION))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.e(e, "Failed to clear IFW rules")
            val reason = when (e) {
                is SecurityException -> MISSING_ROOT_PERMISSION
                else -> UNEXPECTED_EXCEPTION
            }
            return@withContext Result.failure(workDataOf(PARAM_WORK_RESULT to reason))
        }
        Timber.i("Cleared $count IFW rules.")
        return@withContext Result.success(
            workDataOf(PARAM_CLEAR_COUNT to count, RuleWorkResult.PARAM_CHANGED_COUNT to count),
        )
    }

    companion object {
        const val PARAM_CLEAR_COUNT = "param_clear_count"
        private const val PARAM_RESET_PACKAGE_NAME = "param_reset_package_name"

        fun clearIfwWork(packageName: String? = null) = OneTimeWorkRequestBuilder<ResetIfwWorker>()
            .setInputData(
                workDataOf(PARAM_RESET_PACKAGE_NAME to packageName),
            )
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .build()
    }
}
