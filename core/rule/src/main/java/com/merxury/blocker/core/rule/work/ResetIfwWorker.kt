/*
 * Copyright 2023 Blocker
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
import com.merxury.blocker.core.dispatchers.BlockerDispatchers.IO
import com.merxury.blocker.core.dispatchers.Dispatcher
import com.merxury.blocker.core.rule.R
import com.merxury.blocker.core.rule.Rule
import com.merxury.blocker.core.rule.entity.RuleWorkResult.MISSING_ROOT_PERMISSION
import com.merxury.blocker.core.rule.entity.RuleWorkResult.PARAM_WORK_RESULT
import com.merxury.blocker.core.rule.entity.RuleWorkResult.UNEXPECTED_EXCEPTION
import com.merxury.blocker.core.utils.FileUtils
import com.merxury.blocker.core.utils.PermissionUtils
import com.merxury.ifw.util.IfwStorageUtils
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.IOException

@HiltWorker
class ResetIfwWorker @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted params: WorkerParameters,
    @Dispatcher(IO) private val ioDispatcher: CoroutineDispatcher,
) : RuleNotificationWorker(context, params) {

    override fun getNotificationTitle(): Int = R.string.import_ifw_please_wait

    override suspend fun doWork(): Result = withContext(ioDispatcher) {
        Timber.i("Clear IFW rules")
        if (!PermissionUtils.isRootAvailable()) {
            return@withContext Result.failure(
                workDataOf(PARAM_WORK_RESULT to MISSING_ROOT_PERMISSION),
            )
        }
        val packageName = inputData.getString(PARAM_RESET_PACKAGE_NAME)
        if (!packageName.isNullOrEmpty()) {
            return@withContext clearIfwRuleForPackage(packageName)
        }
        var count = 0
        val total: Int
        try {
            val ifwFolder = IfwStorageUtils.getIfwFolder()
            val files = FileUtils.listFiles(ifwFolder)
            total = files.count()
            files.forEach {
                updateNotification(it, count, total)
                Timber.i("Delete $it")
                FileUtils.delete(
                    path = ifwFolder + it,
                    recursively = false,
                    dispatcher = ioDispatcher,
                )
                count++
            }
        } catch (e: RuntimeException) {
            Timber.e(e, "Failed to clear IFW rules")
            return@withContext Result.failure(
                workDataOf(PARAM_WORK_RESULT to MISSING_ROOT_PERMISSION),
            )
        } catch (e: IOException) {
            Timber.e(e, "Failed to clear IFW rules, IO exception occurred")
            return@withContext Result.failure(
                workDataOf(PARAM_WORK_RESULT to UNEXPECTED_EXCEPTION),
            )
        }
        Timber.i("Cleared $count IFW rules.")
        return@withContext Result.success(
            workDataOf(PARAM_CLEAR_COUNT to count),
        )
    }

    private suspend fun clearIfwRuleForPackage(packageName: String): Result {
        try {
            Timber.d("Start clearing IFW rules for package $packageName")
            val ifwFolder = IfwStorageUtils.getIfwFolder()
            val files = FileUtils.listFiles(ifwFolder)
            val filename = packageName + Rule.IFW_EXTENSION
            if (files.contains(filename)) {
                updateNotification(packageName, 1, 1)
                Timber.d("Delete IFW rules for $packageName")
                FileUtils.delete(
                    path = ifwFolder + filename,
                    recursively = false,
                    dispatcher = ioDispatcher,
                )
            }
        } catch (e: RuntimeException) {
            Timber.e(e, "Failed to clear IFW rules")
            return Result.failure(
                workDataOf(PARAM_WORK_RESULT to MISSING_ROOT_PERMISSION),
            )
        } catch (e: IOException) {
            Timber.e(e, "Failed to clear IFW rules, IO exception occurred")
            return Result.failure(
                workDataOf(PARAM_WORK_RESULT to UNEXPECTED_EXCEPTION),
            )
        }
        return Result.success(workDataOf(PARAM_CLEAR_COUNT to 1))
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
