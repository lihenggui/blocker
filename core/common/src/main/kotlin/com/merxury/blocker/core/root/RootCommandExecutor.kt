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
import be.mygod.librootkotlinx.NoShellException
import be.mygod.librootkotlinx.RootCommand
import com.merxury.blocker.core.exception.RootUnavailableException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RootCommandExecutor @Inject constructor(
    private val rootSession: BlockerRootSession,
) {
    suspend fun <T : Parcelable?> execute(command: RootCommand<T>): T = try {
        rootSession.use { server ->
            server.execute(command, command.javaClass.classLoader)
        }
    } catch (e: NoShellException) {
        throw RootUnavailableException(e)
    }

    suspend fun run(vararg command: String): ShellResult = execute(RootProcessCommand(command.toList()))
}
