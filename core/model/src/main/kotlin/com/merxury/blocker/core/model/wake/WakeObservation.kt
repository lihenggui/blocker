// Copyright 2026 Blocker
// SPDX-License-Identifier: Apache-2.0

package com.merxury.blocker.core.model.wake

enum class WakeAccess { NONE, SHIZUKU_SHELL, SHIZUKU_LEGACY_ROOT, SHIZUKU_ROOT, DIRECT_ROOT }

const val SYSTEM_WAKE_EVENTS = "EVENTS/system/epoch"

/** A timestamped system observation. Missing identities and cold-start indicators stay absent. */
data class WakeObservation(
    val kind: String,
    val elapsedNanos: Long,
    val callerUid: Int? = null,
    val targetUid: Int? = null,
    val callerPackage: String? = null,
    val targetPackage: String? = null,
    val component: String? = null,
    val action: String? = null,
    val startType: Int? = null,
    val pid: Int? = null,
    val processName: String? = null,
    val attributes: Map<String, String> = emptyMap(),
    val eventWallNanos: Long? = null,
)

data class WakeBatch(
    val source: String,
    val observations: List<WakeObservation>,
    val gaps: List<String> = emptyList(),
    val subscriptions: Set<String> = emptySet(),
)

fun qualifiedWakeComponent(pkg: String?, name: String?): String? {
    if (name == null || pkg == null) return null
    val parts = name.removePrefix("ComponentInfo{").removePrefix("{").removeSuffix("}").split('/')
    if (parts.size > 2 || (parts.size == 2 && parts[0] != pkg)) return null
    val clazz = parts.last()
    if (clazz.isBlank()) return null
    return when {
        clazz.startsWith('.') -> pkg + clazz
        '.' in clazz -> clazz
        else -> "$pkg.$clazz"
    }
}
