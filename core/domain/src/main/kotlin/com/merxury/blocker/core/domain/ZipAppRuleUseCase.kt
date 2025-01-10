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
import com.merxury.blocker.core.data.respository.component.ComponentRepository
import com.merxury.blocker.core.data.respository.userdata.UserDataRepository
import com.merxury.blocker.core.di.CacheDir
import com.merxury.blocker.core.di.FilesDir
import com.merxury.blocker.core.dispatchers.BlockerDispatchers.IO
import com.merxury.blocker.core.dispatchers.Dispatcher
import com.merxury.blocker.core.domain.model.ZippedRule
import com.merxury.blocker.core.utils.listFilesRecursively
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.datetime.Clock
import timber.log.Timber
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import javax.inject.Inject

class ZipAppRuleUseCase @Inject constructor(
    private val componentRepository: ComponentRepository,
    private val userDataRepository: UserDataRepository,
    @CacheDir private val cacheDir: File,
    @FilesDir private val filesDir: File,
    @GeneratedRuleBaseFolder private val ruleBaseFolder: String,
    @Dispatcher(IO) private val ioDispatcher: CoroutineDispatcher,
) {
    operator fun invoke(packageName: String): Flow<ZippedRule> = flow {
        val time = Clock.System.now().toString()
            .replace(":", "-")
            .replace(".", "-")
        val fileName = "rules-$packageName-$time.zip"
        val zipFile = File(cacheDir, fileName)
        val baseFolder = filesDir.resolve(ruleBaseFolder)
            .resolve(userDataRepository.getLibDisplayLanguage())
        if (!baseFolder.exists()) {
            Timber.e("Rule base folder $baseFolder does not exist")
            emit(ZippedRule.EMPTY)
            return@flow
        }
        val componentList = componentRepository.getComponentList(packageName)
            .first()
        val matchedFile = mutableListOf<File>()
        baseFolder.listFilesRecursively().forEach { file ->
            if (file.isDirectory) {
                return@forEach
            }
            if (file.extension != "json") {
                return@forEach
            }
            val componentName = file.relativeTo(baseFolder).path
                .replace(".json", "")
                .replace("/", ".")
            if (componentList.find { it.name == componentName } == null) {
                Timber.v("Component $componentName does not exist in component list")
                return@forEach
            }
            // Put json file to the zip file
            matchedFile.add(file)
        }
        if (matchedFile.isEmpty()) {
            Timber.i("No matched generated rules found")
            emit(ZippedRule.EMPTY)
        } else {
            Timber.v("Found ${matchedFile.size} matched generated rules")
            updateZipPackage(zipFile, matchedFile)
            emit(ZippedRule(packageName, zipFile))
        }
    }
        .flowOn(ioDispatcher)

    private fun updateZipPackage(zipFile: File, files: List<File>) {
        if (files.isEmpty()) {
            Timber.w("Files is empty, skip update zip package")
            return
        }
        if (zipFile.exists()) {
            Timber.i("Zip file $zipFile exists, delete it")
            zipFile.delete()
        }
        Timber.i("Start to update zip package")
        val baseFolder = filesDir.resolve(ruleBaseFolder)
        val zipOutputStream = zipFile.outputStream()
            .buffered()
            .let { ZipOutputStream(it) }
        // Put files into the zip file, and keep the original folder structure
        zipOutputStream.use { outputStream ->
            files.forEach { file ->
                val relativePath = file.relativeTo(baseFolder)
                val zipEntry = ZipEntry(relativePath.path)
                outputStream.putNextEntry(zipEntry)
                file.inputStream().use { inputStream ->
                    inputStream.copyTo(outputStream)
                }
                outputStream.closeEntry()
            }
        }
    }
}
