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

import android.os.Handler
import android.os.Looper
import android.os.Parcelable
import be.mygod.librootkotlinx.RootCommand
import be.mygod.librootkotlinx.io.awaitExit
import be.mygod.librootkotlinx.io.openReadChannel
import be.mygod.librootkotlinx.io.startPipes
import be.mygod.librootkotlinx.io.useLines
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
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
        ProcessBuilder(command)
            .startPipes(stdin = false)
            .use { pipes ->
                val handler = Handler(Looper.getMainLooper())
                val stdout = pipes.stdout!!.openReadChannel(handler)
                val stderr = pipes.stderr!!.openReadChannel(handler)
                coroutineScope {
                    val stdoutLines = async { stdout.readLines() }
                    val stderrLines = async { stderr.readLines() }
                    val code = pipes.process.awaitExit()
                    ShellResult(
                        out = stdoutLines.await(),
                        err = stderrLines.await(),
                        code = code,
                        isSuccess = code == 0,
                    )
                }
            }
    }

    private suspend fun ByteReadChannel.readLines(): List<String> {
        val lines = mutableListOf<String>()
        useLines(lines::add)
        return lines
    }
}
