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

import android.content.Context
import android.os.ParcelFileDescriptor
import be.mygod.librootkotlinx.Logger
import be.mygod.librootkotlinx.RootServer
import be.mygod.librootkotlinx.RootSession
import be.mygod.librootkotlinx.io.awaitExit
import com.merxury.blocker.core.di.ApplicationScope
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.time.Duration.Companion.seconds

@Singleton
class BlockerRootSession @Inject constructor(
    @ApplicationContext private val appContext: Context,
    @ApplicationScope private val applicationScope: CoroutineScope,
) : RootSession(),
    Logger {
    override val context: Context
        get() = appContext

    override fun d(m: String?, t: Throwable?) = Timber.d(t, m)
    override fun e(m: String?, t: Throwable?) = Timber.e(t, m)
    override fun i(m: String?, t: Throwable?) = Timber.i(t, m)
    override fun w(m: String?, t: Throwable?) = Timber.w(t, m)

    override val rootLifecycleCoroutineContext get() = EmptyCoroutineContext

    override suspend fun handleRootLifecycle(
        process: Process,
        stdin: ParcelFileDescriptor,
        stdout: ParcelFileDescriptor,
        stderr: ParcelFileDescriptor,
    ) = try {
        super.handleRootLifecycle(process, stdin, stdout, stderr)
    } finally {
        applicationScope.launch {
            var exit = withTimeoutOrNull(10.seconds) { process.awaitExit() }
            if (exit == null) {
                process.destroy()
                exit = withTimeoutOrNull(5.seconds) { process.awaitExit() }
                Timber.w(
                    Exception(
                        if (exit == null) {
                            process.destroyForcibly()
                            "Root JVM refused to exit"
                        } else {
                            "Root JVM exited with $exit and timeout"
                        },
                    ),
                )
            } else if (exit != 0) {
                Timber.w(Exception("Root JVM unexpectedly exited with $exit"))
            }
        }
    }

    override suspend fun initServer(server: RootServer) {
        Logger.me = this
        super.initServer(server)
    }
}
