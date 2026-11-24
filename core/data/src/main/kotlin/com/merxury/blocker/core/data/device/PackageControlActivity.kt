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

import android.os.SystemClock
import com.merxury.blocker.core.model.component.AppKey
import com.merxury.blocker.core.model.component.ComponentId
import com.merxury.blocker.core.model.component.UserId
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/** Tracks package writes through their cache read-back, including across the chunks of a batch. */
@Singleton
class PackageControlActivity internal constructor(private val elapsedRealtime: () -> Long) {
    @Inject constructor() : this(SystemClock::elapsedRealtime)

    private val lock = Any()
    private val active = MutableStateFlow<Map<String, ActiveControl>>(emptyMap())
    private val recent = LinkedHashMap<AppKey, RecentControl>()

    suspend fun <T> duringControl(packages: Set<String>, action: suspend () -> T): T {
        if (packages.isEmpty()) return action()
        synchronized(lock) {
            val now = elapsedRealtime()
            active.value = active.value.toMutableMap().apply {
                packages.forEach { name ->
                    val control = get(name)
                    put(name, control?.copy(count = control.count + 1) ?: ActiveControl(1, now))
                }
            }
        }
        try {
            return action()
        } finally {
            endControl(packages)
        }
    }

    internal fun recordWrites(app: AppKey, components: Set<ComponentId>) {
        if (components.isEmpty()) return
        synchronized(lock) {
            val control = active.value[app.packageName] ?: return
            control.writes.getOrPut(app.userId, ::mutableSetOf).addAll(components.map { it.className })
        }
    }

    internal fun isActive(packageName: String): Boolean = packageName in active.value

    internal suspend fun awaitIdle(packageName: String) {
        active.first { packageName !in it }
    }

    internal fun coversChange(app: AppKey, components: Set<String>, receivedAt: Long): Boolean = synchronized(lock) {
        if (components.isEmpty() || app.packageName in components) return@synchronized false
        val control = recent[app] ?: return@synchronized false
        receivedAt in control.startedAt..(control.finishedAt + RECENT_MILLIS) && control.components.containsAll(components)
    }

    private fun endControl(packages: Set<String>) = synchronized(lock) {
        val now = elapsedRealtime()
        recent.values.removeAll { now - it.finishedAt > RECENT_MILLIS }
        active.value = active.value.toMutableMap().apply {
            packages.forEach { name ->
                val control = getValue(name)
                if (control.count > 1) {
                    put(name, control.copy(count = control.count - 1))
                } else {
                    remove(name)
                    control.writes.forEach { (user, components) ->
                        val app = AppKey(name, user)
                        recent.remove(app)
                        recent[app] = RecentControl(control.startedAt, now, components.toSet())
                    }
                }
            }
        }
        while (recent.size > MAX_RECENT_PACKAGES) recent.remove(recent.keys.first())
    }

    private data class ActiveControl(
        val count: Int,
        val startedAt: Long,
        val writes: MutableMap<UserId, MutableSet<String>> = mutableMapOf(),
    )

    private data class RecentControl(val startedAt: Long, val finishedAt: Long, val components: Set<String>)

    private companion object {
        const val RECENT_MILLIS = 3_000L
        const val MAX_RECENT_PACKAGES = 512
    }
}
