// Copyright 2026 Blocker
// SPDX-License-Identifier: Apache-2.0

package com.merxury.blocker.core.data.wake

import android.annotation.SuppressLint
import android.content.Context
import androidx.core.content.edit
import com.merxury.blocker.core.database.wake.WakeContinuation
import com.merxury.blocker.core.database.wake.WakeDetectionDao
import com.merxury.blocker.core.database.wake.WakeExemption
import com.merxury.blocker.core.database.wake.WakeFindingDay
import com.merxury.blocker.core.database.wake.WakeHistoryDao
import com.merxury.blocker.core.database.wake.WakeHoldAssociation
import com.merxury.blocker.core.database.wake.WakeHoldPeriod
import com.merxury.blocker.core.database.wake.WakeHoldProcess
import com.merxury.blocker.core.database.wake.WakeIdentity
import com.merxury.blocker.core.database.wake.WakeProtection
import com.merxury.blocker.core.database.wake.WakeRecord
import com.merxury.blocker.core.database.wake.WakeRelation
import com.merxury.blocker.core.database.wake.WakeSourceStatus
import com.merxury.blocker.core.database.wake.WakeStartInfoSeen
import com.merxury.blocker.core.database.wake.WakeVerdictDay
import com.merxury.blocker.core.model.component.UserId
import com.merxury.blocker.core.model.privileged.ChannelState
import com.merxury.blocker.core.model.privileged.PrivilegedIdentity
import com.merxury.blocker.core.model.wake.WakeBatch
import com.merxury.blocker.core.privileged.PrivilegedChannel
import com.merxury.blocker.core.privileged.wake.WAKE_START_INFO_SOURCE
import com.merxury.blocker.core.privileged.wake.WakeCapture
import com.merxury.blocker.core.privileged.wake.WakeCollector
import com.merxury.blocker.core.privileged.wake.WakeExemptionSnapshot
import com.merxury.blocker.core.privileged.wake.WakeProcStatsPeriod
import com.merxury.blocker.core.privileged.wake.WakeResume
import com.merxury.blocker.core.utils.currentUserId
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

internal const val WAKE_HISTORY_RETENTION_MILLIS = 14L * 24 * 60 * 60 * 1_000

// Detection-only evidence writes report here instead of failing collection.
internal const val EVIDENCE_STATUS = "detection evidence"

// Written by versions that read evidence through su of their own.
private const val LEGACY_ROOT_GRANT = "previous_root_grant"

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

