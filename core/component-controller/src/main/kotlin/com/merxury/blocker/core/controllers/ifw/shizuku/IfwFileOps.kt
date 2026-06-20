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

package com.merxury.blocker.core.controllers.ifw.shizuku

import android.system.Os
import org.lsposed.hiddenapibypass.HiddenApiBypass
import timber.log.Timber
import java.io.File

object IfwFileOps {
    private const val MODE_0644 = 420 // 0644 octal

    fun read(path: String): String? {
        val file = File(path)
        return if (file.exists()) file.readText(Charsets.UTF_8) else null
    }

    fun writeAtomic(path: String, content: String): Boolean = try {
        val target = File(path)
        target.parentFile?.mkdirs()
        val tmp = File(path + ".tmp")
        tmp.writeText(content, Charsets.UTF_8)
        Os.chmod(tmp.absolutePath, MODE_0644)
        restorecon(tmp.absolutePath)
        if (!tmp.renameTo(target)) {
            tmp.delete()
            false
        } else {
            restorecon(target.absolutePath)
            // verify by read-back: an unreadable/mislabeled write is a silent no-op otherwise
            read(path) == content
        }
    } catch (e: Exception) {
        Timber.e(e, "writeAtomic failed for $path")
        File(path + ".tmp").delete()
        false
    }

    fun delete(path: String): Boolean = File(path).let { it.exists() && it.delete() }

    fun exists(path: String): Boolean = File(path).exists()

    fun list(dir: String): List<String> = File(dir).takeIf { it.exists() }?.list()?.toList() ?: emptyList()

    private fun restorecon(path: String) {
        try {
            // android.os.SELinux.restorecon(String) is @hide; reachable via HiddenApiBypass.
            HiddenApiBypass.invoke(
                Class.forName("android.os.SELinux"),
                null,
                "restorecon",
                path,
            )
        } catch (e: Throwable) {
            Timber.w(e, "restorecon unavailable for $path (continuing)")
        }
    }
}
