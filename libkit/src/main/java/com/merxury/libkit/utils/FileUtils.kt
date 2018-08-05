package com.merxury.libkit.utils

import android.os.Environment
import android.util.Log
import com.merxury.libkit.RootCommand
import com.stericson.RootTools.RootTools
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException


object FileUtils {
    const val TAG = "FileUtils"

    @JvmStatic
    fun copy(source: String, dest: String): Boolean {
        Log.i(TAG, "Copy $source to $dest")
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
            Log.e(TAG, e.message)
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
            val output = RootCommand.runBlockingCommand("[ -f '$path' ] && echo \"yes\" || echo \"no\"")
            when (output.trim()) {
                "yes" -> true
                else -> false
            }
        } catch (e: Exception) {
            Log.e(TAG, e.message)
            false
        }
    }

    @JvmStatic
    fun listFiles(path: String): List<String> {
        val output = RootCommand.runBlockingCommand("find '$path'")
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
    fun copyWithRoot(source: String, dest: String): Boolean {
        Log.i(TAG, "Copy $source to $dest with root permission")
        return RootTools.copyFile(source, dest, false, true)
    }

    @JvmStatic
    fun isExternalStorageWritable(): Boolean {
        return Environment.getExternalStorageState() == Environment.MEDIA_MOUNTED
    }

    @JvmStatic
    fun isExternalStorageReadable(): Boolean {
        return Environment.getExternalStorageState() in
                setOf(Environment.MEDIA_MOUNTED, Environment.MEDIA_MOUNTED_READ_ONLY)
    }

    @JvmStatic
    fun getExternalStoragePath(): String {
        return Environment.getExternalStorageDirectory().absolutePath + File.separator
    }

    @JvmStatic
    fun delete(file: String, recursively: Boolean): Boolean {
        val comm = if (recursively) {
            "rm -rf '$file'"
        } else {
            "rm -f '$file'"
        }
        val output = RootCommand.runBlockingCommand(comm)
        val result = output.trim().isEmpty();
        Log.d(TAG, "Delete file $file, result = $result")
        return result
    }

    @JvmStatic
    fun getFileName(path: String): String {
        val trimmedPath = path.trim()
        if (trimmedPath.isEmpty()) {
            return ""
        }
        val filename = trimmedPath.split("/").last()
        val extensionDotPosition = filename.lastIndexOf(".")
        return if (extensionDotPosition <= 0) {
            ""
        } else {
            filename.substring(0, extensionDotPosition)
        }
    }

    @JvmStatic
    fun getFileCounts(pathString: String, filter: String): Int {
        val path = File(pathString)
        if (!path.exists()) {
            return 0
        }
        if (path.isFile) {
            return 1
        }
        return path.walkTopDown().filter { it.name.contains(filter) }.count()
    }
}