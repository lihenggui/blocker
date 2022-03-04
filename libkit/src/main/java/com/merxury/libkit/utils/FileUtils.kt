package com.merxury.libkit.utils

import com.elvishew.xlog.XLog
import com.merxury.libkit.RootCommand
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException

object FileUtils {
    private val logger = XLog.tag("FileUtils").build()

    @JvmStatic
    fun copy(source: String, dest: String): Boolean {
        logger.i("Copy $source to $dest")
        try {
            FileInputStream(source).use { input ->
                FileOutputStream(dest).use { output ->
                    val buf = ByteArray(1024)
                    var length = input.read(buf)
                    while (length > 0) {
                        output.write(buf, 0, length)
                        length = input.read(buf)
                    }
                }
            }
        } catch (e: IOException) {
            e.printStackTrace()
            logger.e(e.message)
            return false
        }
        return true
    }

    @JvmStatic
    fun cat(source: String, dest: String) {
        RootCommand.runBlockingCommand("cat '$source' > '$dest'")
    }

    @JvmStatic
    fun isExist(path: String): Boolean {
        return try {
            if (!PermissionUtils.isRootAvailable) {
                return false
            }
            val output =
                RootCommand.runBlockingCommand("[ -f '$path' ] && echo \"yes\" || echo \"no\"")
            when (output.trim()) {
                "yes" -> true
                else -> false
            }
        } catch (e: Exception) {
            logger.e(e.message)
            false
        }
    }

    @JvmStatic
    fun listFiles(path: String): List<String> {
        val output = RootCommand.runBlockingCommand("ls '$path'")
        if (output.contains("No such file or directory")) {
            return ArrayList()
        }
        val files = output.split("\n")
        return files.filterNot { it.isEmpty() || it == path }
    }

    @JvmStatic
    fun chmod(path: String, permission: Int, recursively: Boolean) {
        val comm = when (recursively) {
            true -> "chmod $permission '$path'"
            false -> "chmod -R $permission '$path'"
        }
        RootCommand.runBlockingCommand(comm)
    }

    @JvmStatic
    fun read(path: String): String {
        val comm = "cat '$path'"
        if (!isExist(path)) {
            return ""
        }
        return RootCommand.runBlockingCommand(comm)
    }

    @JvmStatic
    fun delete(file: String, recursively: Boolean): Boolean {
        val comm = if (recursively) {
            "rm -rf '$file'"
        } else {
            "rm -f '$file'"
        }
        val output = RootCommand.runBlockingCommand(comm)
        val result = output.trim().isEmpty()
        logger.d("Delete file $file, result = $result")
        return result
    }
}