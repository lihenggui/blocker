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

import com.merxury.blocker.core.data.device.ComponentStatusRepository
import com.merxury.blocker.core.data.di.GeneratedRuleBaseFolder
import com.merxury.blocker.core.data.repository.userdata.UserDataRepository
import com.merxury.blocker.core.di.CacheDir
import com.merxury.blocker.core.di.FilesDir
import com.merxury.blocker.core.dispatchers.BlockerDispatchers.IO
import com.merxury.blocker.core.dispatchers.Dispatcher
import com.merxury.blocker.core.domain.model.ZippedRule
import com.merxury.blocker.core.model.component.AppKey
import com.merxury.blocker.core.utils.ZipArchiver
import com.merxury.blocker.core.utils.listFilesRecursively
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.io.IOException
import javax.inject.Inject

/** Archives the generated rules that match components of the selected app and user. */
class ZipAppRuleUseCase
@Inject
constructor(
    private val componentStatusRepository: ComponentStatusRepository,
    private val userDataRepository: UserDataRepository,
    @CacheDir private val cacheDir: File,
    @FilesDir private val filesDir: File,
    @GeneratedRuleBaseFolder private val ruleBaseFolder: String,
    @Dispatcher(IO) private val ioDispatcher: CoroutineDispatcher,
) {
    suspend operator fun invoke(app: AppKey): ZippedRule = withContext(ioDispatcher) {
        val ruleRoot = filesDir.resolve(ruleBaseFolder)
        val baseFolder = ruleRoot.resolve(userDataRepository.getLibDisplayLanguage())
        if (!baseFolder.isDirectory) {
            Timber.e("Rule base folder $baseFolder does not exist")
            return@withContext ZippedRule.EMPTY
        }

        val classNames =
            componentStatusRepository
                .components(app)
                .first()
                .mapTo(mutableSetOf()) { it.declaration.id.className }
        val matchedFiles =
            try {
                baseFolder.listFilesRecursively().filter { file ->
                    ensureActive()
                    file.extension == "json" &&
                        file
                            .relativeTo(baseFolder)
                            .path
                            .removeSuffix(".json")
                            .replace(File.separatorChar, '.') in classNames
                }
            } catch (e: IOException) {
                Timber.e(e, "Failed to read generated rules in $baseFolder")
                return@withContext ZippedRule.EMPTY
            }
        if (matchedFiles.isEmpty()) {
            Timber.i("No matched generated rules found for ${app.packageName}")
            return@withContext ZippedRule.EMPTY
        }

        exportZipArchive(cacheDir, "app-rules-${app.packageName}-") { target ->
            ZipArchiver.zipFiles(ruleRoot, matchedFiles, target, ioDispatcher)
        }.fold(
            onSuccess = { ZippedRule(app.packageName, it) },
            onFailure = {
                Timber.e(it, "Failed to zip rules for ${app.packageName}")
                ZippedRule.EMPTY
            },
        )
    }
}
