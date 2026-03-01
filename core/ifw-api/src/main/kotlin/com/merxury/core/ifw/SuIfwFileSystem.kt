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
import com.merxury.blocker.core.utils.FileUtils
import com.topjohnwu.superuser.io.SuFile
import com.topjohnwu.superuser.io.SuFileInputStream
import com.topjohnwu.superuser.io.SuFileOutputStream
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject

private const val EXTENSION = ".xml"

internal class SuIfwFileSystem @Inject constructor(
    @Dispatcher(IO) private val dispatcher: CoroutineDispatcher,
) : IfwFileSystem {

    override suspend fun readRules(packageName: String): String? = withContext(dispatcher) {
        val filename = "$packageName$EXTENSION"
        val destFile = SuFile(IfwStorageUtils.ifwFolder + filename)
        if (!destFile.exists()) {
            Timber.v("Rule file $filename does not exist")
            return@withContext null
        }
        return@withContext try {
            val input = SuFileInputStream.open(destFile)
            input.readBytes().toString(Charsets.UTF_8)
        } catch (e: Exception) {
            Timber.e(e, "Error reading rules file $destFile")
            null
        }
    }

    override suspend fun writeRules(packageName: String, content: String) = withContext(dispatcher) {
        val filename = "$packageName$EXTENSION"
        val destFile = SuFile(IfwStorageUtils.ifwFolder + filename)
        SuFileOutputStream.open(destFile).use {
            it.write(content.toByteArray(Charsets.UTF_8))
        }
        FileUtils.chmod(destFile.absolutePath, 644, false)
        Timber.i("Saved IFW rules to $destFile")
    }

    override suspend fun deleteRules(packageName: String): Boolean = withContext(dispatcher) {
        val filename = "$packageName$EXTENSION"
        val destFile = SuFile(IfwStorageUtils.ifwFolder + filename)
        if (destFile.exists()) {
            Timber.d("Deleting IFW rule file $filename")
            destFile.delete()
        } else {
            false
        }
    }

    override suspend fun fileExists(packageName: String): Boolean = withContext(dispatcher) {
        val filename = "$packageName$EXTENSION"
        val destFile = SuFile(IfwStorageUtils.ifwFolder + filename)
        destFile.exists()
    }
}
