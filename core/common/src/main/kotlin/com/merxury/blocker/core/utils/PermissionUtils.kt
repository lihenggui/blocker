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

import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Internal utility used only by [String.exec][com.merxury.blocker.core.extension.exec]
 * as a defensive root check for extension functions that cannot use dependency injection.
 *
 * All injectable classes should use [RootAvailabilityChecker] instead.
 */
internal object PermissionUtils {

    private val rooted = AtomicBoolean(false)

    suspend fun isRootAvailable(
        dispatcher: CoroutineDispatcher,
    ): Boolean = withContext(dispatcher) {
        if (rooted.get()) {
            return@withContext true
        }
        val libSuStatus = Shell.isAppGrantedRoot() ?: false
        if (libSuStatus) {
            Timber.i("Get root permission from isAppGrantedRoot")
            rooted.set(true)
            return@withContext true
        }
        val requestResult = requestRootPermission(dispatcher)
        if (requestResult) {
            rooted.set(true)
            Timber.i("Requested root permission from Shell.cmd(\"su\")")
            return@withContext true
        }
        val runtimeResult = requestRootInRuntime(dispatcher)
        if (runtimeResult) {
            rooted.set(true)
            Timber.i("Requested root permission from Runtime.getRuntime().exec(su)")
        }
        return@withContext runtimeResult
    }

    private suspend fun requestRootPermission(
        dispatcher: CoroutineDispatcher,
    ): Boolean = withContext(dispatcher) {
        Shell.cmd("su").exec().isSuccess
    }

    private suspend fun requestRootInRuntime(dispatcher: CoroutineDispatcher): Boolean {
        return withContext(dispatcher) {
            try {
                val process = Runtime.getRuntime().exec("su")
                val exitValue = process.waitFor()
                val isSuccess = exitValue == 0
                if (!isSuccess) {
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
