/*
 * Copyright 2022 Blocker
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
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.documentfile.provider.DocumentFile
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.merxury.blocker.core.model.data.ControllerType
import com.merxury.blocker.core.network.BlockerDispatchers.IO
import com.merxury.blocker.core.network.Dispatcher
import com.merxury.blocker.core.rule.R
import com.merxury.blocker.core.rule.Rule
import com.merxury.blocker.core.rule.entity.BlockerRule
import com.merxury.blocker.core.rule.entity.RuleWorkResult
import com.merxury.blocker.core.rule.util.NotificationUtil
import com.merxury.blocker.core.rule.util.StorageUtil
import com.merxury.blocker.core.utils.ApplicationUtil
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import timber.log.Timber

@HiltWorker
class ImportBlockerRuleWorker @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted params: WorkerParameters,
    @Dispatcher(IO) private val ioDispatcher: CoroutineDispatcher,
) : CoroutineWorker(context, params) {

    override suspend fun getForegroundInfo(): ForegroundInfo {
        return updateNotification("", 0, 0)
    }

    @OptIn(ExperimentalSerializationApi::class)
    override suspend fun doWork(): Result = withContext(ioDispatcher) {
        Timber.i("Start to import app rules")
        var successCount = 0
        try {
            // Check storage permission first
            val backupPath = inputData.getString(PARAM_FOLDER_PATH)
            if (backupPath.isNullOrEmpty()) {
                return@withContext Result.failure(
                    workDataOf(PARAM_WORK_RESULT to RuleWorkResult.FOLDER_NOT_DEFINED)
                )
            }
            if (!StorageUtil.isFolderReadable(context, backupPath)) {
                return@withContext Result.failure(
                    workDataOf(PARAM_WORK_RESULT to RuleWorkResult.MISSING_STORAGE_PERMISSION)
                )
            }
            val shouldRestoreSystemApp = inputData.getBoolean(PARAM_RESTORE_SYS_APPS, false)
            val controllerOrdinal =
                inputData.getInt(PARAM_CONTROLLER_TYPE, ControllerType.IFW.ordinal)
            val controllerType = ControllerType.values()[controllerOrdinal]
            val packageManager = context.packageManager
            val documentDir = DocumentFile.fromTreeUri(context, Uri.parse(backupPath))
            if (documentDir == null) {
                Timber.e("Cannot create DocumentFile")
                return@withContext Result.failure()
            }
            val files = documentDir.listFiles()
                .filter { it.name?.endsWith(Rule.EXTENSION) == true }
            val total = files.count()
            var current = 1
            files.forEach {
                Timber.i("Import ${it.uri}")
                context.contentResolver.openInputStream(it.uri)?.use { input ->
                    val rule = Json.decodeFromStream<BlockerRule>(input)
                    val appInstalled =
                        ApplicationUtil.isAppInstalled(packageManager, rule.packageName)
                    val isSystemApp = ApplicationUtil.isSystemApp(packageManager, rule.packageName)
                    if (!appInstalled) {
                        Timber.w("App ${rule.packageName} is not installed, skipping")
                        current++
                        return@forEach
                    }
                    if (!shouldRestoreSystemApp && isSystemApp) {
                        Timber.d("App ${rule.packageName} is a system app, skipping")
                        current++
                        return@forEach
                    }
                    setForeground(updateNotification(rule.packageName ?: "", current, total))
                    val result = Rule.import(context, rule, controllerType)
                    if (result) {
                        successCount++
                    }
                    current++
                }
            }
            Timber.i("Import rules finished.")
        } catch (e: RuntimeException) {
            Timber.e("Failed to import blocker rules", e)
            return@withContext Result.failure(
                workDataOf(PARAM_WORK_RESULT to RuleWorkResult.MISSING_ROOT_PERMISSION)
            )
        }
        Timber.i("Imported $successCount rules.")
        return@withContext Result.success(
            workDataOf(PARAM_IMPORT_COUNT to successCount)
        )
    }

    private fun updateNotification(name: String, current: Int, total: Int): ForegroundInfo {
        val id = NotificationUtil.PROCESSING_INDICATOR_CHANNEL_ID
        val title = context.getString(R.string.import_app_rules_please_wait)
        val cancel = context.getString(R.string.cancel)
        // This PendingIntent can be used to cancel the worker
        val intent = WorkManager.getInstance(context)
            .createCancelPendingIntent(getId())
        // Create a Notification channel if necessary
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationUtil.createProgressingNotificationChannel(context)
        }
        val notification = NotificationCompat.Builder(context, id)
            .setContentTitle(title)
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
        private const val PARAM_CONTROLLER_TYPE = "param_controller_type"

        fun importWork(
            backupPath: String?,
            restoreSystemApps: Boolean,
            controllerType: ControllerType
        ) = OneTimeWorkRequestBuilder<ImportBlockerRuleWorker>()
            .setInputData(
                workDataOf(
                    PARAM_FOLDER_PATH to backupPath,
                    PARAM_RESTORE_SYS_APPS to restoreSystemApps,
                    PARAM_CONTROLLER_TYPE to controllerType.ordinal
                )
            )
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .build()
    }
}
