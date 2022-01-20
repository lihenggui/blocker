package com.merxury.blocker.util

import android.content.Context
import androidx.documentfile.provider.DocumentFile
import com.elvishew.xlog.XLog
import com.google.gson.GsonBuilder
import com.merxury.blocker.rule.Rule
import com.merxury.blocker.rule.entity.BlockerRule
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object StorageUtil {
    val IFW_RELATIVE_PATH = "ifw"
    private val logger = XLog.tag("StorageUtil").build()

    suspend fun saveRuleToStorage(
        context: Context,
        rule: BlockerRule,
        packageName: String,
        dispatcher: CoroutineDispatcher = Dispatchers.IO
    ): Boolean {
        // Get base dir
        val destUri = PreferenceUtil.getSavedRulePath(context)
        if (destUri == null) {
            logger.w("No dest folder defined")
            return false
        }
        val dir = DocumentFile.fromTreeUri(context, destUri)
        if (dir == null) {
            logger.e("Cannot open $destUri")
            return false
        }
        // Create blocker rule file
        var file = dir.findFile(packageName)
        if (file == null) {
            file = dir.createFile(Rule.BLOCKER_RULE_MIME, packageName)
        }
        if (file == null) {
            logger.w("Cannot create rule $packageName")
            return false
        }
        withContext(dispatcher) {
            try {
                context.contentResolver.openOutputStream(file.uri)?.use {
                    val text = GsonBuilder().setPrettyPrinting().create().toJson(rule)
                    it.write(text.toByteArray())
                }
                true
            } catch (e: Exception) {
                logger.e("Cannot write rules for $packageName", e)
                return@withContext false
            }
        }
        // Should be unreachable
        return false
    }

    suspend fun saveIfwToStorage(
        context: Context,
        filename: String,
        content: String,
        dispatcher: CoroutineDispatcher = Dispatchers.IO
    ): Boolean {
        // Get base dir
        val destUri = PreferenceUtil.getSavedRulePath(context)
        if (destUri == null) {
            logger.w("No dest folder defined")
            return false
        }
        val dir = DocumentFile.fromTreeUri(context, destUri)
        if (dir == null) {
            logger.e("Cannot open $destUri")
            return false
        }
        // Find IFW folder
        var ifwDir = dir.findFile(IFW_RELATIVE_PATH)
        if (ifwDir == null) {
            ifwDir = dir.createDirectory(IFW_RELATIVE_PATH)
        }
        if (ifwDir == null) {
            logger.e("Cannot create ifw dir in $destUri")
            return false
        }
        // Create IFW file
        var file = dir.findFile(filename)
        if (file == null) {
            file = dir.createFile("", filename)
        }
        if (file == null) {
            logger.w("Cannot create ifw rule $filename")
            return false
        }
        // Write file contents
        withContext(dispatcher) {
            try {
                context.contentResolver.openOutputStream(file.uri)?.use {
                    it.write(content.toByteArray())
                }
                true
            } catch (e: Exception) {
                logger.e("Cannot write rules for $filename", e)
                return@withContext false
            }
        }
        // Should be unreachable
        return false
    }
}