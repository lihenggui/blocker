/*
 * Copyright 2023 Blocker
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
package com.merxury.blocker.core.extension

import com.merxury.blocker.core.utils.PermissionUtils
import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Created by Mercury on 2018/2/4.
 */
suspend fun String.exec(dispatcher: CoroutineDispatcher = Dispatchers.IO): ShellResult =
    withContext(dispatcher) {
        val rootGranted = PermissionUtils.isRootAvailable(dispatcher)
        if (!rootGranted) {
            throw RuntimeException("Root unavailable")
        }
        val result = Shell.cmd(this@exec).exec()
        return@withContext ShellResult(
            result.out,
            result.err,
            result.code,
            result.isSuccess,
        )
    }

data class ShellResult(
    /**
     * Get the output of STDOUT.
     * @return a list of strings that stores the output of STDOUT. Empty list if no output
     * is available.
     */
    val out: List<String>,
    /**
     * Get the output of STDERR.
     * @return a list of strings that stores the output of STDERR. Empty list if no output
     * is available.
     */
    val err: List<String>,
    /**
     * Get the return code of the job.
     * @return the return code of the last operation in the shell. If the job is executed
     * properly, the code should range from 0-255. If the job fails to execute, it will return
     * {@link #JOB_NOT_EXECUTED} (-1).
     */
    val code: Int,
    /**
     * Whether the job succeeded.
     * {@code getCode() == 0}.
     * @return {@code true} if the return code is 0.
     */
    val isSuccess: Boolean,
)
