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

package com.merxury.blocker.core.utils

import com.merxury.blocker.core.dispatchers.BlockerDispatchers.IO
import com.merxury.blocker.core.dispatchers.Dispatcher
import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Production implementation of [RootAvailabilityChecker] that checks root access
 * using a three-level fallback: [Shell.isAppGrantedRoot], `Shell.cmd("su")`,
 * and `Runtime.exec("su")`. The result is cached after the first successful check.
 */
@Singleton
class ShellRootAvailabilityChecker @Inject constructor(
    @Dispatcher(IO) private val dispatcher: CoroutineDispatcher,
) : RootAvailabilityChecker {

    private val rooted = AtomicBoolean(false)

    override suspend fun isRootAvailable(): Boolean = withContext(dispatcher) {
        if (rooted.get()) {
            return@withContext true
        }
        val libSuStatus = Shell.isAppGrantedRoot() ?: false
        if (libSuStatus) {
            Timber.i("Get root permission from isAppGrantedRoot")
            rooted.set(true)
            return@withContext true
        }
        val requestResult = requestRootPermission()
        if (requestResult) {
            rooted.set(true)
            Timber.i("Requested root permission from Shell.cmd(\"su\")")
            return@withContext true
        }
        val runtimeResult = requestRootInRuntime()
        if (runtimeResult) {
            rooted.set(true)
            Timber.i("Requested root permission from Runtime.getRuntime().exec(su)")
        }
        return@withContext runtimeResult
    }

    private suspend fun requestRootPermission(): Boolean = withContext(dispatcher) {
        Shell.cmd("su").exec().isSuccess
    }

    private suspend fun requestRootInRuntime(): Boolean {
        // isAppGrantedRoot is always false on KernelSU and APatch.
        // This method looks for a file but su is not a real file in those
        return withContext(dispatcher) {
            try {
                val process = Runtime.getRuntime().exec("su")
                val exitValue = process.waitFor()
                val isSuccess = exitValue == 0
                if (isSuccess) {
                    Timber.i("Requested root permission from Runtime.getRuntime().exec(su)")
                } else {
                    Timber.e("Root unavailable: exitValue of the su command is not 0")
                }
                return@withContext isSuccess
            } catch (e: IOException) {
                Timber.e("Root unavailable: ${e.message}")
                return@withContext false
            }
        }
    }
}
