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

import com.merxury.blocker.core.data.di.GeneratedRuleBaseFolder
import com.merxury.blocker.core.data.repository.userdata.UserDataRepository
import com.merxury.blocker.core.di.CacheDir
import com.merxury.blocker.core.di.FilesDir
import com.merxury.blocker.core.dispatchers.BlockerDispatchers.IO
import com.merxury.blocker.core.dispatchers.Dispatcher
import com.merxury.blocker.core.domain.model.ZippedRule
import com.merxury.blocker.core.utils.ZipArchiver
import com.merxury.blocker.core.utils.listFilesRecursively
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import javax.inject.Inject

class ZipAllRuleUseCase
@Inject
constructor(
    private val userDataRepository: UserDataRepository,
    @CacheDir private val cacheDir: File,
    @FilesDir private val filesDir: File,
    @GeneratedRuleBaseFolder private val ruleBaseFolder: String,
    @Dispatcher(IO) private val ioDispatcher: CoroutineDispatcher,
) {
    suspend operator fun invoke(): ZippedRule = withContext(ioDispatcher) {
        val ruleRoot = filesDir.resolve(ruleBaseFolder)
        val baseFolder = ruleRoot.resolve(userDataRepository.getLibDisplayLanguage())
        if (!baseFolder.isDirectory) {
            Timber.e("Rule base folder $baseFolder does not exist")
            return@withContext ZippedRule.EMPTY
        }

        try {
            val files = baseFolder.listFilesRecursively().filter { it.extension == "json" }
            if (files.isEmpty()) {
                Timber.e("Folder $baseFolder is empty")
                return@withContext ZippedRule.EMPTY
            }
            val zipFile = exportZipArchive(cacheDir, "all-rules-") { target ->
                ZipArchiver.zipFiles(ruleRoot, files, target, ioDispatcher)
            }.getOrThrow()
            ZippedRule(null, zipFile)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.e(e, "Failed to zip rules in $baseFolder")
            ZippedRule.EMPTY
        }
    }
}
