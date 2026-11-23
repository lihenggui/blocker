// Copyright 2026 Blocker
// SPDX-License-Identifier: Apache-2.0

package com.merxury.core.ifw

import android.system.Os
import be.mygod.librootkotlinx.RootCommandNoResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.parcelize.Parcelize
import java.io.File

// rw-r--r--: system_server reads rule files as the system user.
private const val RULE_FILE_MODE = 0x1a4

/**
 * Replaces [target] with [content] so that readers only ever see the old or the new file.
 *
 * system_server re-reads every `.xml` file in the IFW directory when it changes, so the content is
 * written, given its final mode and synced in a `.tmp` file in the same directory, then renamed over
 * [target]. Must run in a process that can write to that directory.
 */
fun replaceIfwFile(target: File, content: String) {
    val temporary = File.createTempFile(".blocker-ifw-", ".tmp", target.parentFile)
    try {
        temporary.outputStream().use { file ->
            file.write(content.toByteArray())
            file.fd.sync()
        }
        Os.chmod(temporary.path, RULE_FILE_MODE)
        Os.rename(temporary.path, target.path)
    } finally {
        temporary.delete()
    }
}

/** Runs [replaceIfwFile] in the root process. */
@Parcelize
internal data class ReplaceIfwFileCommand(
    private val path: String,
    private val content: String,
) : RootCommandNoResult {
    override suspend fun execute() = withContext(Dispatchers.IO) {
        replaceIfwFile(File(path), content)
        null
    }
}
