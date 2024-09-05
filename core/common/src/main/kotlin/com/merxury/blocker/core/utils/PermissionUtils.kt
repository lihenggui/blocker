/*
 * Copyright 2024 Blocker
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

object PermissionUtils {

    private var rooted: Boolean = false

    suspend fun isRootAvailable(
        dispatcher: CoroutineDispatcher,
    ): Boolean = withContext(dispatcher) {
        if (rooted) {
            return@withContext true
        }
        val libSuStatus = Shell.isAppGrantedRoot() ?: false
        if (libSuStatus) {
            Timber.i("Get root permission from isAppGrantedRoot")
            rooted = true
            return@withContext true
        } else {
            val requestResult = requestRootPermission(dispatcher)
            if (requestResult) {
                rooted = true
                Timber.i("Requested root permission from Shell.cmd(\"su\")")
                return@withContext true
            }
            val runtimeResult = requestRootInRuntime(dispatcher)
            if (runtimeResult) {
                rooted = true
                Timber.i("Requested root permission from Runtime.getRuntime().exec(su)")
            }
            return@withContext runtimeResult
        }
    }

    private suspend fun requestRootPermission(dispatcher: CoroutineDispatcher): Boolean = withContext(dispatcher) {
        Shell.cmd("su").exec().isSuccess
    }

    private suspend fun requestRootInRuntime(dispatcher: CoroutineDispatcher): Boolean {
        // isAppGrantedRoot is always false on KernelSU and APatch.
        // This method looks for a file but su is not a real file in those
        return withContext(dispatcher) {
            try {
                Runtime.getRuntime().exec("su --version")
                return@withContext true
            } catch (e: IOException) {
                Timber.e("Root not available: ${e.message}")
                return@withContext false
            }
        }
    }
}
