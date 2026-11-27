// Copyright 2026 Blocker
// SPDX-License-Identifier: Apache-2.0

package com.merxury.blocker.core.model.wake

data class WakeRecord(
    val id: String,
    val boot: String,
    val elapsedNanos: Long,
    val wallMillis: Long,
    val source: String,
    val kind: String,
    val callerUid: Int?,
    val targetUid: Int?,
    val callerPackage: String?,
    val targetPackage: String?,
    val component: String?,
    val action: String?,
    val startType: Int?,
    val pid: Int?,
    val processName: String?,
    val attributes: String,
)

data class WakeRelation(
    val requestId: String,
    val processId: String,
    val evidence: String,
)

data class WakeContinuation(val outgoing: String, val incoming: String)

data class WakeSourceStatus(val source: String, val checkedAt: Long, val status: String)

data class WakeIdentity(
    val uid: Int,
    val packageName: String,
    val revision: String,
    val versionCode: Long,
    val signatures: String,
    val components: String,
    val firstSeen: Long,
    val lastSeen: Long,
)
