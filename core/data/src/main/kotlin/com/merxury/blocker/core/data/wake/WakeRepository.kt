// Copyright 2026 Blocker
// SPDX-License-Identifier: Apache-2.0

package com.merxury.blocker.core.data.wake

import android.content.Context
import android.os.Process
import android.os.UserHandle
import com.merxury.blocker.core.controllers.wake.WakeAccess
import com.merxury.blocker.core.controllers.wake.WakeCapture
import com.merxury.blocker.core.controllers.wake.WakeCollector
import com.merxury.blocker.core.database.wake.WakeContinuation
import com.merxury.blocker.core.database.wake.WakeHistoryDao
import com.merxury.blocker.core.database.wake.WakeIdentity
import com.merxury.blocker.core.database.wake.WakeProtection
import com.merxury.blocker.core.database.wake.WakeRecord
import com.merxury.blocker.core.database.wake.WakeRelation
import com.merxury.blocker.core.database.wake.WakeSourceStatus
import com.merxury.blocker.core.model.wake.WakeBatch
import dagger.hilt.android.qualifiers.ApplicationContext
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

data class WakeImport(val boot: String, val identities: List<WakeIdentity>, val profiles: List<Int>)

@Singleton
class WakeRepository @Inject constructor(
    @param:ApplicationContext context: Context,
    private val history: WakeHistoryDao,
    private val index: WakeIdentityIndex,
    private val collector: WakeCollector,
) {
    private val preferences = context.getSharedPreferences("wake_protection", Context.MODE_PRIVATE)
    val isOwnerProfile get() = Process.myUserHandle() == UserHandle.getUserHandleForUid(0)

    // A fresh local journal can coexist with a managed firewall file from a previous install.
    val rulesPending get() = preferences.getBoolean("rules_pending", true)
    fun markRulesPending(value: Boolean) {
        check(preferences.edit().putBoolean("rules_pending", value).commit())
    }
    val lastDiagnostic get() = preferences.getLong("last_diagnostic", 0)

    suspend fun access() = collector.access()

    suspend fun attributionRecords(boot: String) = history.attributionWindow(boot, 0) + history.scheduledWork(boot, 0)

    suspend fun saveAttribution(records: List<WakeRecord>, relations: List<WakeRelation>, continuations: List<WakeContinuation>) = history.replaceRelations(records.map { it.id }, relations, continuations)

    suspend fun observedComponents(since: Long) = history.componentRequests(since)
    suspend fun protections() = history.readProtections()
    suspend fun saveProtections(paths: List<WakeProtection>) = history.putProtections(paths)
    suspend fun reportStatus(status: WakeSourceStatus) = history.status(status)

    suspend fun collect(diagnostic: Boolean, access: WakeAccess): WakeImport {
        val capture = collector.capture(diagnostic, access)
        val authorization = capture.batches.last { it.source == "authorization" }
        check(authorization.gaps.isEmpty()) { authorization.gaps.joinToString() }
        val local = index.installedPackages()
        val labels = index.labels(local, capture.packages, capture.wallMillis)
        val profileScope = when {
            capture.profiles == listOf(0) -> "OK"
            capture.profiles.isEmpty() -> "unknown users; automatic changes unavailable"
            else -> "multiple profiles; observe only"
        }
        history.status(WakeSourceStatus("profile scope", capture.wallMillis, profileScope))
        history.status(WakeSourceStatus("package index", capture.wallMillis, "indexing ${local.size} packages"))
        importBatches(capture.batches, capture, labels)
        val report = capture.batches.firstOrNull { it.source == "statsd" && it.subscriptions.isNotEmpty() }
        val lastDrain = preferences.getLong("last_drain", 0)
        if (report != null && (report.observations.size >= 300 || capture.wallMillis - lastDrain > 6L * 60 * 60 * 1000)) {
            try {
                val tail = collector.drain(capture.boot, access)
                val finish = capture.copy(nowElapsed = android.os.SystemClock.elapsedRealtimeNanos(), wallMillis = System.currentTimeMillis())
                importBatches(listOf(tail), finish, labels)
                history.status(WakeSourceStatus("statsd buffer", finish.wallMillis, "OK"))
                preferences.edit().putLong("last_drain", finish.wallMillis).apply()
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                history.status(WakeSourceStatus("statsd buffer", capture.wallMillis, e.message ?: "unavailable"))
            }
        }
        if (diagnostic && capture.profiles.isNotEmpty()) preferences.edit().putLong("last_diagnostic", capture.wallMillis).apply()
        val identities = index.refresh(local, capture.packages, capture.wallMillis)
        history.status(WakeSourceStatus("package index", System.currentTimeMillis(), "${identities.size} packages"))
        return WakeImport(capture.boot, identities, capture.profiles)
    }

    private suspend fun importBatches(batches: List<WakeBatch>, capture: WakeCapture, byUid: Map<Int, List<WakePackageLabel>>) {
        val cutoff = capture.wallMillis - 14L * 86400000
        val records = mutableListOf<WakeRecord>()
        for (batch in batches) {
            records += batch.observations.map { event ->
                val wall = capture.wallMillis - (capture.nowElapsed - event.elapsedNanos) / 1_000_000
                val json = JSONObject(event.attributes.toSortedMap()).toString()
                val fields = org.json.JSONArray(
                    listOf(
                        capture.boot, batch.source, event.kind, event.elapsedNanos, event.callerUid,
                        event.targetUid, event.callerPackage, event.targetPackage, event.component, event.action, event.startType,
                        event.pid, event.processName, json,
                    ),
                )
                val id = fields.toString().toByteArray().hexDigest()
                fun name(reported: String?, uid: Int?): String? = reported ?: byUid[uid]?.singleOrNull()?.takeIf { wall >= it.since }?.packageName
                WakeRecord(
                    id, capture.boot, event.elapsedNanos, wall, batch.source, event.kind, event.callerUid, event.targetUid,
                    name(event.callerPackage, event.callerUid), name(event.targetPackage, event.targetUid), event.component, event.action,
                    event.startType, event.pid, event.processName, json,
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
        }
        history.importRecords(records, cutoff)
    }
}
