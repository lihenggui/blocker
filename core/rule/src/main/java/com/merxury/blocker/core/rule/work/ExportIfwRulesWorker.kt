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
import com.merxury.blocker.core.rule.entity.RuleWorkResult
import com.merxury.blocker.core.rule.util.StorageUtil
import com.merxury.blocker.core.utils.FileUtils
import com.merxury.ifw.util.IfwStorageUtils
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.io.IOException

@HiltWorker
class ExportIfwRulesWorker @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted params: WorkerParameters,
    @Dispatcher(IO) private val ioDispatcher: CoroutineDispatcher,
) : RuleNotificationWorker(context, params) {
    override fun getNotificationTitle(): Int = R.string.backing_up_ifw_please_wait

    override suspend fun doWork(): Result = withContext(ioDispatcher) {
        val folderPath = inputData.getString(PARAM_FOLDER_PATH)
        if (folderPath.isNullOrEmpty()) {
            return@withContext Result.failure(
                workDataOf(PARAM_WORK_RESULT to RuleWorkResult.FOLDER_NOT_DEFINED),
            )
        }
        if (!StorageUtil.isFolderReadable(context, folderPath)) {
            return@withContext Result.failure(
                workDataOf(PARAM_WORK_RESULT to RuleWorkResult.MISSING_STORAGE_PERMISSION),
            )
        }
        val backupPackageName = inputData.getString(PARAM_BACKUP_PACKAGE_NAME)
        if (!backupPackageName.isNullOrEmpty()) {
            try {
                val result = exportForSingleApplication(backupPackageName, folderPath)
                return@withContext Result.success(workDataOf(PARAM_EXPORT_COUNT to result))
            } catch (e: IOException) {
                Timber.e(e, "Can't read IFW rules for $backupPackageName")
                return@withContext Result.failure(
                    workDataOf(PARAM_WORK_RESULT to RuleWorkResult.MISSING_ROOT_PERMISSION),
                )
            }
        }
        Timber.i("Start to export IFW rules.")
        var current = 0
        try {
            val ifwFolder = IfwStorageUtils.getIfwFolder()
            val files = FileUtils.listFiles(ifwFolder)
            val total = files.count()
            files.forEach {
                Timber.i("Export $it")
                val filename = it.split(File.separator).last()
                setForeground(updateNotification(filename, current, total))
                val content = FileUtils.read(ifwFolder + it)
                StorageUtil.saveIfwToStorage(context, folderPath, filename, content, ioDispatcher)
                current++
            }
        } catch (e: RuntimeException) {
            Timber.e(e, "Failed to export IFW rules")
            return@withContext Result.failure(
                workDataOf(PARAM_WORK_RESULT to RuleWorkResult.MISSING_ROOT_PERMISSION),
            )
        } catch (e: IOException) {
            Timber.e(e, "Can't read IFW rules")
            return@withContext Result.failure(
                workDataOf(PARAM_WORK_RESULT to RuleWorkResult.UNEXPECTED_EXCEPTION),
            )
        }
        Timber.i("Export IFW rules finished, success count = $current.")
        return@withContext Result.success(
            workDataOf(PARAM_EXPORT_COUNT to current),
        )
    }

    // Export IFW rules for a single application
    // Return value is the number of exported rules
    private suspend fun exportForSingleApplication(packageName: String, backupFolder: String): Int {
        Timber.d("Export IFW rules for $packageName")
        return withContext(ioDispatcher) {
            val ifwFolder = IfwStorageUtils.getIfwFolder()
            val files = FileUtils.listFiles(ifwFolder)
            val targetFileName = packageName + Rule.IFW_EXTENSION
            if (files.contains(targetFileName)) {
                val content = FileUtils.read(ifwFolder + targetFileName)
                StorageUtil.saveIfwToStorage(
                    context = context,
                    baseFolder = backupFolder,
                    filename = targetFileName,
                    content = content,
                    dispatcher = ioDispatcher,
                )
                1
            } else {
                0
            }
        }
    }

    companion object {
        const val PARAM_EXPORT_COUNT = "param_export_count"
        const val PARAM_WORK_RESULT = "param_work_result"
        private const val PARAM_FOLDER_PATH = "param_folder_path"
        private const val PARAM_BACKUP_PACKAGE_NAME = "param_backup_package_name"

        fun exportWork(folderPath: String?, backupPackageName: String? = null) =
            OneTimeWorkRequestBuilder<ExportIfwRulesWorker>()
                .setInputData(
                    workDataOf(
                        PARAM_FOLDER_PATH to folderPath,
                        PARAM_BACKUP_PACKAGE_NAME to backupPackageName,
                    ),
                )
                .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                .build()
    }
}
