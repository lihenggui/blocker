/*
 * Copyright 2025 Blocker
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

package com.merxury.blocker.core.root

import android.os.Parcelable
import be.mygod.librootkotlinx.RootCommand
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withContext
import kotlinx.parcelize.Parcelize

/** Represents the result of a root process execution. */
@Parcelize
data class ShellResult(
    val out: List<String>,
    val err: List<String>,
    val code: Int,
    val isSuccess: Boolean,
) : Parcelable

@Parcelize
data class RootProcessCommand(
    private val command: List<String>,
) : RootCommand<ShellResult> {
    override suspend fun execute(): ShellResult = withContext(Dispatchers.IO) {
        require(command.isNotEmpty()) { "Root process command must not be empty" }
        val process = ProcessBuilder(command).start()
        try {
            coroutineScope {
                val stdout = async {
                    process.inputStream.bufferedReader().use { it.readLines() }
                }
                val stderr = async {
                    process.errorStream.bufferedReader().use { it.readLines() }
                }
                val code = runInterruptible {
                    process.waitFor()
                }
                ShellResult(
                    out = stdout.await(),
                    err = stderr.await(),
                    code = code,
                    isSuccess = code == 0,
                )
            }
        } finally {
            process.destroy()
        }
    }
}
