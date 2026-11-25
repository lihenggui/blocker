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

package com.merxury.blocker.core.privileged.wake

import android.system.ErrnoException
import android.system.Os
import android.system.OsConstants
import com.merxury.blocker.core.privileged.PrivilegedAction
import com.merxury.blocker.core.privileged.PrivilegedCall
import com.merxury.blocker.core.privileged.SystemServices
import kotlinx.parcelize.Parcelize
import java.io.File

/*
 * Calls the wake device tests check and clean up the system files with, run in the privileged
 * process of a channel that may read them, so the checks need no su from the shell.
 */

/** Mode and owner of [path], as `644 0:0`, or null when there is none. */
@Parcelize
internal data class StatFile(val path: String) : PrivilegedCall<String?> {
    override val needsPrivilegedProcess: Boolean get() = true

    override fun run(system: SystemServices): String? = try {
        val stat = Os.stat(path)
        "${Integer.toOctalString(stat.st_mode and 0x1ff)} ${stat.st_uid}:${stat.st_gid}"
    } catch (e: ErrnoException) {
        if (e.errno != OsConstants.ENOENT) throw e
        null
    }
}

/** The content of [path], or null when there is none. */
@Parcelize
internal data class ReadFile(val path: String) : PrivilegedCall<String?> {
    override val needsPrivilegedProcess: Boolean get() = true

    override fun run(system: SystemServices): String? = File(path).takeIf { it.exists() }?.readText()
}

/** Writes [content] to [path], as a user's own rule file would be. */
@Parcelize
internal data class WriteFile(val path: String, val content: String) : PrivilegedAction {
    override val needsPrivilegedProcess: Boolean get() = true

    override fun run(system: SystemServices) = File(path).writeText(content)
}

/** Sets the mode of [path] to [mode]. */
@Parcelize
internal data class SetFileMode(val path: String, val mode: Int) : PrivilegedAction {
    override val needsPrivilegedProcess: Boolean get() = true

    override fun run(system: SystemServices) = Os.chmod(path, mode)
}

/** Deletes each of [paths] that is there. */
@Parcelize
internal data class DeleteFiles(val paths: List<String>) : PrivilegedAction {
    override val needsPrivilegedProcess: Boolean get() = true

    override fun run(system: SystemServices) {
        paths.forEach { File(it).delete() }
    }
}
