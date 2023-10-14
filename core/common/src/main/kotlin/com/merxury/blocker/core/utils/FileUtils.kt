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

package com.merxury.blocker.core.utils

import com.merxury.blocker.core.extension.exec
import com.topjohnwu.superuser.io.SuFile
import com.topjohnwu.superuser.io.SuFileInputStream
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

object FileUtils {

    @JvmStatic
    fun copy(source: String, dest: String): File {
        val sourceFile = SuFile(source)
        val destFile = SuFile(dest)
        return sourceFile.copyTo(destFile)
    }

    @JvmStatic
    suspend fun cat(source: String, dest: String) {
        "cat '$source' > '$dest'".exec()
    }

    @JvmStatic
    fun listFiles(path: String): List<String> {
        val file = SuFile(path)
        if (!file.exists()) {
            Timber.w("File $path not exists")
            return ArrayList()
        }
        return file.list()?.toList() ?: ArrayList()
    }

    @JvmStatic
    suspend fun chmod(path: String, permission: Int, recursively: Boolean) {
        val comm = when (recursively) {
            true -> "chmod $permission '$path'"
            false -> "chmod -R $permission '$path'"
        }
        comm.exec()
    }

    @JvmStatic
    fun read(path: String): String {
        SuFileInputStream.open(path).use {
            return it.readBytes().toString(Charsets.UTF_8)
        }
    }

    @JvmStatic
    @Throws(IOException::class)
    suspend fun delete(
        path: String,
        recursively: Boolean,
        dispatcher: CoroutineDispatcher = Dispatchers.IO,
    ): Boolean = withContext(dispatcher) {
        val file = SuFile(path)
        if (!file.exists()) {
            Timber.e("Can't delete $path since it doesn't exist")
            return@withContext false
        }
        return@withContext if (recursively) {
            file.deleteRecursive()
        } else {
            file.delete()
        }
    }

    fun getFileSize(path: String): Long {
        val file = SuFile(path)
        return if (file.exists()) {
            file.length()
        } else {
            0L
        }
    }

    @Throws(IOException::class)
    fun unzip(zipFilePath: File, destDirectory: String) {
        File(destDirectory).run {
            if (!exists()) {
                mkdirs()
            }
        }
        ZipFile(zipFilePath).use { zip ->
            zip.entries().asSequence().forEach { entry ->
                zip.getInputStream(entry).use { input ->
                    val filePath = destDirectory + File.separator + entry.name
                    if (!entry.isDirectory) {
                        // if the entry is a file, extracts it
                        extractFile(input, filePath)
                    } else {
                        // if the entry is a directory, make the directory
                        val dir = File(filePath)
                        dir.mkdir()
                    }
                }
            }
        }
    }

    /**
     * Extracts a zip entry (file entry)
     * @param inputStream
     * @param destFilePath
     * @throws IOException
     */
    @Throws(IOException::class)
    private fun extractFile(inputStream: InputStream, destFilePath: String) {
        val bos = BufferedOutputStream(FileOutputStream(destFilePath))
        val bytesIn = ByteArray(BUFFER_SIZE)
        var read: Int
        while (inputStream.read(bytesIn).also { read = it } != -1) {
            bos.write(bytesIn, 0, read)
        }
        bos.close()
    }

    /**
     * Size of the buffer to read/write data
     */
    private const val BUFFER_SIZE = 4096

    fun zipFolder(sourcePath: String, toLocation: String?): Boolean {
        val sourceFile = File(sourcePath)
        try {
            val origin: BufferedInputStream?
            val dest = FileOutputStream(toLocation)
            val out = ZipOutputStream(
                BufferedOutputStream(
                    dest,
                ),
            )
            if (sourceFile.isDirectory()) {
                sourceFile.getParent()?.let {
                    zipSubFolder(out, sourceFile, it.length)
                }
            } else {
                val data = ByteArray(BUFFER_SIZE)
                val fi = FileInputStream(sourcePath)
                origin = BufferedInputStream(fi, BUFFER_SIZE)
                val entry = ZipEntry(getLastPathComponent(sourcePath))
                entry.setTime(sourceFile.lastModified())
                out.putNextEntry(entry)
                var count: Int
                while (origin.read(data, 0, BUFFER_SIZE).also { count = it } != -1) {
                    out.write(data, 0, count)
                }
            }
            out.close()
        } catch (e: Exception) {
            Timber.e(e, "Failed to zip file at $sourcePath")
            return false
        }
        return true
    }

    @Throws(IOException::class)
    private fun zipSubFolder(
        out: ZipOutputStream,
        folder: File,
        basePathLength: Int,
    ) {
        val fileList = folder.listFiles()
        if (fileList == null) {
            Timber.e("Failed to list files in folder $folder")
            return
        }
        var origin: BufferedInputStream?
        for (file in fileList) {
            if (file.isDirectory()) {
                zipSubFolder(out, file, basePathLength)
            } else {
                val data = ByteArray(BUFFER_SIZE)
                val unmodifiedFilePath = file.path
                val relativePath = unmodifiedFilePath
                    .substring(basePathLength)
                val fi = FileInputStream(unmodifiedFilePath)
                origin = BufferedInputStream(fi, BUFFER_SIZE)
                val entry = ZipEntry(relativePath)
                entry.setTime(file.lastModified())
                out.putNextEntry(entry)
                var count: Int
                while (origin.read(data, 0, BUFFER_SIZE).also { count = it } != -1) {
                    out.write(data, 0, count)
                }
                origin.close()
            }
        }
    }

    private fun getLastPathComponent(filePath: String): String {
        val segments = filePath.split("/".toRegex())
            .dropLastWhile { it.isEmpty() }
            .toTypedArray()
        return if (segments.isEmpty()) "" else segments[segments.size - 1]
    }
}

// Note: Might be a time-consuming operation
fun File.listFilesRecursively(): List<File> {
    val result = ArrayList<File>()
    val files = listFiles()
    if (files != null) {
        for (file in files) {
            if (file.isDirectory) {
                result.addAll(file.listFilesRecursively())
            } else {
                result.add(file)
            }
        }
    }
    return result
}
