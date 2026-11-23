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

package com.merxury.blocker.core.privileged

import android.app.Activity
import android.os.Bundle
import android.system.ErrnoException
import android.system.Os
import com.merxury.core.ifw.IfwStorageUtils
import kotlinx.parcelize.Parcelize
import java.util.concurrent.atomic.AtomicInteger

/** An activity the device test blocks; counts each start and finishes at once. */
class IfwFixtureActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        starts.incrementAndGet()
        finish()
    }

    companion object {
        val starts = AtomicInteger()
    }
}

/** Mode, owner, group and SELinux label of a package's rule file and of the directory, read where the call runs. */
@Parcelize
internal data class StatIfwRuleFile(val packageName: String) : PrivilegedCall<String> {
    override val needsPrivilegedProcess: Boolean get() = true

    override fun run(system: SystemServices): String {
        val directory = IfwStorageUtils.ifwFolder.removeSuffix("/")
        return "file ${describe("$directory/$packageName.xml")}; directory ${describe(directory)}; process uid ${android.os.Process.myUid()}"
    }

    private fun describe(path: String): String {
        val stat = Os.stat(path)
        val label = try {
            String(Os.getxattr(path, "security.selinux")).trimEnd('\u0000')
        } catch (e: ErrnoException) {
            "unknown (${e.message})"
        }
        return "${Integer.toOctalString(stat.st_mode and 0x1ff)} ${stat.st_uid}:${stat.st_gid} $label"
    }
}

/** Ends the privileged process it runs in, as a crash or a kill would. */
@Parcelize
internal data object EndPrivilegedProcess : PrivilegedCall<Unit> {
    override val needsPrivilegedProcess: Boolean get() = true

    override fun run(system: SystemServices) {
        android.os.Process.killProcess(android.os.Process.myPid())
    }
}
