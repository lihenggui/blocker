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
    const val IFW_RELATIVE_PATH = "ifw"
    private val logger = XLog.tag("StorageUtil").build()

    fun getSavedFolder(context: Context): DocumentFile? {
        val savedUri = PreferenceUtil.getSavedRulePath(context) ?: run {
            logger.e("Saved rule path is null")
            return null
        }
        return DocumentFile.fromTreeUri(context, savedUri)
    }

    fun getOrCreateIfwFolder(context: Context): DocumentFile? {
        val savedFolder = getSavedFolder(context) ?: return null
        val ifwFolder = savedFolder.findFile(IFW_RELATIVE_PATH) ?: run {
            savedFolder.createDirectory(IFW_RELATIVE_PATH) ?: run {
                logger.e("Create ifw folder failed")
                return null
            }
        }
        return ifwFolder
    }

    fun isSavedFolderReadable(context: Context): Boolean {
        val uri = PreferenceUtil.getSavedRulePath(context) ?: return false
        // Hasn't set the dir to store
        val folder = try {
            DocumentFile.fromTreeUri(context, uri)
        } catch (e: Exception) {
            logger.e("Invalid Uri $uri", e)
            return false
        }
        // Folder may be unreachable
        val isFolderUnreachable = (folder == null) || !folder.canRead() || !folder.canWrite()
        return !isFolderUnreachable
    }

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
        var file = dir.findFile(packageName + Rule.EXTENSION)
        if (file == null) {
            file = dir.createFile(Rule.BLOCKER_RULE_MIME, packageName)
        }
        if (file == null) {
            logger.w("Cannot create rule $packageName")
            return false
        }
        return withContext(dispatcher) {
            try {
                context.contentResolver.openOutputStream(file.uri, "rwt")?.use {
                    val text = GsonBuilder().setPrettyPrinting().create().toJson(rule)
                    it.write(text.toByteArray())
                }
                return@withContext true
            } catch (e: Exception) {
                logger.e("Cannot write rules for $packageName", e)
                return@withContext false
            }
        }
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
        var file = ifwDir.findFile(filename)
        if (file == null) {
            file = ifwDir.createFile("", filename)
        }
        if (file == null) {
            logger.w("Cannot create ifw rule $filename")
            return false
        }
        // Write file contents
        return withContext(dispatcher) {
            try {
                context.contentResolver.openOutputStream(file.uri, "rwt")?.use {
                    it.write(content.toByteArray())
                }
                return@withContext true
            } catch (e: Exception) {
                logger.e("Cannot write rules for $filename", e)
                return@withContext false
            }
        }
    }
}