@Singleton
class WakeRepository @Inject constructor(
    @param:ApplicationContext context: Context,
    private val history: WakeHistoryDao,
    private val index: WakeIdentityIndex,
    private val collector: WakeCollector,
    private val detection: WakeDetectionDao,
    private val channel: PrivilegedChannel,
) {
    private val preferences = context.getSharedPreferences("wake_protection", Context.MODE_PRIVATE).apply {
        // Read access comes from the privileged channel now; drop the old root checker's flag once.
        if (contains(LEGACY_ROOT_GRANT)) edit { remove(LEGACY_ROOT_GRANT) }
    }
    val isOwnerProfile get() = currentUserId() == UserId.SYSTEM

    // A fresh local journal can coexist with a managed firewall file from a previous install.
    val rulesPending get() = preferences.getBoolean("rules_pending", true)

    // Reconciliation must stop before a system change if the pending marker cannot reach disk.
    @SuppressLint("UseKtx")
    fun markRulesPending(value: Boolean) {
        check(preferences.edit().putBoolean("rules_pending", value).commit())
    }
    val lastDiagnostic get() = preferences.getLong("last_diagnostic", 0)

    /**
     * The identity evidence is read as: the one the privileged channel the user chose is ready
     * as, acquired without a prompt, since background work must never show one. Any identity
     * reads; null when the channel is unavailable.
     */
    suspend fun readAccess(): PrivilegedIdentity? = (channel.connectIfGranted() as? ChannelState.Ready)?.identity

    suspend fun attributionRecords(boot: String) = history.attributionWindow(boot, 0) + history.scheduledWork(boot, 0)

    suspend fun saveAttribution(records: List<WakeRecord>, relations: List<WakeRelation>, continuations: List<WakeContinuation>) = history.replaceRelations(records.map { it.id }, relations, continuations)

    suspend fun observedComponents(since: Long) = history.componentRequests(since)
    suspend fun resetFirewall(target: String?) = history.resetFirewall(target, System.currentTimeMillis())
    suspend fun firewallResets() = history.firewallResets().associate { it.source.substringAfter('/') to it.checkedAt }
    suspend fun protections() = history.readProtections()
    suspend fun saveProtections(paths: List<WakeProtection>) = history.putProtections(paths)

    suspend fun reportStatus(status: WakeSourceStatus) = history.status(status)

    fun retentionStart(now: Long) = now - WAKE_HISTORY_RETENTION_MILLIS

    suspend fun oldestRecord() = detection.oldestRecord()

    suspend fun oldestImported() = detection.oldestImported()

    suspend fun detectionEvidence(since: Long, now: Long): WakeDetectionEvidence = WakeDetectionEvidence(
        detection.detectionRecords(since),
        detection.relations(),
        detection.continuations(),
        detection.periods(since),
        detection.holdProcesses(since),
        detection.holdAssociations(since),
        history.identities(retentionStart(now)).groupBy({ it.uid }, { it.packageName }).mapValues { it.value.toSet() },
    )

    suspend fun saveDetection(from: Long, through: Long, findings: List<WakeFindingDay>, verdicts: List<WakeVerdictDay>, firstKeptDay: Long) = detection.replaceDetection(from, through, findings, verdicts, firstKeptDay)

    /** Daily verdicts, newest day first. */
    fun verdicts(): Flow<List<WakeVerdictDay>> = detection.verdicts()

    /** Daily findings of one package, newest day first. */
    fun findings(target: String): Flow<List<WakeFindingDay>> = detection.findings(target)

    /** Collects and stores evidence read as [identity]; sources that cannot be read record why and the rest is stored. */
    suspend fun collect(diagnostic: Boolean, identity: PrivilegedIdentity): WakeImport {
        // Detection evidence never decides whether collection or enforcement runs.
        val capture = collector.capture(diagnostic, identity) { boot ->
            evidence(System.currentTimeMillis()) { WakeResume(detection.latestEvent(boot), detection.latestPeriod()) } ?: WakeResume(null, null)
        }
        val local = index.installedPackages()
        val labels = index.labels(local, capture.packages, capture.wallMillis)
        val profileScope = when {
            capture.profiles == listOf(0) -> "OK"
            capture.profiles.isEmpty() -> "unknown users; automatic changes unavailable"
            else -> "multiple profiles; observe only"
        }
        history.status(WakeSourceStatus("profile scope", capture.wallMillis, profileScope))
        history.status(WakeSourceStatus("package index", capture.wallMillis, "indexing ${local.size} packages"))
        val (startInfo, batches) = capture.batches.partition { it.source == WAKE_START_INFO_SOURCE }
        importBatches(batches, capture, labels)
        val saved = evidence(capture.wallMillis) {
            for (batch in startInfo) importStartInfo(batch, capture, labels)
            for (period in capture.procstats) savePeriod(period, capture.wallMillis)
            capture.exemptions?.let { saveExemptions(it, capture.wallMillis) }
            detection.expireEvidence(retentionStart(capture.wallMillis))
        }
        if (saved != null) history.status(WakeSourceStatus(EVIDENCE_STATUS, capture.wallMillis, "OK"))
        val report = capture.batches.firstOrNull { it.source == "statsd" && it.subscriptions.isNotEmpty() }
        val lastDrain = preferences.getLong("last_drain", 0)
        if (report != null && (report.observations.size >= 300 || capture.wallMillis - lastDrain > 6L * 60 * 60 * 1000)) {
            try {
                val tail = collector.drain(capture.boot, identity)
                val finish = capture.copy(nowElapsed = android.os.SystemClock.elapsedRealtimeNanos(), wallMillis = System.currentTimeMillis())
                importBatches(listOf(tail), finish, labels)
                history.status(WakeSourceStatus("statsd buffer", finish.wallMillis, "OK"))
                preferences.edit { putLong("last_drain", finish.wallMillis) }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                history.status(WakeSourceStatus("statsd buffer", capture.wallMillis, e.message ?: "unavailable"))
            }
        }
        if (diagnostic && capture.profiles.isNotEmpty()) preferences.edit { putLong("last_diagnostic", capture.wallMillis) }
        val identities = index.refresh(local, capture.packages, capture.wallMillis)
        history.status(WakeSourceStatus("package index", System.currentTimeMillis(), "${identities.size} packages"))
        return WakeImport(capture.boot, identities, capture.profiles, capture.channelUnavailable, capture.everyReadRefused)
    }

    /** Runs a detection-only write. A failure is reported as a detection status and never propagates. */
    private suspend fun <T> evidence(now: Long, block: suspend () -> T): T? = try {
        block()
    } catch (e: Exception) {
        if (e is kotlinx.coroutines.CancellationException) throw e
        try {
            history.status(WakeSourceStatus(EVIDENCE_STATUS, now, e.message ?: e.javaClass.simpleName))
        } catch (reporting: Exception) {
            if (reporting is kotlinx.coroutines.CancellationException) throw reporting
        }
        null
    }

    /** Imports start-info records of this boot not imported before, and marks them seen in the same transaction. */
    private suspend fun importStartInfo(batch: WakeBatch, capture: WakeCapture, byUid: Map<Int, List<WakePackageLabel>>) {
        val fresh = detection.unseen(batch.observations.mapNotNull { it.attributes["signature"] })
        val accepted = batch.observations.filter { it.attributes["signature"] in fresh }
        val records = records(batch.copy(observations = accepted.map { it.copy(attributes = it.attributes - "signature") }), capture, byUid)
        history.importStartInfo(records, fresh.map { WakeStartInfoSeen(it, capture.wallMillis) }, capture.wallMillis - WAKE_HISTORY_RETENTION_MILLIS)
    }

    private suspend fun savePeriod(period: WakeProcStatsPeriod, now: Long) = detection.replacePeriod(
        WakeHoldPeriod(period.id, period.startWallMillis, period.endWallMillis, now),
        period.holds.map { WakeHoldProcess(period.id, it.target, it.uid, it.process, it.screenOffMillis, it.screenOnMillis, it.fgsScreenOffMillis, it.fgsScreenOnMillis) },
        period.associations.map { WakeHoldAssociation(period.id, it.target, it.uid, it.component, it.sourceProcess, it.sourceUid, it.boundMillis, it.activeMillis) },
    )

    private suspend fun saveExemptions(snapshot: WakeExemptionSnapshot, now: Long) {
        val packages = snapshot.idleWhitelist.orEmpty().keys + snapshot.standbyBuckets.orEmpty().keys + snapshot.backgroundRestricted.orEmpty()
        detection.replaceExemptions(
            packages.map { pkg ->
                WakeExemption(
                    pkg,
                    // Empty when the list was read and the package is not on it.
                    snapshot.idleWhitelist?.let { it[pkg]?.sorted()?.joinToString(",").orEmpty() },
                    snapshot.standbyBuckets?.get(pkg),
                    snapshot.backgroundRestricted?.let { pkg in it },
                    now,
                )
            },
        )
    }

    private suspend fun importBatches(batches: List<WakeBatch>, capture: WakeCapture, byUid: Map<Int, List<WakePackageLabel>>) {
        history.importRecords(batches.flatMap { records(it, capture, byUid) }, capture.wallMillis - WAKE_HISTORY_RETENTION_MILLIS)
    }

    /** Converts one batch into records and reports the state of its source. */
    private suspend fun records(batch: WakeBatch, capture: WakeCapture, byUid: Map<Int, List<WakePackageLabel>>): List<WakeRecord> {
        val records = batch.observations.map { event ->
            val wall = event.eventWallNanos?.div(1_000_000) ?: (capture.wallMillis - (capture.nowElapsed - event.elapsedNanos) / 1_000_000)
            val json = JSONObject(event.attributes.toSortedMap()).toString()
            val fields = org.json.JSONArray(
                listOf(
                    capture.boot, batch.source, event.kind, event.eventWallNanos ?: event.elapsedNanos, event.callerUid,
                    event.targetUid, event.callerPackage, event.targetPackage, event.component, event.action, event.startType,
                    event.pid, event.processName, json,
                ),
            )
            val id = fields.toString().toByteArray().hexDigest()
            fun name(reported: String?, uid: Int?): String? = reported ?: byUid[uid]?.singleOrNull()?.takeIf { wall >= it.since }?.packageName
            val attributes = JSONObject(json)
            if (event.kind == "ServiceRequestEventReported") {
                name(null, event.callerUid)?.let { attributes.put("unique_caller_package", it) }
            }
            WakeRecord(
                id, capture.boot, event.elapsedNanos, wall, batch.source, event.kind, event.callerUid, event.targetUid,
                name(event.callerPackage, event.callerUid), name(event.targetPackage, event.targetUid), event.component, event.action,
                event.startType, event.pid, event.processName, attributes.toString(),
            )
        }
        if (batch.subscriptions.isNotEmpty()) {
            val counts = batch.observations.groupingBy { it.kind }.eachCount()
            for (name in batch.subscriptions) {
                history.status(
                    WakeSourceStatus(
                        name,
                        capture.wallMillis,
                        if (name in counts) "report samples: ${counts[name]}" else "subscribed; no sample in this report",
                    ),
                )
            }
        }
        history.status(WakeSourceStatus(batch.source, capture.wallMillis, batch.gaps.joinToString("; ").ifBlank { "OK" }))
        return records
    }
}
