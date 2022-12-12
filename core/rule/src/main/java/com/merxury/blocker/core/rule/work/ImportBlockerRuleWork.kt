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
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.merxury.blocker.core.PreferenceUtil
import com.merxury.blocker.core.rule.R
import com.merxury.blocker.core.rule.Rule
import com.merxury.blocker.core.rule.entity.BlockerRule
import com.merxury.blocker.core.rule.util.NotificationUtil
import com.merxury.blocker.core.rule.util.StorageUtil
import com.merxury.blocker.core.utils.ApplicationUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import timber.log.Timber

class ImportBlockerRuleWork(context: Context, params: WorkerParameters) :
    CoroutineWorker(context, params) {

    override suspend fun getForegroundInfo(): ForegroundInfo {
        return updateNotification("", 0, 0)
    }

    @OptIn(ExperimentalSerializationApi::class)
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        Timber.i("Start to import app rules")
        var successCount = 0
        try {
            val context = applicationContext
            val shouldRestoreSystemApp = PreferenceUtil.shouldRestoreSystemApps(context)
            val controllerType = PreferenceUtil.getControllerType(context)
            val packageManager = context.packageManager
            val documentDir = StorageUtil.getSavedFolder(context)
            if (documentDir == null) {
                Timber.e("Cannot create DocumentFile")
//                ToastUtil.showToast(R.string.dir_is_invalid, Toast.LENGTH_LONG)
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
        } catch (e: Exception) {
            Timber.e("Failed to import blocker rules", e)
//            ToastUtil.showToast(R.string.import_failed_message, Toast.LENGTH_LONG)
            return@withContext Result.failure()
        }
        if (successCount == 0) {
            Timber.i("No rules were imported.")
//            ToastUtil.showToast(R.string.no_rules_imported, Toast.LENGTH_LONG)
            return@withContext Result.failure()
        } else {
            Timber.i("Imported $successCount rules.")
//            ToastUtil.showToast(R.string.import_successfully, Toast.LENGTH_LONG)
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
            .setSmallIcon(com.merxury.blocker.core.common.R.drawable.ic_blocker_notification)
            .setProgress(total, current, false)
            .setOngoing(true)
            .addAction(android.R.drawable.ic_delete, cancel, intent)
            .build()
        return ForegroundInfo(NotificationUtil.PROCESSING_NOTIFICATION_ID, notification)
    }
}
