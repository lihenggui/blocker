package com.merxury.blocker.work

import android.content.Context
import androidx.documentfile.provider.DocumentFile
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.elvishew.xlog.XLog
import com.google.gson.Gson
import com.merxury.blocker.rule.Rule
import com.merxury.blocker.rule.entity.BlockerRule
import com.merxury.blocker.util.PreferenceUtil
import com.merxury.libkit.utils.ApplicationUtil
import com.merxury.libkit.utils.PermissionUtils
import java.io.InputStreamReader

class ImportAllRuleWork(appContext: Context, workerParams: WorkerParameters) :
    CoroutineWorker(appContext, workerParams) {
    private val logger = XLog.tag("ExportAllRuleWork")

    override suspend fun doWork(): Result {
        if (!PermissionUtils.isRootAvailable) {
            logger.e("No root permission, stop exporting")
            return Result.failure()
        }
        val context = applicationContext
        val folderUri = PreferenceUtil.getSavedRulePath(context)
        if (folderUri == null) {
            logger.e("Cannot read rule path")
            return Result.failure()
        }
        val documentDir = DocumentFile.fromTreeUri(applicationContext, folderUri)
        if (documentDir == null) {
            logger.e("Cannot create DocumentFile")
            return Result.failure()
        }
        documentDir.listFiles()
            .filter { it.name?.endsWith(Rule.EXTENSION) == true }
            .forEach {
                val inputStream = InputStreamReader(context.contentResolver.openInputStream(it.uri))
                val rule = Gson().fromJson(inputStream, BlockerRule::class.java)
                if (!ApplicationUtil.isAppInstalled(context.packageManager, rule.packageName)) {
                    return@forEach
                }
                Rule.import(context, rule)
            }
//        applicationContext.contentResolver.query(folderUri, null, null, null, null)?.use {
//            logger.i("Opened $folderUri, file count = ${it.count}")
//            val nameIndex = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
//            val sizeIndex = it.getColumnIndex(OpenableColumns.SIZE)
//            val uriIndex = it.getColumnIndexOrThrow(MediaStore.Images.Media.DATA)
//            it.moveToFirst()
//            do {
//                val fileName = it.getString(nameIndex)
//                val fileSize = it.getLong(sizeIndex)
//                logger.i("Import $fileName, size = $fileSize")
//                if (!fileName.endsWith(Rule.EXTENSION) || fileSize == 0L) {
//                    logger.e("Invalid file, skipping")
//                    continue
//                }
//                val inputStream = applicationContext.contentResolver.openInputStream(folderUri.)
//            } while (it.moveToNext())
//        }
//        FileUtils.listFiles(Rule.getBlockerRuleFolder(context).absolutePath).filter {
//            it.endsWith(Rule.EXTENSION)
//        }.forEach {
//
//        }
        return Result.success()
    }
}