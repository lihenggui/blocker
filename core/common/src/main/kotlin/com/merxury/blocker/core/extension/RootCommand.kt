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
    val out: List<String>,
    val err: List<String>,
    val code: Int,
    val isSuccess: Boolean,
)
