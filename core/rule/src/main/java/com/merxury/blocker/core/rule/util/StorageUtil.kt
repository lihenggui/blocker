/*
 * Copyright 2023 Blocker
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

package com.merxury.blocker.core.rule.util

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.merxury.blocker.core.rule.Rule
import com.merxury.blocker.core.rule.entity.BlockerRule
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import timber.log.Timber
import java.io.IOException

object StorageUtil {
    private const val IFW_RELATIVE_PATH = "ifw"

    fun getOrCreateIfwFolder(context: Context, baseFolder: String): DocumentFile? {
        val baseDocument = DocumentFile.fromTreeUri(context, Uri.parse(baseFolder))
        if (baseDocument == null) {
            Timber.e("Can't parse the path of the base folder.")
            return null
        }
        val ifwFolder = baseDocument.findFile(IFW_RELATIVE_PATH) ?: run {
            baseDocument.createDirectory(IFW_RELATIVE_PATH) ?: run {
                Timber.e("Create ifw folder failed")
                return null
            }
        }
        return ifwFolder
    }

    fun isFolderReadable(context: Context, path: String): Boolean {
        val uri = Uri.parse(path)
        // Hasn't set the dir to store
        val folder = try {
            DocumentFile.fromTreeUri(context, uri)
        } catch (e: Exception) {
            Timber.e(e, "Uri $uri is not readable.")
            return false
        }
        // Folder may be unreachable
        val isFolderUnreachable = (folder == null) || !folder.canRead() || !folder.canWrite()
        if (isFolderUnreachable) {
            Timber.w("Uri $uri is not reachable.")
        }
        return !isFolderUnreachable
    }
    suspend fun saveIfwToStorage(
        context: Context,
        baseFolder: String,
        filename: String,
        content: String,
        dispatcher: CoroutineDispatcher = Dispatchers.IO,
    ): Boolean = withContext(dispatcher) {
        // Get base dir
        val destUri = Uri.parse(baseFolder)
        if (destUri == null) {
            Timber.w("No dest folder defined")
            return@withContext false
        }
        val dir = DocumentFile.fromTreeUri(context, destUri)
        if (dir == null) {
            Timber.e("Cannot open $destUri")
            return@withContext false
        }
        // Find IFW folder
        var ifwDir = dir.findFile(IFW_RELATIVE_PATH)
        if (ifwDir == null) {
            ifwDir = dir.createDirectory(IFW_RELATIVE_PATH)
        }
        if (ifwDir == null) {
            Timber.e("Cannot create ifw dir in $destUri")
            return@withContext false
        }
        // Create IFW file
        var file = ifwDir.findFile(filename)
        if (file == null) {
            file = ifwDir.createFile("", filename)
        }
        if (file == null) {
            Timber.w("Cannot create ifw rule $filename")
            return@withContext false
        }
        // Write file contents
        return@withContext try {
            context.contentResolver.openOutputStream(file.uri, "rwt")?.use {
                it.write(content.toByteArray())
            }
            true
        } catch (e: IOException) {
            Timber.e(e, "Cannot write rules for $filename")
            false
        }
    }

    suspend fun saveRuleToStorage(
        context: Context,
        rule: BlockerRule,
        packageName: String,
        destUri: Uri,
        dispatcher: CoroutineDispatcher = Dispatchers.IO,
    ): Boolean {
        val dir = DocumentFile.fromTreeUri(context, destUri)
        if (dir == null) {
            Timber.e("Cannot open $destUri")
            return false
        }
        // Create blocker rule file
        var file = dir.findFile(packageName + Rule.EXTENSION)
        if (file == null) {
            file = dir.createFile(Rule.BLOCKER_RULE_MIME, packageName)
        }
        if (file == null) {
            Timber.w("Cannot create rule $packageName")
            return false
        }
        return withContext(dispatcher) {
            try {
                context.contentResolver.openOutputStream(file.uri, "rwt")?.use {
                    val text = Json.encodeToString(rule)
                    it.write(text.toByteArray())
                }
                return@withContext true
            } catch (e: Exception) {
                Timber.e(e, "Cannot write rules for $packageName")
                return@withContext false
            }
        }
    }
}
