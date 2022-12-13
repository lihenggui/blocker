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

package com.merxury.blocker.ui.detail.appinfo

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.elvishew.xlog.XLog
import com.google.gson.Gson
import com.merxury.blocker.core.ComponentControllerProxy
import com.merxury.blocker.core.PreferenceUtil
import com.merxury.blocker.core.model.data.ControllerType
import com.merxury.blocker.core.rule.Rule
import com.merxury.blocker.core.rule.entity.BlockerRule
import com.merxury.blocker.core.rule.util.StorageUtil
import com.merxury.blocker.core.utils.FileUtils
import com.merxury.ifw.util.RuleSerializer
import com.merxury.ifw.util.StorageUtils
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object RuleBackupHelper {
    private val logger = XLog.tag("RuleBackupHelper")

    @Throws(Exception::class)
    suspend fun import(
        context: Context,
        packageName: String,
        dispatcher: CoroutineDispatcher = Dispatchers.IO
    ): Uri? {
        return withContext(dispatcher) {
            val controllerType = PreferenceUtil.getControllerType(context)
            val savedPath = PreferenceUtil.getSavedRulePath(context) ?: return@withContext null
            val backupName = packageName + Rule.EXTENSION
            val folder = DocumentFile.fromTreeUri(context, savedPath) ?: return@withContext null
            val backupFile = folder.findFile(backupName) ?: run {
                logger.e("Backup file $backupName not found in folder ${folder.uri}")
                return@withContext null
            }
            context.contentResolver.openInputStream(backupFile.uri).use {
                val reader = BufferedReader(InputStreamReader(it))
                val blockerRule = Gson().fromJson(
                    reader,
                    BlockerRule::class.java
                )
                Rule.import(context, blockerRule, controllerType)
                logger.i(
                    "Import rule ${blockerRule.packageName} " +
                        "from ${backupFile.uri.path} successfully"
                )
            }
            return@withContext backupFile.uri
        }
    }

    @Throws(Exception::class)
    suspend fun export(
        context: Context,
        packageName: String,
        dispatcher: CoroutineDispatcher = Dispatchers.IO
    ): Boolean {
        val savedPath = PreferenceUtil.getSavedRulePath(context) ?: return false
        return withContext(dispatcher) {
            val result = Rule.export(context, packageName, savedPath)
            if (result) {
                logger.i("Export rule $packageName successfully")
            } else {
                logger.e("Export rule $packageName failed")
            }
            return@withContext result
        }
    }

    @Throws(Exception::class)
    suspend fun importIfwRule(
        context: Context,
        packageName: String,
        dispatcher: CoroutineDispatcher = Dispatchers.IO
    ): Uri? {
        return withContext(dispatcher) {
            val baseUri = PreferenceUtil.getSavedRulePath(context) ?: return@withContext null
            val baseFolder = DocumentFile.fromTreeUri(context, baseUri) ?: return@withContext null
            val ifwFolder = baseFolder.findFile("ifw")
            val fileName = packageName + Rule.IFW_EXTENSION
            // Find the file in ifw folder
            var backupFile = ifwFolder?.findFile(fileName)
            if (backupFile == null) {
                logger.w("Backup file $fileName not found in folder ${ifwFolder?.uri}")
            }
            // Didn't find rules in ifw folder, try to find rules in root folder
            if (backupFile == null) {
                backupFile = baseFolder.findFile(packageName + Rule.IFW_EXTENSION)
            }
            if (backupFile == null) {
                logger.e("Backup file $fileName not found in folder ${baseFolder.uri}")
                return@withContext null
            }
            val controller = ComponentControllerProxy.getInstance(ControllerType.IFW, context)
            context.contentResolver.openInputStream(backupFile.uri)?.use { stream ->
                val rule = RuleSerializer.deserialize(stream) ?: return@use
                Rule.updateIfwState(rule, controller)
                logger.i("Import ifw rule ${backupFile.uri} success")
            }
            return@withContext backupFile.uri
        }
    }

    @Throws(Exception::class)
    suspend fun exportIfwRule(
        context: Context,
        packageName: String,
        dispatcher: CoroutineDispatcher = Dispatchers.IO
    ): String? {
        return withContext(dispatcher) {
            val ifwFolder = StorageUtils.getIfwFolder()
            val files = FileUtils.listFiles(ifwFolder)
            val ifwFile = files.filter { it.contains(packageName) }
            if (ifwFile.isEmpty()) {
                logger.e("Can't find file IFW rule in $ifwFolder, package = $packageName")
                return@withContext null
            }
            ifwFile.forEach {
                logger.i("Export $it")
                val filename = it.split(File.separator).last()
                val content = FileUtils.read(ifwFolder + it)
                val result = StorageUtil.saveIfwToStorage(context, filename, content)
                if (!result) {
                    logger.i("Export $it failed")
                    return@withContext null
                }
            }
            return@withContext PreferenceUtil.getIfwRulePath(context)?.path +
                File.separator + ifwFile.firstOrNull()
        }
    }
}
