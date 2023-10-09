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

import com.merxury.blocker.core.data.di.CacheDir
import com.merxury.blocker.core.data.di.FilesDir
import com.merxury.blocker.core.data.di.RuleBaseFolder
import com.merxury.blocker.core.dispatchers.BlockerDispatchers.IO
import com.merxury.blocker.core.dispatchers.Dispatcher
import com.merxury.blocker.core.utils.FileUtils
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.datetime.Clock
import timber.log.Timber
import java.io.File
import javax.inject.Inject

class ZipAllRuleUseCase @Inject constructor(
    @CacheDir private val cacheDir: File,
    @FilesDir private val filesDir: File,
    @RuleBaseFolder private val ruleBaseFolder: String,
    @Dispatcher(IO) private val ioDispatcher: CoroutineDispatcher,
) {
    operator fun invoke(): Flow<File?> = flow {
        val time = Clock.System.now().toString()
            .replace(":", "_")
            .replace(".", "_")
        val fileName = "rules_$time.zip"
        val zipFile = File(cacheDir, fileName)
        val baseFolder = filesDir.resolve(ruleBaseFolder)
        if (!baseFolder.exists()) {
            Timber.e("Rule base folder $baseFolder does not exist")
            emit(null)
            return@flow
        }
        val files = baseFolder.listFiles()
        if (files.isNullOrEmpty()) {
            Timber.e("Folder $files is empty")
            emit(null)
            return@flow
        }
        try {
            FileUtils.zipFileAtPath(baseFolder.absolutePath, zipFile.absolutePath)
            emit(zipFile)
        } catch (e: Exception) {
            Timber.e(e)
            emit(null)
        }
    }
        .flowOn(ioDispatcher)
}
