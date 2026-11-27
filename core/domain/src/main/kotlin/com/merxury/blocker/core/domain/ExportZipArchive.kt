/*
 * Copyright 2025 Blocker
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

package com.merxury.blocker.core.domain

import com.merxury.blocker.core.utils.ZipArchiver
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import kotlin.time.Clock

private val exportMutex = Mutex()

internal suspend fun exportZipArchive(
    cacheDir: File,
    prefix: String,
    writeArchive: suspend (File) -> Result<File>,
): Result<File> = exportMutex.withLock {
    val time = Clock.System.now().toString().replace(":", "-").replace(".", "-")
    val target = cacheDir.resolve("$prefix$time.zip")
    writeArchive(target).onSuccess {
        ZipArchiver.pruneMatchingFiles(cacheDir, prefix, suffix = ".zip", keep = it)
    }
}
