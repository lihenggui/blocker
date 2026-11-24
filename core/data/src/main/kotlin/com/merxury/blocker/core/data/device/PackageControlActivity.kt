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

package com.merxury.blocker.core.data.device

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Singleton

/** Tracks package writes through their cache read-back, including across the chunks of a batch. */
@Singleton
class PackageControlActivity @Inject constructor() {
    private val active = MutableStateFlow<Map<String, Int>>(emptyMap())

    suspend fun <T> duringControl(packages: Set<String>, action: suspend () -> T): T {
        if (packages.isEmpty()) return action()
        changeCounts(packages, 1)
        try {
            return action()
        } finally {
            changeCounts(packages, -1)
        }
    }

    internal fun isActive(packageName: String): Boolean = packageName in active.value

    internal suspend fun awaitIdle(packageName: String) {
        active.first { packageName !in it }
    }

    private fun changeCounts(packages: Set<String>, delta: Int) = active.update { counts ->
        counts.toMutableMap().apply {
            packages.forEach { name ->
                val count = getOrDefault(name, 0) + delta
                if (count == 0) remove(name) else put(name, count)
            }
        }
    }
}
