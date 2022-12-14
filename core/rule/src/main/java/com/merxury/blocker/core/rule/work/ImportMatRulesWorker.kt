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
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ForegroundInfo
import androidx.work.OneTimeWorkRequest
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.merxury.blocker.core.PreferenceUtil
import com.merxury.blocker.core.controllers.ComponentControllerProxy
import com.merxury.blocker.core.network.BlockerDispatchers.IO
import com.merxury.blocker.core.network.Dispatcher
import com.merxury.blocker.core.rule.R
import com.merxury.blocker.core.rule.Rule
import com.merxury.blocker.core.rule.util.NotificationUtil
import com.merxury.blocker.core.utils.ApplicationUtil
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import timber.log.Timber

@HiltWorker
class ImportMatRulesWorker @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted params: WorkerParameters,
    @Dispatcher(IO) private val ioDispatcher: CoroutineDispatcher,
) : CoroutineWorker(context, params) {

    private val uriString = params.inputData.getString(KEY_FILE_URI)

    override suspend fun getForegroundInfo(): ForegroundInfo {
        return updateNotification("", 0, 0)
    }

    override suspend fun doWork(): Result = withContext(ioDispatcher) {
        val uri = Uri.parse(uriString)
        if (uri == null) {
            Timber.e("File URI is null, cannot import MAT rules")
            return@withContext Result.failure()
        }
        val controllerType = PreferenceUtil.getControllerType(context)
        val controller = ComponentControllerProxy.getInstance(controllerType, context)
        val shouldRestoreSystemApps = PreferenceUtil.shouldRestoreSystemApps(context)
        val uninstalledAppList = mutableListOf<String>()
        var total: Int
        var current = 0
        try {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                val fileContent = stream.reader().readLines().filter { line ->
                    line.trim().isNotEmpty() && line.contains("/")
                }
                total = fileContent.count()
                fileContent.forEach { line ->
                    val trimmedLine = line.split(" ").firstOrNull() ?: return@forEach
                    val splitResult = trimmedLine.split("/")
                    val packageName = splitResult[0]
                    val name = splitResult[1]
                    if (Rule.isApplicationUninstalled(context, uninstalledAppList, packageName)) {
                        return@forEach
                    }
                    val isSystemApp = ApplicationUtil.isSystemApp(
                        context.packageManager, packageName
                    )
                    if (!shouldRestoreSystemApps && isSystemApp) {
                        return@forEach
                    }
                    setForeground(updateNotification(packageName, current, total))
                    controller.disable(packageName, name)
                    current++
                }
            }
        } catch (e: Exception) {
            Timber.e("Failed to import MAT files.", e)
//            ToastUtil.showToast(R.string.import_mat_failed_message, Toast.LENGTH_LONG)
        }
        return@withContext Result.success()
    }

    private fun updateNotification(name: String, current: Int, total: Int): ForegroundInfo {
        val id = NotificationUtil.PROCESSING_INDICATOR_CHANNEL_ID
        val title = context.getString(R.string.import_mat_rule_please_wait)
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
        private const val KEY_FILE_URI = "key_file_uri"

        fun importWork(fileUri: Uri): OneTimeWorkRequest {
            val data = Data.Builder()
                .putString(KEY_FILE_URI, fileUri.toString())
                .build()
            return OneTimeWorkRequestBuilder<ImportMatRulesWorker>()
                .setInputData(data)
                .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                .build()
        }
    }
}
