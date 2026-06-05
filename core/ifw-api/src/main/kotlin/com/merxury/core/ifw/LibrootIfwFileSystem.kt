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

package com.merxury.core.ifw

import com.merxury.blocker.core.dispatchers.BlockerDispatchers.IO
import com.merxury.blocker.core.dispatchers.Dispatcher
import com.merxury.blocker.core.root.RootCommandExecutor
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject

private const val EXTENSION = ".xml"

internal class LibrootIfwFileSystem @Inject constructor(
    private val rootCommandExecutor: RootCommandExecutor,
    @Dispatcher(IO) private val dispatcher: CoroutineDispatcher,
) : IfwFileSystem {

    override suspend fun readRules(packageName: String): String? = withContext(dispatcher) {
        val filename = "$packageName$EXTENSION"
        val path = IfwStorageUtils.ifwFolder + filename
        if (!rootCommandExecutor.fileExists(path)) {
            Timber.v("Rule file $filename does not exist")
            return@withContext null
        }
        return@withContext try {
            rootCommandExecutor.readFile(path)
        } catch (e: Exception) {
            Timber.e(e, "Error reading rules file $path")
            null
        }
    }

    override suspend fun writeRules(packageName: String, content: String) = withContext(dispatcher) {
        val filename = "$packageName$EXTENSION"
        val path = IfwStorageUtils.ifwFolder + filename
        rootCommandExecutor.writeFile(path, content)
        rootCommandExecutor.chmod(path, 644, recursively = false)
        Timber.i("Saved IFW rules to $path")
    }

    override suspend fun deleteRules(packageName: String): Boolean = withContext(dispatcher) {
        val filename = "$packageName$EXTENSION"
        val path = IfwStorageUtils.ifwFolder + filename
        if (rootCommandExecutor.fileExists(path)) {
            Timber.d("Deleting IFW rule file $filename")
            rootCommandExecutor.deleteFile(path, recursively = false)
        } else {
            false
        }
    }

    override suspend fun fileExists(packageName: String): Boolean = withContext(dispatcher) {
        val filename = "$packageName$EXTENSION"
        rootCommandExecutor.fileExists(IfwStorageUtils.ifwFolder + filename)
    }

    override suspend fun listRuleFiles(): List<String> = withContext(dispatcher) {
        rootCommandExecutor.listFiles(IfwStorageUtils.ifwFolder)
            .filter { it.endsWith(EXTENSION) }
            .map { it.removeSuffix(EXTENSION) }
    }
}
