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
import android.content.pm.ComponentInfo
import android.os.Build
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.elvishew.xlog.XLog
import com.merxury.blocker.R
import com.merxury.blocker.core.ComponentControllerProxy
import com.merxury.blocker.core.root.EControllerMethod
import com.merxury.blocker.core.utils.ApplicationUtil
import com.merxury.blocker.util.NotificationUtil
import com.merxury.blocker.util.PreferenceUtil
import com.merxury.blocker.util.StorageUtil
import com.merxury.blocker.util.ToastUtil
import com.merxury.ifw.util.RuleSerializer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ImportIfwRulesWork(context: Context, params: WorkerParameters) :
    CoroutineWorker(context, params) {

    private val logger = XLog.tag("ImportIfwRulesWork")

    override suspend fun getForegroundInfo(): ForegroundInfo {
        return updateNotification("", 0, 0)
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        logger.i("Started to import IFW rules")
        val context = applicationContext
        val total: Int
        var imported = 0
        try {
            val shouldRestoreSystemApps = PreferenceUtil.shouldRestoreSystemApps(context)
            // Check directory is readable
            val ifwFolder = StorageUtil.getOrCreateIfwFolder(context)
            if (ifwFolder == null) {
                logger.e("Folder hasn't been set yet.")
                ToastUtil.showToast(R.string.import_ifw_failed_message, Toast.LENGTH_LONG)
                return@withContext Result.failure()
            }
            val controller = ComponentControllerProxy.getInstance(EControllerMethod.IFW, context)
            val files =
                ifwFolder.listFiles().filter { it.isFile && it.name?.endsWith(".xml") == true }
            total = files.count()
            // Start importing files
            files.forEach { documentFile ->
                logger.i("Importing ${documentFile.name}")
                setForeground(updateNotification(documentFile.name ?: "", imported, total))
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
                        logger.i("Skipping system app $packageName")
                        return@forEach
                    }
                    controller.batchDisable(activities) {}
                    controller.batchDisable(broadcast) {}
                    controller.batchDisable(service) {}
                    imported++
                }
            }
        } catch (e: Exception) {
            logger.e("Cannot import IFW rules", e)
            ToastUtil.showToast(R.string.import_ifw_failed_message, Toast.LENGTH_LONG)
            return@withContext Result.failure()
        }
        logger.i("Imported $imported IFW rules.")
        ToastUtil.showToast(R.string.import_successfully, Toast.LENGTH_LONG)
        return@withContext Result.success()
    }

    private fun updateNotification(name: String, current: Int, total: Int): ForegroundInfo {
        val id = NotificationUtil.PROCESSING_INDICATOR_CHANNEL_ID
        val title = applicationContext.getString(R.string.import_ifw_please_wait)
        val cancel = applicationContext.getString(R.string.cancel)
        // This PendingIntent can be used to cancel the worker
        val intent = WorkManager.getInstance(applicationContext).createCancelPendingIntent(getId())
        // Create a Notification channel if necessary
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationUtil.createProgressingNotificationChannel(applicationContext)
        }
        val notification = NotificationCompat.Builder(applicationContext, id).setContentTitle(title)
            .setTicker(title).setSubText(name).setSmallIcon(R.mipmap.ic_launcher)
            .setProgress(total, current, false)
            .setOngoing(true)
            .addAction(android.R.drawable.ic_delete, cancel, intent)
            .build()
        return ForegroundInfo(NotificationUtil.PROCESSING_NOTIFICATION_ID, notification)
    }
}
