/*
 * Copyright 2022 Blocker
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *       https://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 */

package com.merxury.blocker.work

import android.content.Context
import android.os.Build
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.elvishew.xlog.XLog
import com.google.gson.Gson
import com.merxury.blocker.R
import com.merxury.blocker.core.rule.Rule
import com.merxury.blocker.core.rule.entity.BlockerRule
import com.merxury.blocker.core.utils.ApplicationUtil
import com.merxury.blocker.util.NotificationUtil
import com.merxury.blocker.util.PreferenceUtil
import com.merxury.blocker.util.StorageUtil
import com.merxury.blocker.util.ToastUtil
import java.io.InputStreamReader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ImportBlockerRuleWork(context: Context, params: WorkerParameters) :
    CoroutineWorker(context, params) {
    private val logger = XLog.tag("ImportBlockerRuleWork")

    override suspend fun getForegroundInfo(): ForegroundInfo {
        return updateNotification("", 0, 0)
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        logger.i("Start to import app rules")
        var successCount = 0
        try {
            val context = applicationContext
            val shouldRestoreSystemApp = PreferenceUtil.shouldRestoreSystemApps(context)
            val controllerType = PreferenceUtil.getControllerType(context)
            val packageManager = context.packageManager
            val documentDir = StorageUtil.getSavedFolder(context)
            if (documentDir == null) {
                logger.e("Cannot create DocumentFile")
                ToastUtil.showToast(R.string.dir_is_invalid, Toast.LENGTH_LONG)
                return@withContext Result.failure()
            }
            val files = documentDir.listFiles()
                .filter { it.name?.endsWith(Rule.EXTENSION) == true }
            val total = files.count()
            var current = 1
            files.forEach {
                logger.i("Import ${it.uri}")
                val inputStream =
                    InputStreamReader(context.contentResolver.openInputStream(it.uri))
                val rule = Gson().fromJson(inputStream, BlockerRule::class.java)
                val appInstalled = ApplicationUtil.isAppInstalled(packageManager, rule.packageName)
                val isSystemApp = ApplicationUtil.isSystemApp(packageManager, rule.packageName)
                if (!appInstalled) {
                    logger.w("App ${rule.packageName} is not installed, skipping")
                    current++
                    return@forEach
                }
                if (!shouldRestoreSystemApp && isSystemApp) {
                    logger.d("App ${rule.packageName} is a system app, skipping")
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
            logger.i("Import rules finished.")
        } catch (e: Exception) {
            logger.e("Failed to import blocker rules", e)
            ToastUtil.showToast(R.string.import_failed_message, Toast.LENGTH_LONG)
            return@withContext Result.failure()
        }
        if (successCount == 0) {
            logger.i("No rules were imported.")
            ToastUtil.showToast(R.string.no_rules_imported, Toast.LENGTH_LONG)
            return@withContext Result.failure()
        } else {
            logger.i("Imported $successCount rules.")
            ToastUtil.showToast(R.string.import_successfully, Toast.LENGTH_LONG)
            return@withContext Result.success()
        }
    }

    private fun updateNotification(name: String, current: Int, total: Int): ForegroundInfo {
        val id = NotificationUtil.PROCESSING_INDICATOR_CHANNEL_ID
        val title = applicationContext.getString(R.string.import_app_rules_please_wait)
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
            .setSmallIcon(R.mipmap.ic_launcher)
            .setProgress(total, current, false)
            .setOngoing(true)
            .addAction(android.R.drawable.ic_delete, cancel, intent)
            .build()
        return ForegroundInfo(NotificationUtil.PROCESSING_NOTIFICATION_ID, notification)
    }
}
