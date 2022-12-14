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

package com.merxury.blocker.core.rule.util

import android.content.Context
import androidx.documentfile.provider.DocumentFile
import com.merxury.blocker.core.PreferenceUtil
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber

object StorageUtil {
    private const val IFW_RELATIVE_PATH = "ifw"

    fun getSavedFolder(context: Context): DocumentFile? {
        val savedUri = PreferenceUtil.getSavedRulePath(context) ?: run {
            Timber.e("Saved rule path is null")
            return null
        }
        return DocumentFile.fromTreeUri(context, savedUri)
    }

    fun getOrCreateIfwFolder(context: Context): DocumentFile? {
        val savedFolder = getSavedFolder(context) ?: return null
        val ifwFolder = savedFolder.findFile(IFW_RELATIVE_PATH) ?: run {
            savedFolder.createDirectory(IFW_RELATIVE_PATH) ?: run {
                Timber.e("Create ifw folder failed")
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
            Timber.e("Invalid Uri $uri", e)
            return false
        }
        // Folder may be unreachable
        val isFolderUnreachable = (folder == null) || !folder.canRead() || !folder.canWrite()
        return !isFolderUnreachable
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
            Timber.w("No dest folder defined")
            return false
        }
        val dir = DocumentFile.fromTreeUri(context, destUri)
        if (dir == null) {
            Timber.e("Cannot open $destUri")
            return false
        }
        // Find IFW folder
        var ifwDir = dir.findFile(IFW_RELATIVE_PATH)
        if (ifwDir == null) {
            ifwDir = dir.createDirectory(IFW_RELATIVE_PATH)
        }
        if (ifwDir == null) {
            Timber.e("Cannot create ifw dir in $destUri")
            return false
        }
        // Create IFW file
        var file = ifwDir.findFile(filename)
        if (file == null) {
            file = ifwDir.createFile("", filename)
        }
        if (file == null) {
            Timber.w("Cannot create ifw rule $filename")
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
                Timber.e("Cannot write rules for $filename", e)
                return@withContext false
            }
        }
    }
}
