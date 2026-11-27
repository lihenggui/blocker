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

package com.merxury.blocker.core.utils

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

object ZipArchiver {
    private val writeMutex = Mutex()

    suspend fun zipFile(
        sourceFile: File,
        targetZip: File,
        dispatcher: CoroutineDispatcher = Dispatchers.IO,
    ): Result<File> = zipFiles(sourceFile.absoluteFile.parentFile ?: sourceFile, listOf(sourceFile), targetZip, dispatcher)

    suspend fun zipDirectory(
        sourceDir: File,
        targetZip: File,
        dispatcher: CoroutineDispatcher = Dispatchers.IO,
    ): Result<File> = createZip(sourceDir, targetZip, dispatcher) {
        require(!targetZip.absoluteFile.normalize().canonicalFile.startsWith(sourceDir.canonicalFile)) {
            "ZIP target is inside the source directory: $targetZip"
        }
        sourceDir.listFilesRecursively()
    }

    suspend fun zipFiles(
        baseDir: File,
        files: List<File>,
        targetZip: File,
        dispatcher: CoroutineDispatcher = Dispatchers.IO,
    ): Result<File> = createZip(baseDir, targetZip, dispatcher) { files }

    fun pruneMatchingFiles(
        directory: File,
        prefix: String,
        suffix: String,
        olderThanEpochMillis: Long,
        keep: File? = null,
    ) {
        val keepPath = keep?.absoluteFile?.normalize()
        for (file in directory.listFiles().orEmpty()) {
            if (file.isFile && file.name.startsWith(prefix) && file.name.endsWith(suffix) &&
                file.lastModified() < olderThanEpochMillis &&
                file.absoluteFile.normalize() != keepPath && !file.delete() && file.exists()
            ) {
                Timber.w("Cannot delete old archive %s", file)
            }
        }
    }

    private suspend fun createZip(
        baseDir: File,
        targetZip: File,
        dispatcher: CoroutineDispatcher,
        sourceFiles: suspend () -> List<File>,
    ): Result<File> = withContext(dispatcher) {
        try {
            writeMutex.withLock {
                require(baseDir.isDirectory) { "ZIP base is not a directory: $baseDir" }
                val target = targetZip.absoluteFile.normalize()
                val parent = requireNotNull(target.parentFile) { "ZIP target has no parent: $target" }
                val temp = parent.resolve("${target.name}.tmp")
                val base = baseDir.absoluteFile.normalize()
                val canonicalBase = baseDir.canonicalFile
                val canonicalTarget = target.canonicalFile
                val canonicalTemp = temp.canonicalFile
                val entries =
                    sourceFiles().map { source ->
                        ensureActive()
                        val canonicalSource = source.canonicalFile
                        require(canonicalSource.startsWith(canonicalBase)) {
                            "ZIP source is outside the base directory: $source"
                        }
                        require(canonicalSource.isFile) { "ZIP source is not a file: $source" }
                        require(canonicalSource != canonicalTarget && canonicalSource != canonicalTemp) {
                            "ZIP source is also the target or temporary file: $source"
                        }
                        entryName(base, source) to canonicalSource
                    }

                if (!parent.mkdirs() && !parent.isDirectory) {
                    throw IOException("Cannot create ZIP target directory: $parent")
                }
                writeZip(entries, temp, target)
                Result.success(targetZip)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun entryName(
        base: File,
        file: File,
    ): String {
        val path = file.absoluteFile.normalize()
        require(path.startsWith(base)) { "ZIP source is outside the base directory: $file" }
        val name = path.relativeTo(base).invariantSeparatorsPath
        require('\\' !in name) { "ZIP source has an invalid path separator: $file" }
        return name
    }

    private suspend fun writeZip(
        entries: List<Pair<String, File>>,
        temp: File,
        target: File,
    ) {
        val context = currentCoroutineContext()
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)

        // Remove a leftover temporary file from an interrupted process without following links.
        if (temp.isDirectory && !temp.isSymbolicLink()) {
            throw IOException("ZIP temporary path is a directory: $temp")
        }
        // createNewFile fails if an entry remains after deletion.
        temp.delete()
        if (!temp.createNewFile()) {
            throw IOException("Cannot create temporary ZIP: $temp")
        }
        try {
            ZipOutputStream(FileOutputStream(temp).buffered()).use { output ->
                for ((name, source) in entries) {
                    context.ensureActive()
                    output.putNextEntry(ZipEntry(name).apply { time = source.lastModified() })
                    source.inputStream().use { input ->
                        while (true) {
                            context.ensureActive()
                            val count = input.read(buffer)
                            if (count == -1) break
                            output.write(buffer, 0, count)
                        }
                    }
                    output.closeEntry()
                }
            }
            context.ensureActive()
            if (!temp.renameTo(target)) {
                throw IOException("Cannot replace ZIP target: $target")
            }
        } finally {
            if (!temp.delete() && temp.exists()) {
                Timber.w("Cannot delete temporary ZIP %s", temp)
            }
        }
    }
}
