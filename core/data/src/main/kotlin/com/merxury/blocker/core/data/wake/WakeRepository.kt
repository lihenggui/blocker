// Copyright 2026 Blocker
// SPDX-License-Identifier: Apache-2.0

package com.merxury.blocker.core.data.wake

import com.merxury.blocker.core.model.privileged.PrivilegedIdentity
import com.merxury.blocker.core.model.wake.WakeContinuation
import com.merxury.blocker.core.model.wake.WakeExemption
import com.merxury.blocker.core.model.wake.WakeFindingDay
import com.merxury.blocker.core.model.wake.WakeHoldAssociation
import com.merxury.blocker.core.model.wake.WakeHoldPeriod
import com.merxury.blocker.core.model.wake.WakeHoldProcess
import com.merxury.blocker.core.model.wake.WakeIdentity
import com.merxury.blocker.core.model.wake.WakeProtection
import com.merxury.blocker.core.model.wake.WakeRecord
import com.merxury.blocker.core.model.wake.WakeRelation
import com.merxury.blocker.core.model.wake.WakeSourceStatus
import com.merxury.blocker.core.model.wake.WakeVerdictDay
import kotlinx.coroutines.flow.Flow

/**
 * One imported collection.
 *
 * @param channelUnavailable whether the privileged channel could not run some read; the sources
 * it missed recorded why
 * @param everyReadRefused whether the channel refused every read, so nothing was read
 */
data class WakeImport(
    val boot: String,
    val identities: List<WakeIdentity>,
    val profiles: List<Int>,
    val channelUnavailable: Boolean = false,
    val everyReadRefused: Boolean = false,
    val secondaryPackagesAvailable: Boolean = true,
)

/** Stored evidence for detection. [packages] maps each uid to the packages installed under it. */
data class WakeDetectionEvidence(
    val records: List<WakeRecord>,
    val relations: List<WakeRelation>,
    val continuations: List<WakeContinuation>,
    val periods: List<WakeHoldPeriod>,
    val processes: List<WakeHoldProcess>,
    val associations: List<WakeHoldAssociation>,
    val packages: Map<Int, Set<String>>,
)

interface WakeRepository {
    val isOwnerProfile: Boolean
    val rulesPending: Boolean
    val lastDiagnostic: Long

    fun markRulesPending(value: Boolean)

    /** Quietly reads the chosen channel's identity, or returns null when it is unavailable. */
    suspend fun readAccess(): PrivilegedIdentity?

    suspend fun attributionRecords(boot: String): List<WakeRecord>

    suspend fun saveAttribution(records: List<WakeRecord>, relations: List<WakeRelation>, continuations: List<WakeContinuation>)

    suspend fun observedComponents(since: Long): List<WakeRecord>

    suspend fun resetFirewall(target: String?)

    suspend fun firewallResets(): Map<String, Long>

    suspend fun protections(): List<WakeProtection>

    suspend fun saveProtections(paths: List<WakeProtection>)

    suspend fun reportStatus(status: WakeSourceStatus)

    fun statuses(): Flow<List<WakeSourceStatus>>

    fun retentionStart(now: Long): Long

    suspend fun oldestRecord(): Long?

    suspend fun oldestImported(): Long?

    suspend fun detectionEvidence(since: Long, now: Long): WakeDetectionEvidence

    suspend fun saveDetection(from: Long, through: Long, findings: List<WakeFindingDay>, verdicts: List<WakeVerdictDay>, firstKeptDay: Long)

    fun hasFindings(): Flow<Boolean>

    fun findingsInvolving(packageName: String, fromDay: Long): Flow<List<WakeFindingDay>>

    fun findingsSince(fromDay: Long): Flow<List<WakeFindingDay>>

    fun verdictsSince(fromDay: Long): Flow<List<WakeVerdictDay>>

    fun observeExemptions(targets: List<String>): Flow<List<WakeExemption>>

    fun observeSpecialBindings(targets: List<String>): Flow<List<WakeRecord>>

    /** Stores readable evidence and reports the sources that could not be read. */
    suspend fun collect(diagnostic: Boolean, identity: PrivilegedIdentity): WakeImport
}
