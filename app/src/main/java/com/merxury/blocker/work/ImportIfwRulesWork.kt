package com.merxury.blocker.work

import android.content.Context
import android.content.pm.ComponentInfo
import android.os.Build
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.documentfile.provider.DocumentFile
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.elvishew.xlog.XLog
import com.merxury.blocker.R
import com.merxury.blocker.core.ComponentControllerProxy
import com.merxury.blocker.core.root.EControllerMethod
import com.merxury.blocker.util.NotificationUtil
import com.merxury.blocker.util.PreferenceUtil
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
        var total = 0
        var imported = 0
        try {
            // Check directory is readable
            val ifwBackupFolderUri = PreferenceUtil.getIfwRulePath(context)
            if (ifwBackupFolderUri == null) {
                logger.e("IFW folder hasn't been set yet.")
                return@withContext Result.failure()
            }
            val controller = ComponentControllerProxy.getInstance(EControllerMethod.IFW, context)
            val folder = DocumentFile.fromTreeUri(context, ifwBackupFolderUri)
            if (folder == null) {
                logger.e("Cannot open ifw backup folder")
                return@withContext Result.failure()
            }
            val files = folder.listFiles()
                .filter { it.isFile && it.name?.endsWith(".xml") == true }
            total = files.count()
            // Start importing files
            files.forEach { documentFile ->
                logger.i("Importing ${documentFile.name}")
                updateNotification(documentFile.name ?: "", imported, total)
                context.contentResolver.openInputStream(documentFile.uri)?.use { stream ->
                    val rule = RuleSerializer.deserialize(stream) ?: return@forEach
                    val activities = rule.activity?.componentFilters
                        ?.asSequence()
                        ?.map { filter -> filter.name.split("/") }
                        ?.map { names ->
                            val component = ComponentInfo()
                            component.packageName = names[0]
                            component.name = names[1]
                            component
                        }
                        ?.toList() ?: mutableListOf()
                    val broadcast = rule.broadcast?.componentFilters
                        ?.asSequence()
                        ?.map { filter -> filter.name.split("/") }
                        ?.map { names ->
                            val component = ComponentInfo()
                            component.packageName = names[0]
                            component.name = names[1]
                            component
                        }
                        ?.toList() ?: mutableListOf()
                    val service = rule.service?.componentFilters
                        ?.asSequence()
                        ?.map { filter -> filter.name.split("/") }
                        ?.map { names ->
                            val component = ComponentInfo()
                            component.packageName = names[0]
                            component.name = names[1]
                            component
                        }
                        ?.toList() ?: mutableListOf()
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