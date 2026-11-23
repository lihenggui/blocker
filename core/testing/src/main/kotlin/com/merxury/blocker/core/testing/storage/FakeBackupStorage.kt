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

package com.merxury.blocker.core.testing.storage

import com.merxury.blocker.core.rule.backup.BackupStorage
import com.merxury.blocker.core.rule.backup.BackupStorageException

/**
 * A backup folder in memory: files by path, with folders implied by the paths, as the
 * [BackupStorage] contract describes. Switches make the folder or single files fail.
 */
class FakeBackupStorage : BackupStorage {
    private val files = sortedMapOf<String, String>()

    /** When true, every operation throws, as for a folder whose grant was revoked. */
    var unavailable = false

    /** Paths whose reads throw. */
    val unreadablePaths = mutableSetOf<String>()

    /** Paths whose writes throw and leave the file as it was. */
    val unwritablePaths = mutableSetOf<String>()

    /** Every path written, in order. */
    val writes = mutableListOf<String>()

    /** Sets a file as another tool, or an earlier version, would have written it. */
    fun putFile(path: String, content: String) {
        files[path] = content
    }

    fun fileContent(path: String): String? = files[path]

    /** Every file by path. */
    val allFiles: Map<String, String> get() = files.toMap()

    override suspend fun listFiles(folder: String): List<String> {
        check()
        return children(folder).filter { '/' !in it.second }.map { it.second }
    }

    override suspend fun listFolders(folder: String): List<String> {
        check()
        return children(folder).filter { '/' in it.second }.map { it.second.substringBefore('/') }.distinct()
    }

    override suspend fun read(path: String): String? {
        check()
        if (path in unreadablePaths) throw BackupStorageException("Cannot read $path")
        return files[path]
    }

    override suspend fun write(path: String, content: String) {
        check()
        writes += path
        if (path in unwritablePaths) throw BackupStorageException("Cannot write $path")
        files[path] = content
    }

    // Each file below [folder] with its path relative to it.
    private fun children(folder: String): List<Pair<String, String>> {
        val prefix = if (folder.isEmpty()) "" else "$folder/"
        return files.keys.filter { it.startsWith(prefix) }.map { it to it.removePrefix(prefix) }
    }

    private fun check() {
        if (unavailable) throw BackupStorageException("The backup folder is not available")
    }
}
