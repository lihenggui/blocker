package com.merxury.blocker.core.utils

import com.elvishew.xlog.XLog
import com.merxury.blocker.core.RootCommand
import com.topjohnwu.superuser.io.SuFile
import com.topjohnwu.superuser.io.SuFileInputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

object FileUtils {
    private val logger = XLog.tag("FileUtils").build()

    @JvmStatic
    fun copy(source: String, dest: String): File {
        val sourceFile = SuFile(source)
        val destFile = SuFile(dest)
        return sourceFile.copyTo(destFile)
    }

    @JvmStatic
    fun cat(source: String, dest: String) {
        RootCommand.runBlockingCommand("cat '$source' > '$dest'")
    }

    @JvmStatic
    fun listFiles(path: String): List<String> {
        val file = SuFile(path)
        if (!file.exists()) {
            logger.w("File $path not exists")
            return ArrayList()
        }
        return file.list()?.toList() ?: ArrayList()
    }

    @JvmStatic
    fun chmod(path: String, permission: Int, recursively: Boolean) {
        val comm = when (recursively) {
            true -> "chmod $permission '$path'"
            false -> "chmod -R $permission '$path'"
        }
        com.merxury.blocker.core.RootCommand.runBlockingCommand(comm)
    }

    @JvmStatic
    fun read(path: String): String {
        SuFileInputStream.open(path).use {
            return it.readBytes().toString(Charsets.UTF_8)
        }
    }

    @JvmStatic
    @Throws(IOException::class)
    fun delete(path: String, recursively: Boolean): Boolean {
        val file = SuFile(path)
        if (!file.exists()) {
            logger.e("Can't delete $path since it doesn't exist")
            return false
        }
        return if (recursively) {
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

    suspend fun zipFile(prefix: String, input: File): File? {
        return withContext(Dispatchers.IO) {
            if (!input.exists()) {
                return@withContext null
            }
            val output = File.createTempFile(prefix, ".zip")
            ZipOutputStream(BufferedOutputStream(FileOutputStream(output))).use { zos ->
                input.walkTopDown().forEach { file ->
                    val zipFileName = file.absolutePath
                        .removePrefix(input.absolutePath)
                        .removePrefix("/")
                    val entry = ZipEntry("$zipFileName${(if (file.isDirectory) "/" else "")}")
                    zos.putNextEntry(entry)
                    if (file.isFile) {
                        file.inputStream().copyTo(zos)
                    }
                }
            }
            return@withContext output
        }
    }
}