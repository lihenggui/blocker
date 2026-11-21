// Copyright 2026 Blocker
// SPDX-License-Identifier: Apache-2.0

package com.merxury.blocker.core.model.wake

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
