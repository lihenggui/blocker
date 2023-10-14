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
import com.merxury.blocker.core.data.di.GeneratedRuleBaseFolder
import com.merxury.blocker.core.data.respository.userdata.UserDataRepository
import com.merxury.blocker.core.dispatchers.BlockerDispatchers.IO
import com.merxury.blocker.core.dispatchers.Dispatcher
import com.merxury.blocker.core.domain.model.ZippedRule
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
    private val userDataRepository: UserDataRepository,
    @CacheDir private val cacheDir: File,
    @FilesDir private val filesDir: File,
    @GeneratedRuleBaseFolder private val ruleBaseFolder: String,
    @Dispatcher(IO) private val ioDispatcher: CoroutineDispatcher,
) {
    operator fun invoke(): Flow<ZippedRule> = flow {
        val time = Clock.System.now().toString()
            .replace(":", "-")
            .replace(".", "-")
        val fileName = "rules-$time.zip"
        val zipFile = File(cacheDir, fileName)
        val baseFolder = filesDir.resolve(ruleBaseFolder)
            .resolve(userDataRepository.getLibDisplayLanguage())
        if (!baseFolder.exists()) {
            Timber.e("Rule base folder $baseFolder does not exist")
            emit(ZippedRule.EMPTY)
            return@flow
        }
        val files = baseFolder.listFiles()
        if (files.isNullOrEmpty()) {
            Timber.e("Folder $files is empty")
            emit(ZippedRule.EMPTY)
            return@flow
        }
        try {
            FileUtils.zipFolder(baseFolder.absolutePath, zipFile.absolutePath)
            emit(ZippedRule(null, zipFile))
        } catch (e: Exception) {
            Timber.e(e)
            emit(ZippedRule.EMPTY)
        }
    }
        .flowOn(ioDispatcher)
}
