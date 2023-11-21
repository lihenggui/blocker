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

package com.merxury.blocker.core.domain

import com.merxury.blocker.core.di.CacheDir
import com.merxury.blocker.core.di.FilesDir
import com.merxury.blocker.core.dispatchers.BlockerDispatchers.IO
import com.merxury.blocker.core.dispatchers.Dispatcher
import com.merxury.blocker.core.logging.LOG_DIR
import com.merxury.blocker.core.utils.FileUtils
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.datetime.Clock
import timber.log.Timber
import java.io.File
import javax.inject.Inject

class ZipLogFileUseCase @Inject constructor(
    @CacheDir private val cacheDir: File,
    @FilesDir private val filesDir: File,
    @Dispatcher(IO) private val ioDispatcher: CoroutineDispatcher,
) {
    operator fun invoke(): Flow<File?> = flow {
        val logFolder = filesDir.resolve(LOG_DIR)
        if (!logFolder.exists()) {
            Timber.w("Log folder not exists")
            emit(null)
            return@flow
        }
        val currentTime = Clock.System.now().toString()
            .replace(":", "-")
            .replace(".", "-")
        val file = cacheDir.resolve("Blocker-log-$currentTime.zip")
        val status = FileUtils.zipFolder(logFolder.absolutePath, file.absolutePath)
        if (status) {
            emit(file)
        } else {
            emit(null)
        }
    }
        .flowOn(ioDispatcher)
}
