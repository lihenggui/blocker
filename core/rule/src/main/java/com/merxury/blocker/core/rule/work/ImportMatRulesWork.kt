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
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.merxury.blocker.core.ComponentControllerProxy
import com.merxury.blocker.core.PreferenceUtil
import com.merxury.blocker.core.rule.R
import com.merxury.blocker.core.rule.Rule
import com.merxury.blocker.core.rule.util.NotificationUtil
import com.merxury.blocker.core.utils.ApplicationUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber

class ImportMatRulesWork(context: Context, params: WorkerParameters) :
    CoroutineWorker(context, params) {

    private val uriString = params.inputData.getString(KEY_FILE_URI)

    override suspend fun getForegroundInfo(): ForegroundInfo {
        return updateNotification("", 0, 0)
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val uri = Uri.parse(uriString)
        if (uri == null) {
            Timber.e("File URI is null, cannot import MAT rules")
            return@withContext Result.failure()
        }
        val context = applicationContext
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
        val title = applicationContext.getString(R.string.import_mat_rule_please_wait)
        val cancel = applicationContext.getString(R.string.cancel)
        // This PendingIntent can be used to cancel the worker
        val intent = WorkManager.getInstance(applicationContext)
            .createCancelPendingIntent(getId())
        // Create a Notification channel if necessary
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationUtil.createProgressingNotificationChannel(applicationContext)
        }
        val notification = NotificationCompat.Builder(applicationContext, id)
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
        const val KEY_FILE_URI = "key_file_uri"
    }
}
