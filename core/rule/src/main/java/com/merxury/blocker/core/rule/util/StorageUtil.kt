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
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.io.IOException

object StorageUtil {
    private const val IFW_RELATIVE_PATH = "ifw"

    fun getOrCreateIfwFolder(baseFolder: File): DocumentFile? {
        val baseDocument = DocumentFile.fromFile(baseFolder)
        val ifwFolder = baseDocument.findFile(IFW_RELATIVE_PATH) ?: run {
            baseDocument.createDirectory(IFW_RELATIVE_PATH) ?: run {
                Timber.e("Create ifw folder failed")
                return null
            }
        }
        return ifwFolder
    }

    fun isFolderReadable(path: File): Boolean {
        val folder = DocumentFile.fromFile(path)
        // Folder may be unreachable
        val isFolderUnreachable = !folder.canRead() || !folder.canWrite()
        if (isFolderUnreachable) {
            Timber.w("Path $path is not reachable.")
        }
        return !isFolderUnreachable
    }

    suspend fun saveIfwToStorage(
        context: Context,
        baseFolder: File,
        filename: String,
        content: String,
        dispatcher: CoroutineDispatcher = Dispatchers.IO,
    ): Boolean = withContext(dispatcher) {
        // Get base dir
        val dir = DocumentFile.fromFile(baseFolder)
        // Find IFW folder
        var ifwDir = dir.findFile(IFW_RELATIVE_PATH)
        if (ifwDir == null) {
            ifwDir = dir.createDirectory(IFW_RELATIVE_PATH)
        }
        if (ifwDir == null) {
            Timber.e("Cannot create ifw dir in $baseFolder")
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
}
