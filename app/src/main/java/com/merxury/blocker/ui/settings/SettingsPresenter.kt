package com.merxury.blocker.ui.settings

import android.content.Context
import com.elvishew.xlog.XLog
import com.google.gson.Gson
import com.merxury.blocker.R
import com.merxury.blocker.exception.RootUnavailableException
import com.merxury.blocker.rule.Rule
import com.merxury.blocker.rule.entity.BlockerRule
import com.merxury.blocker.util.NotificationUtil
import com.merxury.libkit.utils.ApplicationUtil
import com.merxury.libkit.utils.FileUtils
import com.stericson.RootTools.RootTools
import kotlinx.coroutines.*
import java.io.File
import java.io.FileNotFoundException
import java.io.FileReader

class SettingsPresenter(
    private val context: Context,
    private val settingsView: SettingsContract.SettingsView
) : SettingsContract.SettingsPresenter {
    private val logger = XLog.tag("SettingsPresenter").build()
    private val exceptionHandler = CoroutineExceptionHandler { _, e: Throwable -> logger.e(e) }
    private val uiScope = CoroutineScope(Dispatchers.Main)

    override fun exportAllRules() = uiScope.launch {
        var succeedCount = 0
        var failedCount = 0
        var appCount: Int
        val errorHandler = CoroutineExceptionHandler { _, e ->
            failedCount++
            logger.e(e)
        }
        withContext(Dispatchers.IO + errorHandler) {
            checkRootAccess()
            val applicationList = ApplicationUtil.getApplicationList(context)
            appCount = applicationList.size
            NotificationUtil.createProcessingNotification(context, appCount)
            applicationList.forEach { currentApp ->
                Rule.export(context, currentApp.packageName)
                succeedCount++
                NotificationUtil.updateProcessingNotification(
                    context,
                    currentApp.label,
                    (succeedCount + failedCount),
                    appCount
                )
            }
            delay(1000L)
            NotificationUtil.finishProcessingNotification(context, succeedCount)
        }
        settingsView.showExportResult(true, succeedCount, failedCount)
    }

    override fun importAllRules() = uiScope.launch {
        var restoredCount = 0
        var rulesCount: Int
        withContext(Dispatchers.IO + exceptionHandler) {
            checkRootAccess()
            rulesCount = FileUtils.getFileCounts(
                Rule.getBlockerRuleFolder(context).absolutePath,
                Rule.EXTENSION
            )
            NotificationUtil.createProcessingNotification(context, rulesCount)
            val files = FileUtils.listFiles(Rule.getBlockerRuleFolder(context).absolutePath)
            if (files.isEmpty()) {
                return@withContext
            }
            files.filter {
                it.endsWith(Rule.EXTENSION)
            }.forEach {
                val rule = Gson().fromJson(FileReader(it), BlockerRule::class.java)
                if (!ApplicationUtil.isAppInstalled(context.packageManager, rule.packageName)) {
                    return@forEach
                }
                Rule.import(context, File(it))
                restoredCount++
                NotificationUtil.updateProcessingNotification(
                    context,
                    rule.packageName ?: "",
                    restoredCount,
                    rulesCount
                )
            }
        }
        NotificationUtil.finishProcessingNotification(context, restoredCount)
    }

    override fun exportAllIfwRules() = uiScope.launch {
        withContext(Dispatchers.IO + exceptionHandler) {
            checkRootAccess()
            NotificationUtil.createProcessingNotification(context, 0)
            val exportedCount = Rule.exportIfwRules(context)
            NotificationUtil.finishProcessingNotification(context, exportedCount)
        }
    }

    override fun importAllIfwRules() = uiScope.launch {
        var count = 0
        withContext(Dispatchers.IO + exceptionHandler) {
            checkRootAccess()
            NotificationUtil.createProcessingNotification(context, 0)
            count = Rule.importIfwRules(context)
            NotificationUtil.finishProcessingNotification(context, count)
        }
        settingsView.showExportResult(true, count, 0)
    }

    override fun resetIFW() = uiScope.launch {
        val errorHandler = CoroutineExceptionHandler { _, e ->
            logger.e(e)
            settingsView.showMessage(R.string.ifw_reset_error)
        }
        var result = false
        withContext(Dispatchers.IO + errorHandler) {
            checkRootAccess()
            result = Rule.resetIfw()
        }
        if (result) {
            settingsView.showMessage(R.string.done)
        } else {
            settingsView.showMessage(R.string.ifw_reset_error)
        }
    }

    override fun importMatRules(filePath: String?) = uiScope.launch {
        val errorHandler = CoroutineExceptionHandler { _, e ->
            logger.e(e)
            NotificationUtil.finishProcessingNotification(context, 0)
        }
        withContext(Dispatchers.IO + errorHandler) {
            checkRootAccess()
            NotificationUtil.createProcessingNotification(context, 0)
            if (filePath == null) {
                throw NullPointerException("File path cannot be null")
            }
            val file = File(filePath)
            if (!file.exists()) {
                throw FileNotFoundException("Cannot find MyAndroidTools Rule File: ${file.path}")
            }
            val result = Rule.importMatRules(context, file) { context, name, current, total ->
                NotificationUtil.updateProcessingNotification(context, name, current, total)
            }
            Thread.sleep(1000)
            NotificationUtil.finishProcessingNotification(
                context,
                result.failedCount + result.succeedCount
            )
        }
    }

    override fun start(context: Context) {

    }

    override fun destroy() {

    }

    private fun checkRootAccess() {
        if (!RootTools.isAccessGiven()) {
            throw RootUnavailableException()
        }
    }
}