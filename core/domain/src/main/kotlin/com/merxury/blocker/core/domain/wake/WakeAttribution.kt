// Copyright 2026 Blocker
// SPDX-License-Identifier: Apache-2.0

package com.merxury.blocker.core.domain.wake

import com.merxury.blocker.core.database.wake.WakeContinuation
import com.merxury.blocker.core.database.wake.WakeRecord
import com.merxury.blocker.core.database.wake.WakeRelation
import com.merxury.blocker.core.model.wake.SYSTEM_WAKE_EVENTS
import com.merxury.blocker.core.model.wake.qualifiedWakeComponent
import org.json.JSONObject

internal object WakeAttribution {
    private const val WINDOW = 5_000_000_000L

    // Find the first included timestamp even when several records share an endpoint.
    private inline fun <T> List<T>.within(from: Long, through: Long, crossinline timestamp: (T) -> Long): Sequence<T> {
        var low = 0
        var high = size
        while (low < high) {
            val middle = (low + high).ushr(1)
            if (timestamp(this[middle]) < from) low = middle + 1 else high = middle
        }
        return subList(low, size).asSequence().takeWhile { timestamp(it) <= through }
    }

    fun relationships(records: List<WakeRecord>): List<WakeRelation> {
        val links = mutableListOf<WakeRelation>()
        for (group in records.groupBy { it.boot to it.targetUid }.values) {
            val starts = group.filter { it.kind == "ProcessStartTime" && it.pid != null && it.targetUid != null }
            val requests = group.filter { it.kind != "ProcessStartTime" && it.startType == 3 }.sortedBy { it.elapsedNanos }
            for (start in starts) {
                val fields = JSONObject(start.attributes)
                val elapsed = actualStart(start, fields)
                val adjacent = requests.within(elapsed - WINDOW, elapsed + WINDOW) { it.elapsedNanos }
                val matches = adjacent.filter { request ->
                    if (request.callerUid != null && start.callerUid != null && request.callerUid != start.callerUid) return@filter false
                    val sameCaller = request.callerUid != null && request.callerUid == start.callerUid
                    val sameComponent = qualifiedWakeComponent(request.targetPackage, request.component)?.let { name ->
                        name == qualifiedWakeComponent(request.targetPackage, start.component)
                    } == true
                    when (request.kind) {
                        "ServiceRequestEventReported" -> fields.optInt("hosting_type_id") in setOf(11, 15, 16) && sameComponent
                        "AppStartOccurred" -> fields.optInt("hosting_type_id") == 1 && sameComponent
                        "ProviderAcquisitionEventReported" -> fields.optInt("hosting_type_id") == 5 && sameCaller
                        "BroadcastDeliveryEventReported" -> fields.optInt("hosting_type_id") == 4 && sameCaller && request.action != null && request.action == start.action
                        else -> false
                    }
                }
                val request = matches.singleOrNull() ?: continue
                links += WakeRelation(request.id, start.id, if (start.callerUid != null && start.callerUid == request.callerUid) "MATCHING_CALLER" else "MATCHING_COMPONENT")
            }
        }
        links += scheduledWork(records)
        return links.groupBy { it.requestId }.values.mapNotNull { it.singleOrNull() }
    }

    private data class Launch(val request: WakeRecord, val start: WakeRecord, val time: Long)

