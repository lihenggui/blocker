// Copyright 2026 Blocker
// SPDX-License-Identifier: Apache-2.0

package com.merxury.blocker.core.data.wake

import android.annotation.SuppressLint
import android.content.Context
import androidx.core.content.edit
import androidx.room.withTransaction
import com.merxury.blocker.core.database.wake.WakeDetectionDao
import com.merxury.blocker.core.database.wake.WakeHistoryDao
import com.merxury.blocker.core.database.wake.WakeHistoryDatabase
import com.merxury.blocker.core.database.wake.WakeStartInfoSeen
import com.merxury.blocker.core.model.component.UserId
import com.merxury.blocker.core.model.privileged.ChannelState
import com.merxury.blocker.core.model.privileged.PrivilegedIdentity
import com.merxury.blocker.core.model.wake.WakeBatch
import com.merxury.blocker.core.model.wake.WakeContinuation
import com.merxury.blocker.core.model.wake.WakeExemption
import com.merxury.blocker.core.model.wake.WakeFindingDay
import com.merxury.blocker.core.model.wake.WakeHoldAssociation
import com.merxury.blocker.core.model.wake.WakeHoldPeriod
import com.merxury.blocker.core.model.wake.WakeHoldProcess
import com.merxury.blocker.core.model.wake.WakeProtection
import com.merxury.blocker.core.model.wake.WakeRecord
import com.merxury.blocker.core.model.wake.WakeRelation
import com.merxury.blocker.core.model.wake.WakeSourceStatus
import com.merxury.blocker.core.model.wake.WakeVerdictDay
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
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

internal const val WAKE_HISTORY_RETENTION_MILLIS = 14L * 24 * 60 * 60 * 1_000

// Detection-only evidence writes report here instead of failing collection.
internal const val EVIDENCE_STATUS = "detection evidence"

// Written by versions that read evidence through su of their own.
private const val LEGACY_ROOT_GRANT = "previous_root_grant"

