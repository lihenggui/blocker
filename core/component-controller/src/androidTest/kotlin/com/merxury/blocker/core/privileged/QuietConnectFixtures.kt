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

import androidx.test.platform.app.InstrumentationRegistry
import com.merxury.blocker.core.controllers.shizuku.DefaultShizukuApi
import com.merxury.blocker.core.controllers.shizuku.ShizukuApi
import com.merxury.blocker.core.privileged.root.BlockerRootSession
import com.merxury.blocker.core.privileged.root.RootCallCommand
import com.merxury.blocker.core.privileged.root.RootCommandRunner
import com.merxury.blocker.core.privileged.root.RootGrantMemory

/** Remembers a root grant for one test only. */
internal class InMemoryRootGrantMemory(remembered: Boolean) : RootGrantMemory {
    @Volatile
    var remembered = remembered
        private set

    override suspend fun isRemembered(): Boolean = remembered

    override suspend fun remember(granted: Boolean) {
        remembered = granted
    }
}

/** Runs commands in a root process started through su, as the app does, and counts them. */
internal class SessionRootRunner : RootCommandRunner {
    private val session = BlockerRootSession(InstrumentationRegistry.getInstrumentation().targetContext)
    val hasActiveServer: Boolean get() = session.hasActiveServer

    @Volatile
    var runs = 0
        private set

    override suspend fun run(command: RootCallCommand): CallBytes {
        runs++
        return session.use { server -> server.execute(command, RootCallCommand::class.java.classLoader) }
    }
}

/** The real Shizuku API, except that asking for the permission is only counted, never shown. */
internal class PromptCountingShizukuApi(private val api: ShizukuApi = DefaultShizukuApi()) : ShizukuApi by api {
    @Volatile
    var requests = 0
        private set

    override fun requestPermission(requestCode: Int) {
        requests++
    }
}
