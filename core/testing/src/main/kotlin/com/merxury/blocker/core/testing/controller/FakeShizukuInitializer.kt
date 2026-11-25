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

package com.merxury.blocker.core.testing.controller

import com.merxury.blocker.core.privileged.shizuku.RegisterShizukuResult
import com.merxury.blocker.core.privileged.shizuku.ShizukuInitializer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first

/**
 * @param permissionGranted whether the Shizuku permission is already granted.
 * @param grantsOnRequest what the user answers when [registerShizuku] has to ask for it.
 * @param shizukuRunning whether Shizuku is running. Use [startShizuku] to start it later.
 */
class FakeShizukuInitializer(
    var shizukuUid: Int = 0,
    var permissionGranted: Boolean = true,
    var grantsOnRequest: Boolean = false,
    shizukuRunning: Boolean = true,
) : ShizukuInitializer {
    private val running = MutableStateFlow(shizukuRunning)

    var registerCallCount = 0
        private set

    /** Number of [awaitBinder] calls still waiting. */
    var binderWaiterCount = 0
        private set

    fun startShizuku() {
        running.value = true
    }

    override suspend fun registerShizuku(): RegisterShizukuResult {
        registerCallCount++
        if (!running.value) return RegisterShizukuResult.NotRunning
        if (!permissionGranted && grantsOnRequest) permissionGranted = true
        return if (permissionGranted) RegisterShizukuResult.Granted(shizukuUid) else RegisterShizukuResult.Denied
    }

    override suspend fun registerShizukuIfGranted(): RegisterShizukuResult {
        registerCallCount++
        if (!running.value) return RegisterShizukuResult.NotRunning
        return if (permissionGranted) RegisterShizukuResult.Granted(shizukuUid) else RegisterShizukuResult.Denied
    }

    override suspend fun awaitBinder() {
        binderWaiterCount++
        try {
            running.first { it }
        } finally {
            binderWaiterCount--
        }
    }
}
