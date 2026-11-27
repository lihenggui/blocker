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

import com.merxury.blocker.core.di.CacheDir
import com.merxury.blocker.core.di.FilesDir
import com.merxury.blocker.core.dispatchers.BlockerDispatchers.IO
import com.merxury.blocker.core.dispatchers.Dispatcher
import com.merxury.blocker.core.logging.LOG_DIR
import com.merxury.blocker.core.logging.LogFileLock
import com.merxury.blocker.core.utils.ZipArchiver
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import javax.inject.Inject

class ZipLogFileUseCase
@Inject
constructor(
    @CacheDir private val cacheDir: File,
    @FilesDir private val filesDir: File,
    @Dispatcher(IO) private val ioDispatcher: CoroutineDispatcher,
) {
    suspend operator fun invoke(): File? = withContext(ioDispatcher) {
        val logFolder = filesDir.resolve(LOG_DIR)
        if (!logFolder.isDirectory) {
            Timber.w("Log folder does not exist: $logFolder")
            return@withContext null
        }
        exportZipArchive(cacheDir, "Blocker-log-") { target ->
            LogFileLock.mutex.withLock {
                ZipArchiver.zipDirectory(logFolder, target, ioDispatcher)
            }
        }.getOrElse {
            Timber.e(it, "Failed to zip logs in $logFolder")
            null
        }
    }
}
