/*
 * Copyright 2026 Blocker
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

package com.merxury.blocker.core.privileged.calls

import android.system.ErrnoException
import android.system.Os
import android.system.OsConstants
import com.merxury.blocker.core.privileged.PrivilegedAction
import com.merxury.blocker.core.privileged.PrivilegedCall
import com.merxury.blocker.core.privileged.SystemServices
import com.merxury.core.ifw.IfwStorageUtils
import com.merxury.core.ifw.isRuleFileName
import com.merxury.core.ifw.replaceIfwFile
import kotlinx.parcelize.Parcelize
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException

private const val EXTENSION = ".xml"

/*
 * The calls on the Intent Firewall directory. Each names a package, never a path, and checks the
 * name again when it is created in the privileged process, so no call reaches a file outside the
 * directory or one of the wake feature's files. Each is safe to run twice.
 */

/** The content of the package's rule file, or null when it has none. */
@Parcelize
internal data class ReadIfwRuleFile(val packageName: String) : PrivilegedCall<String?> {
    init {
        requireRuleFileName(packageName)
    }

    override val needsPrivilegedProcess: Boolean get() = true

    override fun run(system: SystemServices): String? = IfwDirectory().read(packageName)
}

/** Replaces the package's rule file with [content] so readers only ever see the old or new file. */
@Parcelize
internal data class ReplaceIfwRuleFile(val packageName: String, val content: String) : PrivilegedAction {
    init {
        requireRuleFileName(packageName)
    }

    override val needsPrivilegedProcess: Boolean get() = true

    override fun run(system: SystemServices) = IfwDirectory().replace(packageName, content)

    // The content can be long and says nothing a log needs.
    override fun toString(): String = "ReplaceIfwRuleFile($packageName, ${content.length} chars)"
}

/** Deletes the package's rule file. Returns whether there was one. */
@Parcelize
internal data class DeleteIfwRuleFile(val packageName: String) : PrivilegedCall<Boolean> {
    init {
        requireRuleFileName(packageName)
    }

    override val needsPrivilegedProcess: Boolean get() = true

    override fun run(system: SystemServices): Boolean = IfwDirectory().delete(packageName)
}

/**
 * The names, without `.xml`, of every file in the directory that ends in `.xml`, including the
 * wake feature's and hidden ones; callers keep the names they may open, see
 * [com.merxury.core.ifw.isRuleFileName].
 */
@Parcelize
internal data object ListIfwRuleFiles : PrivilegedCall<List<String>> {
    override val needsPrivilegedProcess: Boolean get() = true

    override fun run(system: SystemServices): List<String> = IfwDirectory().list()
}

private fun requireRuleFileName(packageName: String) {
    // Guards the file access only; which names count as packages is the caller's decision.
    require(isRuleFileName(packageName)) { "Not a rule file name Blocker may open: $packageName" }
}

/**
 * The file operations behind the calls, on [directory]. Errors that mean this identity may not
 * touch the file become [SecurityException], so they read as not permitted rather than as a
 * damaged file.
 */
internal class IfwDirectory(private val directory: File = File(IfwStorageUtils.ifwFolder)) {

    fun read(packageName: String): String? {
        val file = fileOf(packageName)
        if (!exists(file)) return null
        return try {
            file.readText()
        } catch (e: FileNotFoundException) {
            // Deleted since it was found, or not readable after all.
            when (errnoOf(e)) {
                OsConstants.ENOENT -> null
                else -> throw e.asSecurityExceptionIfDenied()
            }
        }
    }

    fun replace(packageName: String, content: String) {
        if (!exists(directory)) check(directory.mkdirs()) { "Cannot create $directory" }
        try {
            replaceIfwFile(fileOf(packageName), content)
        } catch (e: IOException) {
            throw e.asSecurityExceptionIfDenied()
        } catch (e: ErrnoException) {
            throw e.asSecurityExceptionIfDenied()
        }
    }

    fun delete(packageName: String): Boolean = try {
        Os.remove(fileOf(packageName).path)
        true
    } catch (e: ErrnoException) {
        if (e.errno == OsConstants.ENOENT) false else throw e.asSecurityExceptionIfDenied()
    }

    fun list(): List<String> {
        if (!exists(directory)) return emptyList()
        val names = directory.list() ?: throw IOException("Cannot list $directory")
        return names.filter { it.endsWith(EXTENSION) }.map { it.removeSuffix(EXTENSION) }.sorted()
    }

    private fun fileOf(packageName: String): File {
        requireRuleFileName(packageName)
        return File(directory, packageName + EXTENSION)
    }

    /** Whether [file] exists. Unlike [File.exists], says so when it cannot tell. */
    private fun exists(file: File): Boolean = try {
        Os.stat(file.path)
        true
    } catch (e: ErrnoException) {
        if (e.errno == OsConstants.ENOENT) false else throw e.asSecurityExceptionIfDenied()
    }
}

private fun errnoOf(e: Throwable): Int? = generateSequence(e) { it.cause }.take(4).filterIsInstance<ErrnoException>().firstOrNull()?.errno

private fun Exception.asSecurityExceptionIfDenied(): Exception = when (errnoOf(this)) {
    OsConstants.EACCES, OsConstants.EPERM -> SecurityException(message).also { it.initCause(this) }
    else -> this
}
