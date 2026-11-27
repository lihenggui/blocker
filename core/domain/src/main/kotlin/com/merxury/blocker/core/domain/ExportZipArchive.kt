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
import kotlin.time.Duration.Companion.hours

private val archiveRetention = 24.hours
private val exportMutex = Mutex()

internal suspend fun exportZipArchive(
    cacheDir: File,
    prefix: String,
    writeArchive: suspend (File) -> Result<File>,
): Result<File> = exportMutex.withLock {
    val time = Clock.System.now().toString().replace(":", "-").replace(".", "-")
    var target = cacheDir.resolve("$prefix$time.zip")
    var suffix = 0
    // A repeated timestamp must not replace a file already shared with another app.
    while (target.exists()) {
        suffix++
        target = cacheDir.resolve("$prefix$time-$suffix.zip")
    }
    writeArchive(target).onSuccess {
        ZipArchiver.pruneMatchingFiles(
            directory = cacheDir,
            prefix = prefix,
            suffix = ".zip",
            olderThanEpochMillis = (Clock.System.now() - archiveRetention).toEpochMilliseconds(),
            keep = it,
        )
    }
}
