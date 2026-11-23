/*
 * Copyright 2026 Blocker
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
import androidx.core.net.toUri
import androidx.documentfile.provider.DocumentFile
import com.merxury.blocker.core.rule.backup.BackupStorage
import com.merxury.blocker.core.rule.backup.BackupStorageException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import timber.log.Timber

private const val JSON_EXTENSION = ".json"
private const val JSON_MIME = "application/json"

/**
 * [BackupStorage] over the document tree the user picked through the Storage Access Framework.
 * Files are created as earlier versions created them, so a provider names them the same way.
 */
class DocumentTreeBackupStorage private constructor(
    private val context: Context,
    private val root: DocumentFile,
    private val ioDispatcher: CoroutineDispatcher,
) : BackupStorage {

    override suspend fun listFiles(folder: String): List<String> = access("list $folder") {
        find(folder)?.listFiles()?.filter { it.isFile }?.mapNotNull { it.name }.orEmpty()
    }

    override suspend fun listFolders(folder: String): List<String> = access("list $folder") {
        find(folder)?.listFiles()?.filter { it.isDirectory }?.mapNotNull { it.name }.orEmpty()
    }

    override suspend fun read(path: String): String? = access("read $path") {
        val file = find(path)?.takeIf { it.isFile } ?: return@access null
        context.contentResolver.openInputStream(file.uri)?.use { it.bufferedReader().readText() }
            ?: throw BackupStorageException("Cannot open $path")
    }

    override suspend fun write(path: String, content: String) = access("write $path") {
        val folder = path.substringBeforeLast('/', "")
        val name = path.substringAfterLast('/')
        val parent = folderOf(folder)
        val file = parent.findFile(name) ?: createFile(parent, name) ?: throw BackupStorageException("Cannot create $path")
        // "rwt" truncates, so a shorter backup leaves no tail of the old one.
        context.contentResolver.openOutputStream(file.uri, "rwt")?.use { it.write(content.toByteArray()) }
            ?: throw BackupStorageException("Cannot open $path for writing")
    }

    // The provider adds the extension of the MIME type, so a JSON file is created by its base name.
    private fun createFile(parent: DocumentFile, name: String): DocumentFile? = if (name.endsWith(JSON_EXTENSION)) {
        parent.createFile(JSON_MIME, name.removeSuffix(JSON_EXTENSION))
    } else {
        parent.createFile("", name)
    }

    /** The document at [path], or null when some part of it does not exist. */
    private fun find(path: String): DocumentFile? = path.split('/').filter { it.isNotEmpty() }.fold(root as DocumentFile?) { dir, name -> dir?.findFile(name) }

    /** The folder at [path], created with its missing parents. */
    private fun folderOf(path: String): DocumentFile = path.split('/').filter { it.isNotEmpty() }.fold(root) { dir, name ->
        dir.findFile(name)?.takeIf { it.isDirectory } ?: dir.createDirectory(name) ?: throw BackupStorageException("Cannot create the folder $name")
    }

    private suspend fun <T> access(what: String, block: () -> T): T = withContext(ioDispatcher) {
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: BackupStorageException) {
            throw e
        } catch (e: Exception) {
            // A revoked grant shows as SecurityException, a removed volume as other exceptions.
            Timber.e(e, "Cannot $what in the backup folder")
            throw BackupStorageException("Cannot $what", e)
        }
    }

    companion object {
        /**
         * The tree at [treeUri], or null when it cannot be opened or Blocker may no longer read and
         * write it, for example because the grant was revoked.
         */
        fun open(context: Context, treeUri: String, ioDispatcher: CoroutineDispatcher): DocumentTreeBackupStorage? {
            val root = try {
                DocumentFile.fromTreeUri(context, treeUri.toUri())
            } catch (e: Exception) {
                Timber.e(e, "Uri $treeUri is not a document tree")
                null
            }
            if (root == null || !root.canRead() || !root.canWrite()) {
                Timber.w("Uri $treeUri is not reachable")
                return null
            }
            return DocumentTreeBackupStorage(context, root, ioDispatcher)
        }
    }
}
