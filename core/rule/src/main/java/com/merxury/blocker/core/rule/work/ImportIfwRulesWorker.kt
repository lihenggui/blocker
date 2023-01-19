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
import android.content.pm.ComponentInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.merxury.blocker.core.controllers.ComponentControllerProxy
import com.merxury.blocker.core.model.data.ControllerType
import com.merxury.blocker.core.network.BlockerDispatchers.IO
import com.merxury.blocker.core.network.Dispatcher
import com.merxury.blocker.core.rule.R
import com.merxury.blocker.core.rule.entity.RuleWorkResult.FOLDER_NOT_DEFINED
import com.merxury.blocker.core.rule.entity.RuleWorkResult.MISSING_ROOT_PERMISSION
import com.merxury.blocker.core.rule.entity.RuleWorkResult.MISSING_STORAGE_PERMISSION
import com.merxury.blocker.core.rule.entity.RuleWorkResult.UNEXPECTED_EXCEPTION
import com.merxury.blocker.core.rule.util.NotificationUtil
import com.merxury.blocker.core.rule.util.StorageUtil
import com.merxury.blocker.core.utils.ApplicationUtil
import com.merxury.ifw.util.RuleSerializer
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.IOException

@HiltWorker
class ImportIfwRulesWorker @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted params: WorkerParameters,
    @Dispatcher(IO) private val ioDispatcher: CoroutineDispatcher,
) : CoroutineWorker(context, params) {

    override suspend fun getForegroundInfo(): ForegroundInfo {
        return updateNotification("", 0, 0)
    }

    override suspend fun doWork(): Result = withContext(ioDispatcher) {
        val folderPath = inputData.getString(PARAM_FOLDER_PATH)
        if (folderPath.isNullOrEmpty()) {
            return@withContext Result.failure(
                workDataOf(PARAM_WORK_RESULT to FOLDER_NOT_DEFINED),
            )
        }
        if (!StorageUtil.isFolderReadable(context, folderPath)) {
            return@withContext Result.failure(
                workDataOf(PARAM_WORK_RESULT to MISSING_STORAGE_PERMISSION),
            )
        }
        Timber.i("Started to import IFW rules")
        val total: Int
        var importedCount = 0
        try {
            val shouldRestoreSystemApps = inputData.getBoolean(PARAM_RESTORE_SYS_APPS, false)
            // Check directory is readable
            val ifwFolder = StorageUtil.getOrCreateIfwFolder(context, folderPath)
                ?: return@withContext Result.failure(
                    workDataOf(PARAM_WORK_RESULT to UNEXPECTED_EXCEPTION),
                )
            val controller = ComponentControllerProxy.getInstance(ControllerType.IFW, context)
            val files = ifwFolder.listFiles()
                .filter { it.isFile && it.name?.endsWith(".xml") == true }
            total = files.count()
            // Start importing files
            files.forEach { documentFile ->
                Timber.i("Importing ${documentFile.name}")
                setForeground(updateNotification(documentFile.name ?: "", importedCount, total))
                var packageName: String? = null
                context.contentResolver.openInputStream(documentFile.uri)?.use { stream ->
                    val rule = RuleSerializer.deserialize(stream) ?: return@forEach
                    val activities = rule.activity?.componentFilters?.asSequence()
                        ?.map { filter -> filter.name.split("/") }?.map { names ->
                            val component = ComponentInfo()
                            component.packageName = names[0]
                            component.name = names[1]
                            packageName = component.packageName
                            component
                        }?.toList() ?: mutableListOf()
                    val broadcast = rule.broadcast?.componentFilters?.asSequence()
                        ?.map { filter -> filter.name.split("/") }?.map { names ->
                            val component = ComponentInfo()
                            component.packageName = names[0]
                            component.name = names[1]
                            packageName = component.packageName
                            component
                        }?.toList() ?: mutableListOf()
                    val service = rule.service?.componentFilters?.asSequence()
                        ?.map { filter -> filter.name.split("/") }?.map { names ->
                            val component = ComponentInfo()
                            component.packageName = names[0]
                            component.name = names[1]
                            packageName = component.packageName
                            component
                        }?.toList() ?: mutableListOf()
                    val isSystemApp =
                        ApplicationUtil.isSystemApp(context.packageManager, packageName)
                    if (!shouldRestoreSystemApps && isSystemApp) {
                        Timber.i("Skipping system app $packageName")
                        return@forEach
                    }
                    controller.batchDisable(activities) {}
                    controller.batchDisable(broadcast) {}
                    controller.batchDisable(service) {}
                    importedCount++
                }
            }
        } catch (e: RuntimeException) {
            Timber.e("Cannot import IFW rules", e)
            return@withContext Result.failure(
                workDataOf(PARAM_WORK_RESULT to MISSING_ROOT_PERMISSION),
            )
        } catch (e: IOException) {
            Timber.e("Cannot read IFW rules", e)
            return@withContext Result.failure(
                workDataOf(PARAM_WORK_RESULT to UNEXPECTED_EXCEPTION),
            )
        }
        Timber.i("Imported $importedCount IFW rules.")
        return@withContext Result.success(
            workDataOf(PARAM_IMPORT_COUNT to importedCount),
        )
    }

    private fun updateNotification(name: String, current: Int, total: Int): ForegroundInfo {
        val id = NotificationUtil.PROCESSING_INDICATOR_CHANNEL_ID
        val title = context.getString(R.string.import_ifw_please_wait)
        val cancel = context.getString(R.string.cancel)
        // This PendingIntent can be used to cancel the worker
        val intent = WorkManager.getInstance(context).createCancelPendingIntent(getId())
        // Create a Notification channel if necessary
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationUtil.createProgressingNotificationChannel(context)
        }
        val notification = NotificationCompat.Builder(context, id).setContentTitle(title)
            .setTicker(title)
            .setSubText(name)
            .setSmallIcon(com.merxury.blocker.core.common.R.drawable.ic_blocker_notification)
            .setProgress(total, current, false)
            .setOngoing(true)
            .addAction(android.R.drawable.ic_delete, cancel, intent)
            .build()
        return ForegroundInfo(NotificationUtil.PROCESSING_NOTIFICATION_ID, notification)
    }

    companion object {
        const val PARAM_IMPORT_COUNT = "param_import_count"
        const val PARAM_WORK_RESULT = "param_work_result"
        private const val PARAM_FOLDER_PATH = "param_folder_path"
        private const val PARAM_RESTORE_SYS_APPS = "param_restore_sys_apps"

        fun importIfwWork(
            backupPath: String?,
            restoreSystemApps: Boolean,
        ) = OneTimeWorkRequestBuilder<ImportIfwRulesWorker>()
            .setInputData(
                workDataOf(
                    PARAM_FOLDER_PATH to backupPath,
                    PARAM_RESTORE_SYS_APPS to restoreSystemApps,
                ),
            )
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .build()
    }
}
