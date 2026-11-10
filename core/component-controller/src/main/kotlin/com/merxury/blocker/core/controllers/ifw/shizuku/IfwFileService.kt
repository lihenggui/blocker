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

import android.os.IBinder
import timber.log.Timber
import kotlin.system.exitProcess

/**
 * Runs inside a Shizuku UserService at the Shizuku UID (1000/0). Performs raw IFW file I/O.
 * Must keep a public no-arg constructor: Shizuku reflectively instantiates it.
 */
class IfwFileService : IIfwFileService.Stub() {

    override fun readFile(path: String): String? = IfwFileOps.read(path)

    override fun writeFile(path: String, content: String): Boolean = IfwFileOps.writeAtomic(path, content)

    override fun deleteFile(path: String): Boolean = IfwFileOps.delete(path)

    override fun fileExists(path: String): Boolean = IfwFileOps.exists(path)

    override fun listFiles(dir: String): List<String> = IfwFileOps.list(dir)

    override fun destroy() {
        Timber.d("IfwFileService destroy()")
        exitProcess(0)
    }

    override fun asBinder(): IBinder = this
}
