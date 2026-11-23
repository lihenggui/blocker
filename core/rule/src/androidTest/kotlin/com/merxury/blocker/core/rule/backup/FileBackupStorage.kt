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

package com.merxury.blocker.core.rule.backup

import java.io.File
import java.io.IOException

/** [BackupStorage] over a plain directory, for device tests that cannot drive the folder picker. */
internal class FileBackupStorage(private val root: File) : BackupStorage {

    override suspend fun listFiles(folder: String): List<String> = File(root, folder).listFiles()?.filter { it.isFile }?.map { it.name }.orEmpty()

    override suspend fun listFolders(folder: String): List<String> = File(root, folder).listFiles()?.filter { it.isDirectory }?.map { it.name }.orEmpty()

    override suspend fun read(path: String): String? = File(root, path).takeIf { it.isFile }?.readText()

    override suspend fun write(path: String, content: String) {
        val file = File(root, path)
        try {
            file.parentFile?.mkdirs()
            file.writeText(content)
        } catch (e: IOException) {
            throw BackupStorageException("Cannot write $path", e)
        }
    }

    /** Every file's path relative to the folder. */
    fun allPaths(): List<String> = root.walkTopDown().filter { it.isFile }.map { it.relativeTo(root).path }.sorted().toList()
}