    // A continuation describes two observed cold starts in sequence, not the intent of the apps.
    fun continuations(records: List<WakeRecord>, relations: List<WakeRelation>): List<WakeContinuation> {
        val byId = records.associateBy { it.id }
        val statsStarts = records.filter { it.kind == "ProcessStartTime" }
        val startTimes = statsStarts.associate { it.id to actualStart(it) }
        val launches = relations.mapNotNull { link ->
            val request = byId[link.requestId] ?: return@mapNotNull null
            val start = byId[link.processId] ?: return@mapNotNull null
            if (start.kind != "ProcessStartTime" || request.callerUid == null || request.targetUid == null ||
                request.callerUid == request.targetUid
            ) {
                return@mapNotNull null
            }
            Launch(request, start, startTimes.getValue(start.id))
        }
        val system = records.filter { it.source == SYSTEM_WAKE_EVENTS }
        val starts = system.filter { it.kind == "am_proc_start" && it.pid != null && it.targetUid != null }
        val byPid = starts.groupBy { it.boot to it.pid }
        val statsByPid = statsStarts.groupBy { it.boot to it.pid }
        val byProcess = starts.groupBy { Triple(it.boot, it.targetUid, it.processName) }
        val deaths = system.filter { it.kind == "am_proc_died" }.groupBy { it.boot to it.pid }
        val exits = records.filter { it.source == "statsd" && it.kind == "AppProcessDied" }
            .groupBy { Triple(it.boot, it.targetUid, it.processName) }
        val incoming = launches.sortedBy { it.time }.groupBy { it.request.boot to it.request.targetUid }
        return launches.mapNotNull { (request, start, time) ->
            if (request.elapsedNanos > time + WINDOW) return@mapNotNull null
            val fields = JSONObject(request.attributes)
            val reported = JSONObject(start.attributes).optString("caller_process_name")
            val candidates = incoming[request.boot to request.callerUid].orEmpty()
                .within(time - WINDOW, minOf(time - 1, request.elapsedNanos)) { it.time }
            val prior = candidates.singleOrNull { (_, middle, firstTime) ->
                val pidKey = middle.boot to middle.pid
                val instances = byPid[pidKey].orEmpty()
                val witness = instances.singleOrNull { it.targetUid == middle.targetUid && it.elapsedNanos in (firstTime - WINDOW)..minOf(firstTime + WINDOW, request.elapsedNanos) }
                    ?: return@singleOrNull false
                val process = witness.processName?.takeIf { it.isNotEmpty() } ?: return@singleOrNull false
                if (!matchesCaller(request, fields, reported, process)) return@singleOrNull false
                val lifetime = witness.elapsedNanos..request.elapsedNanos
                val key = Triple(middle.boot, middle.targetUid, process)
                deaths[pidKey].orEmpty().none { it.elapsedNanos in lifetime } &&
                    instances.none { it.id != witness.id && it.elapsedNanos in lifetime } &&
                    statsByPid[pidKey].orEmpty().none { it.id != middle.id && startTimes.getValue(it.id) in firstTime..request.elapsedNanos } &&
                    exits[key].orEmpty().none { it.elapsedNanos in lifetime } &&
                    byProcess[key].orEmpty().none { it.id != witness.id && it.elapsedNanos in lifetime }
            } ?: return@mapNotNull null
            WakeContinuation(request.id, prior.request.id)
        }
    }

    private fun matchesCaller(request: WakeRecord, fields: JSONObject, reported: String, process: String): Boolean {
        if (reported.isNotEmpty() && reported != process) return false
        val short = fields.optString("request_process_name")
        if (short == process || (short.isEmpty() && reported == process)) return true
        if (request.kind != "ServiceRequestEventReported") return false
        val pkg = fields.optString("unique_caller_package")
        if (pkg.isEmpty() || pkg != request.callerPackage || !process.startsWith(pkg) || process.removePrefix(pkg) != short) return false
        // A default-process start may also omit the name when the caller record is unavailable.
        val state = fields.optInt("requester_process_state", -1)
        return short.isNotEmpty() || fields.optInt("request_type") == 2 || state in 0..19 || state in 1000..1019
    }

    private fun actualStart(start: WakeRecord, fields: JSONObject = JSONObject(start.attributes)): Long = fields.optLong("process_start_time_millis", -1)
        .takeIf { it >= 0 }?.times(1_000_000) ?: start.elapsedNanos

    private fun scheduledWork(records: List<WakeRecord>): List<WakeRelation> {
        val links = mutableListOf<WakeRelation>()
        val scheduled = mutableMapOf<List<String>, WakeRecord>()
        val snapshots = records.filter { it.kind == "PendingIntentRegistration" }.groupBy {
            it.boot to JSONObject(it.attributes).optString("record")
        }
        for (event in records.sortedBy { it.elapsedNanos }) {
            if (event.kind == "PendingAlarm") {
                val fields = JSONObject(event.attributes)
                val token = fields.optString("record").takeIf { it.isNotEmpty() } ?: continue
                // Binder record identifiers are ephemeral. Only join the same collection snapshot.
                val registration = snapshots[event.boot to token]?.singleOrNull {
                    it.elapsedNanos == event.elapsedNanos && it.callerPackage == fields.optString("creator_package")
                } ?: continue
                links += WakeRelation(event.id, registration.id, "PENDING_INTENT_REGISTRATION")
            }
            if (event.kind != "ScheduledJobStateChanged") continue
            val fields = JSONObject(event.attributes)
            val uid = fields.optString("attribution_node").takeIf { it.isNotEmpty() } ?: continue
            val job = fields.optString("job_id").takeIf { it.isNotEmpty() } ?: continue
            val namespace = fields.optString("namespace_hash")
            val key = listOf(event.boot, uid, namespace, job, fields.optString("job_name"))
            when (fields.optInt("state", -1)) {
                2 -> scheduled[key] = event
                3 -> scheduled.remove(key)
                1 -> scheduled[key]?.let { links += WakeRelation(event.id, it.id, "SCHEDULED_JOB") }
            }
        }
        return links
    }
}
