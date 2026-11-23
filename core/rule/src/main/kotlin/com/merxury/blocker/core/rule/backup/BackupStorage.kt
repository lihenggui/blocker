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

import java.io.IOException

/**
 * The folder the user picked for backups, as text files addressed by relative path.
 *
 * A path is a sequence of names joined by `/`, relative to the folder; `""` is the folder itself.
 * Names never contain `/`, and `.` and `..` are not special. Every method throws
 * [BackupStorageException] when the folder or the file cannot be reached, and never for a file or
 * sub-folder that simply does not exist.
 */
interface BackupStorage {
    /** Names of the files directly in [folder]; empty when [folder] does not exist. */
    suspend fun listFiles(folder: String): List<String>

    /** Names of the sub-folders directly in [folder]; empty when [folder] does not exist. */
    suspend fun listFolders(folder: String): List<String>

    /** The text of the file at [path], or null when there is no such file. */
    suspend fun read(path: String): String?

    /** Replaces the file at [path] with [content], creating it and any missing sub-folders. */
    suspend fun write(path: String, content: String)
}

/** The backup folder, or one file in it, could not be read or written. */
class BackupStorageException(message: String, cause: Throwable? = null) : IOException(message, cause)

/** Joins [folder] and [name] into a [BackupStorage] path. */
internal fun pathOf(folder: String, name: String): String = if (folder.isEmpty()) name else "$folder/$name"
