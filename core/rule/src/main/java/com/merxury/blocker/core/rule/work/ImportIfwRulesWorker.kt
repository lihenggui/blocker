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
import com.merxury.blocker.core.ComponentControllerProxy
import com.merxury.blocker.core.PreferenceUtil
import com.merxury.blocker.core.model.data.ControllerType
import com.merxury.blocker.core.network.BlockerDispatchers.IO
import com.merxury.blocker.core.network.Dispatcher
import com.merxury.blocker.core.rule.R
import com.merxury.blocker.core.rule.util.NotificationUtil
import com.merxury.blocker.core.rule.util.StorageUtil
import com.merxury.blocker.core.utils.ApplicationUtil
import com.merxury.ifw.util.RuleSerializer
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import timber.log.Timber

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
        Timber.i("Started to import IFW rules")
        val total: Int
        var imported = 0
        try {
            val shouldRestoreSystemApps = PreferenceUtil.shouldRestoreSystemApps(context)
            // Check directory is readable
            val ifwFolder = StorageUtil.getOrCreateIfwFolder(context)
            if (ifwFolder == null) {
                Timber.e("Folder hasn't been set yet.")
//                ToastUtil.showToast(R.string.import_ifw_failed_message, Toast.LENGTH_LONG)
                return@withContext Result.failure()
            }
            val controller = ComponentControllerProxy.getInstance(ControllerType.IFW, context)
            val files =
                ifwFolder.listFiles().filter { it.isFile && it.name?.endsWith(".xml") == true }
            total = files.count()
            // Start importing files
            files.forEach { documentFile ->
                Timber.i("Importing ${documentFile.name}")
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
                        Timber.i("Skipping system app $packageName")
                        return@forEach
                    }
                    controller.batchDisable(activities) {}
                    controller.batchDisable(broadcast) {}
                    controller.batchDisable(service) {}
                    imported++
                }
            }
        } catch (e: Exception) {
            Timber.e("Cannot import IFW rules", e)
//            ToastUtil.showToast(R.string.import_ifw_failed_message, Toast.LENGTH_LONG)
            return@withContext Result.failure()
        }
        Timber.i("Imported $imported IFW rules.")
//        ToastUtil.showToast(R.string.import_successfully, Toast.LENGTH_LONG)
        return@withContext Result.success()
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
        fun importIfwWork() = OneTimeWorkRequestBuilder<ImportIfwRulesWorker>()
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .build()
    }
}