@Singleton
internal class LocalWakeRepository @Inject constructor(
    @param:ApplicationContext context: Context,
    private val history: WakeHistoryDao,
    private val index: WakeIdentityIndex,
    private val collector: WakeCollector,
    private val detection: WakeDetectionDao,
    private val channel: PrivilegedChannel,
    private val database: WakeHistoryDatabase,
) : WakeRepository {
    private val preferences = context.getSharedPreferences("wake_protection", Context.MODE_PRIVATE).apply {
        // Read access comes from the privileged channel now; drop the old root checker's flag once.
        if (contains(LEGACY_ROOT_GRANT)) edit { remove(LEGACY_ROOT_GRANT) }
    }
    override val isOwnerProfile get() = currentUserId() == UserId.SYSTEM

    // A fresh local journal can coexist with a managed firewall file from a previous install.
    override val rulesPending get() = preferences.getBoolean("rules_pending", true)

    // Reconciliation must stop before a system change if the pending marker cannot reach disk.
    @SuppressLint("UseKtx")
    override fun markRulesPending(value: Boolean) {
        check(preferences.edit().putBoolean("rules_pending", value).commit())
    }
    override val lastDiagnostic get() = preferences.getLong("last_diagnostic", 0)

    /**
     * The identity evidence is read as: the one the privileged channel the user chose is ready
     * as, acquired without a prompt, since background work must never show one. Any identity
     * reads; null when the channel is unavailable.
     */
    override suspend fun readAccess(): PrivilegedIdentity? = (channel.connectIfGranted() as? ChannelState.Ready)?.identity

    override suspend fun attributionRecords(boot: String) = (history.attributionWindow(boot, 0) + history.scheduledWork(boot, 0)).map { it.asModel() }

    override suspend fun saveAttribution(records: List<WakeRecord>, relations: List<WakeRelation>, continuations: List<WakeContinuation>) = history.replaceRelations(
        records.map { it.id },
        relations.map { it.asEntity() },
        continuations.map { it.asEntity() },
    )

    override suspend fun observedComponents(since: Long) = history.componentRequests(since).map { it.asModel() }
    override suspend fun resetFirewall(target: String?) = history.resetFirewall(target, System.currentTimeMillis())
    override suspend fun firewallResets() = history.firewallResets().associate { it.source.substringAfter('/') to it.checkedAt }
    override suspend fun protections() = history.readProtections().map { it.asModel() }
    override suspend fun saveProtections(paths: List<WakeProtection>) = history.putProtections(paths.map { it.asEntity() })

    override suspend fun reportStatus(status: WakeSourceStatus) = history.status(status.asEntity())

    override fun statuses(): Flow<List<WakeSourceStatus>> = history.statuses()
        .distinctUntilChanged()
        .map { rows -> rows.map { it.asModel() } }

    override fun retentionStart(now: Long) = now - WAKE_HISTORY_RETENTION_MILLIS

    override suspend fun oldestRecord() = detection.oldestRecord()

    override suspend fun oldestImported() = detection.oldestImported()

    override suspend fun detectionEvidence(since: Long, now: Long): WakeDetectionEvidence = database.withTransaction {
        WakeDetectionEvidence(
            records = detection.detectionRecords(since).map { it.asModel() },
            relations = detection.relations().map { it.asModel() },
            continuations = detection.continuations().map { it.asModel() },
            periods = detection.periods(since).map { it.asModel() },
            processes = detection.holdProcesses(since).map { it.asModel() },
            associations = detection.holdAssociations(since).map { it.asModel() },
            packages = history.identities(retentionStart(now)).groupBy({ it.uid }, { it.packageName }).mapValues { it.value.toSet() },
        )
    }

    override suspend fun saveDetection(from: Long, through: Long, findings: List<WakeFindingDay>, verdicts: List<WakeVerdictDay>, firstKeptDay: Long) = detection.replaceDetection(
        from,
        through,
        findings.map { it.asEntity() },
        verdicts.map { it.asEntity() },
        firstKeptDay,
    )

    override fun hasFindings(): Flow<Boolean> = detection.hasFindings().distinctUntilChanged()

    override fun findingsInvolving(packageName: String, fromDay: Long): Flow<List<WakeFindingDay>> = detection.findingsInvolving(packageName, fromDay)
        .distinctUntilChanged()
        .map { rows -> rows.map { it.asModel() } }

    override fun findingsSince(fromDay: Long): Flow<List<WakeFindingDay>> = detection.findingsSince(fromDay)
        .distinctUntilChanged()
        .map { rows -> rows.map { it.asModel() } }

    override fun verdictsSince(fromDay: Long): Flow<List<WakeVerdictDay>> = detection.verdictsSince(fromDay)
        .distinctUntilChanged()
        .map { rows -> rows.map { it.asModel() } }

    override fun observeExemptions(targets: List<String>): Flow<List<WakeExemption>> = observeTargets(targets, detection::observeExemptions)
        .distinctUntilChanged()
        .map { rows -> rows.map { it.asModel() } }

    override fun observeSpecialBindings(targets: List<String>): Flow<List<WakeRecord>> = observeTargets(targets, detection::observeSpecialBindings).map { bindings ->
        // Global dumps such as device policy can include secondary profiles in the owner source.
        bindings.filter { runCatching { JSONObject(it.attributes).optString("user").toIntOrNull() }.getOrNull() == 0 }
            .map { it.asModel() }
    }.distinctUntilChanged()

    private inline fun <reified T> observeTargets(targets: List<String>, query: (List<String>) -> Flow<List<T>>): Flow<List<T>> {
        val queries = targets.distinct().chunked(900).map(query)
        return when (queries.size) {
            0 -> flowOf(emptyList())
            1 -> queries.single()
            else -> combine(queries) { it.asList().flatten() }
        }
    }

    /** Collects and stores evidence read as [identity]; sources that cannot be read record why and the rest is stored. */
    override suspend fun collect(diagnostic: Boolean, identity: PrivilegedIdentity): WakeImport {
        // Detection evidence never decides whether collection or enforcement runs.
        val capture = collector.capture(diagnostic, identity) { boot ->
            evidence(System.currentTimeMillis()) { WakeResume(detection.latestEvent(boot), detection.latestPeriod()) } ?: WakeResume(null, null)
        }
        val local = index.installedPackages()
        val labels = index.labels(local, capture.packages, capture.wallMillis)
        val readSources = capture.batches.filter { it.gaps.isEmpty() }.mapTo(mutableSetOf()) { it.source }
        val secondaryPackagesAvailable = capture.profiles.all { it == 0 || "PACKAGES/$it" in readSources }
        val profileScope = when {
            0 !in capture.profiles -> "owner profile unavailable"
            !secondaryPackagesAvailable -> "secondary profile packages unavailable; automatic firewall rules unavailable"
            else -> "OK"
        }
        reportStatus(WakeSourceStatus("profile scope", capture.wallMillis, profileScope))
        reportStatus(WakeSourceStatus("package index", capture.wallMillis, "indexing ${local.size} packages"))
        val (startInfo, batches) = capture.batches.partition { it.source == WAKE_START_INFO_SOURCE }
        importBatches(batches, capture, labels)
        val saved = evidence(capture.wallMillis) {
            for (batch in startInfo) importStartInfo(batch, capture, labels)
            for (period in capture.procstats) savePeriod(period, capture.wallMillis)
            capture.exemptions?.let { saveExemptions(it, capture.wallMillis) }
            detection.expireEvidence(retentionStart(capture.wallMillis))
        }
        if (saved != null) reportStatus(WakeSourceStatus(EVIDENCE_STATUS, capture.wallMillis, "OK"))
        val report = capture.batches.firstOrNull { it.source == "statsd" && it.subscriptions.isNotEmpty() }
        val lastDrain = preferences.getLong("last_drain", 0)
        if (report != null && (report.observations.size >= 300 || capture.wallMillis - lastDrain > 6L * 60 * 60 * 1000)) {
            try {
                val tail = collector.drain(capture.boot, identity)
                val finish = capture.copy(nowElapsed = android.os.SystemClock.elapsedRealtimeNanos(), wallMillis = System.currentTimeMillis())
                importBatches(listOf(tail), finish, labels)
                reportStatus(WakeSourceStatus("statsd buffer", finish.wallMillis, "OK"))
                preferences.edit { putLong("last_drain", finish.wallMillis) }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                reportStatus(WakeSourceStatus("statsd buffer", capture.wallMillis, e.message ?: "unavailable"))
            }
        }
        if (diagnostic && capture.profiles.isNotEmpty()) preferences.edit { putLong("last_diagnostic", capture.wallMillis) }
        val identities = index.refresh(local, capture.packages, capture.wallMillis)
        reportStatus(WakeSourceStatus("package index", System.currentTimeMillis(), "${identities.size} packages"))
        return WakeImport(capture.boot, identities, capture.profiles, capture.channelUnavailable, capture.everyReadRefused, secondaryPackagesAvailable)
    }

    /** Runs a detection-only write. A failure is reported as a detection status and never propagates. */
    private suspend fun <T> evidence(now: Long, block: suspend () -> T): T? = try {
        block()
    } catch (e: Exception) {
        if (e is kotlinx.coroutines.CancellationException) throw e
        try {
            reportStatus(WakeSourceStatus(EVIDENCE_STATUS, now, e.message ?: e.javaClass.simpleName))
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
        history.importStartInfo(records.map { it.asEntity() }, fresh.map { WakeStartInfoSeen(it, capture.wallMillis) }, capture.wallMillis - WAKE_HISTORY_RETENTION_MILLIS)
    }

    private suspend fun savePeriod(period: WakeProcStatsPeriod, now: Long) = detection.replacePeriod(
        WakeHoldPeriod(period.id, period.startWallMillis, period.endWallMillis, now).asEntity(),
        period.holds.map { WakeHoldProcess(period.id, it.target, it.uid, it.process, it.screenOffMillis, it.screenOnMillis, it.fgsScreenOffMillis, it.fgsScreenOnMillis).asEntity() },
        period.associations.map { WakeHoldAssociation(period.id, it.target, it.uid, it.component, it.sourceProcess, it.sourceUid, it.boundMillis, it.activeMillis).asEntity() },
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
                ).asEntity()
            },
        )
    }

    private suspend fun importBatches(batches: List<WakeBatch>, capture: WakeCapture, byUid: Map<Int, List<WakePackageLabel>>) {
        history.importRecords(batches.flatMap { records(it, capture, byUid) }.map { it.asEntity() }, capture.wallMillis - WAKE_HISTORY_RETENTION_MILLIS)
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
                reportStatus(
                    WakeSourceStatus(
                        name,
                        capture.wallMillis,
                        if (name in counts) "report samples: ${counts[name]}" else "subscribed; no sample in this report",
                    ),
                )
            }
        }
        reportStatus(WakeSourceStatus(batch.source, capture.wallMillis, batch.gaps.joinToString("; ").ifBlank { "OK" }))
        return records
    }
}
