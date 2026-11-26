// Copyright 2026 Blocker
// SPDX-License-Identifier: Apache-2.0

package com.merxury.blocker.core.domain.wake

import com.merxury.blocker.core.data.wake.WakeDetectionEvidence
import com.merxury.blocker.core.database.wake.WakeRecord
import com.merxury.blocker.core.model.component.appIdOfUid
import com.merxury.blocker.core.model.component.isRegularAppUid
import com.merxury.blocker.core.model.component.userOfUid
import com.merxury.blocker.core.model.wake.Confidence
import com.merxury.blocker.core.model.wake.ReviveTrigger
import com.merxury.blocker.core.model.wake.SYSTEM_WAKE_EVENTS
import com.merxury.blocker.core.model.wake.qualifiedWakeComponent
import com.merxury.blocker.core.privileged.wake.WAKE_AUDIO_PLAYBACK
import com.merxury.blocker.core.privileged.wake.WAKE_NOTIFICATIONS_OFF
import com.merxury.blocker.core.privileged.wake.WAKE_OVERLAY_WINDOWS
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.math.abs

private const val COMPONENTLESS_BAL_WINDOW_NS = 2_000_000_000L

private const val MAX_ALARM_SNAPSHOT_AGE_NS = 60L * 60 * 1_000_000_000L

internal fun isApp(uid: Int?) = uid != null && appIdOfUid(uid) >= 10_000

internal fun String?.isAppPackage(): Boolean = this != null && isNotBlank() && this != "android" && !all(Char::isDigit) && toLongOrNull() == null

/** Push services whose deliveries wake their clients. Shared services count only for known push actions. */
internal object WakePushTransports {
    private val transports = mapOf(
        "com.google.android.gms" to setOf("com.google.android.c2dm.intent.RECEIVE"),
        "com.xiaomi.xmsf" to null,
        "com.heytap.mcs" to null,
        "com.vivo.pushservice" to null,
        "com.huawei.android.pushagent" to null,
        "com.huawei.hwid" to setOf(
            "com.huawei.push.action.MESSAGING_EVENT",
            "com.huawei.intent.action.PUSH",
            "com.huawei.android.push.intent.RECEIVE",
        ),
        "com.hihonor.pushagent" to null,
        "com.meizu.cloud" to null,
        "com.coloros.mcs" to null,
        "com.oplus.mcs" to null,
        "com.sec.spp.push" to null,
    )

    fun matches(caller: String?, action: String?): Boolean {
        if (caller == null || caller !in transports) return false
        val actions = transports[caller] ?: return true
        return action in actions
    }
}

/**
 * Background: not persistent, top, bound-top, foreground service or important foreground. The request
 * atoms declare stats ProcessStateEnum (1000-1020), but ActivityManager writes its raw
 * android.app.ProcessStateEnum values (-1-20): an API 35 emulator reported 0, 2, 3, 5, 10, 11 and 15.
 * Both are read; unknown (-1, 998, 999, a wrapped 4294967295) and nonexistent are null.
 */
internal fun requesterInBackground(state: Int?): Boolean? = when (state) {
    0, 1, 2, 3, 4, 6 -> false
    5, in 7..19 -> true
    1000, 1001, 1002, 1020, 1003, 1005 -> false
    in 1004..1018 -> true
    else -> null
}

/** Shared lookups over one batch of stored evidence. Joins use the boot and elapsed clock of each record. */
internal class WakeEvidenceIndex(val evidence: WakeDetectionEvidence) {
    private val parsed = HashMap<String, Map<String, String>>()

    private fun WakeRecord.attributesMap(): Map<String, String> = parsed.getOrPut(id) {
        try {
            (Json.parseToJsonElement(this.attributes) as? JsonObject)?.mapValues { (_, value) -> (value as? JsonPrimitive)?.content.orEmpty() }.orEmpty()
        } catch (_: IllegalArgumentException) {
            emptyMap()
        }
    }

    fun WakeRecord.attr(key: String): String? = attributesMap()[key]?.takeIf { it.isNotEmpty() }

    fun WakeRecord.number(key: String): Long? = attr(key)?.toLongOrNull()

    /** The uid of an attribution chain is the first entry. */
    fun WakeRecord.chainUid(): Int? = attr("attribution_node")?.substringBefore(',')?.toIntOrNull()

    val byId = evidence.records.associateBy { it.id }
    val newestWall: Map<String, Long> by lazy { evidence.records.groupBy { it.boot }.mapValues { (_, list) -> list.maxOf { it.wallMillis } } }
    val byKind = evidence.records.groupBy { it.kind }

    fun kind(name: String) = byKind[name].orEmpty()

    /** Records of [kind] in [boot] whose uid is [uid], sorted by elapsed time. */
    fun byUid(kind: String, uid: (WakeRecord) -> Int?): Map<Pair<String, Int>, List<WakeRecord>> = kind(kind).mapNotNull { record ->
        uid(record)?.let { (record.boot to it) to record }
    }.groupBy({ it.first }, { it.second }).mapValues { (_, list) -> list.sortedBy { it.elapsedNanos } }

    fun within(list: List<WakeRecord>?, from: Long, through: Long) = list.orEmpty().filter { it.elapsedNanos in from..through }

    private val notifications by lazy {
        // NotificationRecordLogger: POSTED and UPDATED. ADJUSTED comes from the assistant.
        val appEvents = setOf(162L, 163L)
        byUid("NotificationReported") { record -> record.targetUid.takeIf { record.number("event_id") in appEvents } }
    }

    fun postedNotification(boot: String, uid: Int, target: String, elapsedNanos: Long): Boolean = notifications[boot to uid].orEmpty().any { record ->
        record.elapsedNanos in elapsedNanos..(elapsedNanos + WakeRevives.PRODUCTIVE_WINDOW) &&
            (record.targetPackage == target || (record.targetPackage == null && packageOf(uid) == target))
    }

    private val interactiveStates = kind("InteractiveStateChanged").groupBy { it.boot }
        .mapValues { (_, records) -> records.sortedBy { it.elapsedNanos } }

    private fun screenState(boot: String, elapsedNanos: Long) = interactiveStates[boot]?.lastOrNull { it.elapsedNanos <= elapsedNanos }?.number("state")

    /** An unknown screen state cannot establish that the screen was off. */
    fun isScreenOff(boot: String, elapsedNanos: Long): Boolean = screenState(boot, elapsedNanos) == 0L

    fun WakeRecord.balCallerUid(): Int? = attr("real_calling_uid")?.toIntOrNull()?.takeIf(::isRegularAppUid)
        ?: callerUid?.takeIf(::isRegularAppUid)

    fun WakeRecord.balEffectiveCallerUid(): Int? = callerUid?.takeIf(::isRegularAppUid)
        ?: attr("real_calling_uid")?.toIntOrNull()?.takeIf(::isRegularAppUid)

    private val launchers = kind("RoleHolder").filter { it.action == "android.app.role.HOME" }.mapNotNull { it.targetPackage }.toSet()

    fun isLauncher(pkg: String?): Boolean = pkg in launchers

    private fun WakeRecord.balCallerPackages() = evidence.packages[callerUid].orEmpty() +
        evidence.packages[balCallerUid()].orEmpty() + listOfNotNull(callerPackage)

    private val appBackgroundLaunches = kind("BalAllowed").filter { record ->
        record.balCallerUid() != null && record.balCallerPackages().none { it == "com.android.systemui" || isLauncher(it) }
    }

    private val backgroundLaunches = appBackgroundLaunches.mapNotNull { record ->
        val key = componentKey(record.component) ?: return@mapNotNull null
        (record.boot to key.substringBefore('/')) to record
    }.groupBy({ it.first }, { it.second })

    private val componentlessLaunches = appBackgroundLaunches.filter { it.component.isNullOrBlank() }.groupBy { it.boot }

    fun backgroundActivityLaunch(
        boot: String,
        target: String,
        component: String?,
        elapsedNanos: Long,
        windowNanos: Long,
        callerUid: Int? = null,
        callerPackage: String? = null,
        targetUid: Int? = null,
        allowUnattributed: Boolean = true,
        coldActivityTimes: List<Long>,
    ): WakeRecord? {
        val qualified = qualifiedWakeComponent(target, component)
        val window = (elapsedNanos - windowNanos)..(elapsedNanos + windowNanos)
        val named = backgroundLaunches[boot to target].orEmpty().asSequence()
            .filter { it.elapsedNanos in window && (component == null || (qualified != null && qualifiedWakeComponent(target, it.component) == qualified)) }
            .minByOrNull { abs(it.elapsedNanos - elapsedNanos) }
        if (named != null) return named
        val candidates = componentlessLaunches[boot].orEmpty().asSequence().filter { it.elapsedNanos in window }
        return when {
            callerUid != null -> candidates.singleOrNull { it.callerUid == callerUid || it.balCallerUid() == callerUid }
                ?.takeIf { it.isUniqueColdActivity(elapsedNanos, coldActivityTimes, windowNanos) }

            callerPackage != null -> candidates.singleOrNull { callerPackage in it.balCallerPackages() }
                ?.takeIf { it.isUniqueColdActivity(elapsedNanos, coldActivityTimes) }

            allowUnattributed && screenState(boot, elapsedNanos) == 1L ->
                candidates.singleOrNull { abs(it.elapsedNanos - elapsedNanos) <= COMPONENTLESS_BAL_WINDOW_NS }
                    ?.takeIf {
                        it.isUniqueColdActivity(elapsedNanos, coldActivityTimes) &&
                            it.balEffectiveCallerUid() != targetUid && target !in it.balCallerPackages()
                    }

            else -> null
        }
    }

    private fun WakeRecord.isUniqueColdActivity(
        launchTime: Long,
        coldActivityTimes: List<Long>,
        windowNanos: Long = COMPONENTLESS_BAL_WINDOW_NS,
    ): Boolean = abs(elapsedNanos - launchTime) <= windowNanos &&
        coldActivityTimes.asSequence().filter { abs(elapsedNanos - it) <= windowNanos }.take(2).count() == 1

    fun packageOf(uid: Int?, process: String? = null): String? {
        if (uid == null) return null
        val names = evidence.packages[uid].orEmpty()
        val base = process?.substringBefore(':')
        return when {
            base != null && base in names -> base
            names.size == 1 -> names.single()
            else -> null
        }
    }

    private data class PidStart(val record: WakeRecord, val elapsedNanos: Long)

    private val startsByPid by lazy {
        val events = kind("am_proc_start").filter { it.source == SYSTEM_WAKE_EVENTS }.groupBy { it.boot to it.pid }
        val stats = kind("ProcessStartTime").filter { it.source == "statsd" && it.startType != 1 && it.startType != 2 }.mapNotNull { record ->
            val elapsed = record.processStartElapsed()
            // The event log names the process and reports its actual uid, including isolated processes.
            if (events[record.boot to record.pid].orEmpty().any { abs(it.elapsedNanos - elapsed) <= WakeRevives.MERGE_WINDOW }) {
                null
            } else {
                PidStart(record, elapsed)
            }
        }
        (events.values.flatten().map { PidStart(it, it.elapsedNanos) } + stats)
            .filter { it.record.pid != null }.groupBy { it.record.boot to it.record.pid }
            .mapValues { (_, starts) -> starts.sortedBy { it.elapsedNanos } }
    }
    private val deathsByPid by lazy { kind("am_proc_died").filter { it.source == SYSTEM_WAKE_EVENTS }.groupBy { it.boot to it.pid } }
    private val uidsByPackage by lazy {
        evidence.packages.flatMap { (uid, packages) -> packages.map { it to uid } }.groupBy({ it.first }, { it.second })
    }

    fun WakeRecord.processStartElapsed(): Long = number("process_start_time_millis")?.takeIf { it >= 0 }?.times(1_000_000) ?: elapsedNanos

    /** The latest known PID lifetime takes precedence over a package name shared across profiles. */
    fun unfreezeTarget(record: WakeRecord): Pair<Int, String>? {
        val process = record.processName ?: return null
        val base = process.substringBefore(':')
        val key = record.boot to record.pid
        val start = startsByPid[key]?.lastOrNull { it.elapsedNanos <= record.elapsedNanos }
        if (start != null) {
            val uid = start.record.targetUid?.takeIf(::isRegularAppUid) ?: return null
            if (deathsByPid[key].orEmpty().any { it.elapsedNanos in start.elapsedNanos..record.elapsedNanos }) return null
            val names = evidence.packages[uid].orEmpty()
            val target = if (start.record.kind == "am_proc_start") {
                if (start.record.processName != process) return null
                base.takeIf { it in names } ?: names.singleOrNull()
            } else {
                start.record.targetPackage?.takeIf { it == base && it in names }
            }
            return target?.let { uid to it }
        }
        val uid = uidsByPackage[base]?.singleOrNull()?.takeIf(::isRegularAppUid) ?: return null
        return (uid to base).takeIf { evidence.packages[uid]?.size == 1 }
    }

    private val alarmSnapshots by lazy {
        kind("PendingAlarm").groupBy({ it.boot }, { it.elapsedNanos }).mapValues { (_, times) -> times.distinct().sorted() }
    }
    private val alarmIntents by lazy {
        evidence.relations.asSequence().filter { it.evidence == "PENDING_INTENT_REGISTRATION" }.mapNotNull { relation ->
            val alarm = byId[relation.requestId]?.takeIf { it.kind == "PendingAlarm" } ?: return@mapNotNull null
            val intent = byId[relation.processId]?.takeIf { it.kind == "PendingIntentRegistration" } ?: return@mapNotNull null
            val target = intent.targetPackage ?: return@mapNotNull null
            if (alarm.boot != intent.boot || alarm.elapsedNanos != intent.elapsedNanos) return@mapNotNull null
            Triple(alarm.boot, target, alarm.elapsedNanos) to intent
        }.groupBy({ it.first }, { it.second })
    }

    /** A snapshot witnesses pending alarms at one instant. Older snapshots may contain cancelled alarms. */
    private fun pendingAlarmIntents(boot: String, target: String, elapsedNanos: Long, uid: Int?): List<WakeRecord> {
        val times = alarmSnapshots[boot] ?: return emptyList()
        val snapshot = times.lastOrNull { it <= elapsedNanos }
            ?: times.firstOrNull()?.takeIf { it <= elapsedNanos + WakeRevives.JOIN_WINDOW } ?: return emptyList()
        if (elapsedNanos - snapshot > MAX_ALARM_SNAPSHOT_AGE_NS) return emptyList()
        return alarmIntents[Triple(boot, target, snapshot)].orEmpty().filter { intent ->
            val callerUid = intent.callerUid
            uid == null || callerUid == null || userOfUid(callerUid) == userOfUid(uid)
        }
    }

    fun pendingAlarmIntent(boot: String, target: String, elapsedNanos: Long, creator: String? = null, uid: Int? = null): WakeRecord? {
        val registrations = pendingAlarmIntents(boot, target, elapsedNanos, uid)
        // WakeupAlarmOccurred can name the scheduler, whose package may differ from the creator.
        val matchingCreator = creator?.let { registrations.filter { intent -> intent.callerPackage == it } }.orEmpty()
        // Repeated tokens may describe the same intent. Different creators, actions, or component
        // types are ambiguous even when the target package is the same.
        return matchingCreator.ifEmpty { registrations }.asSequence()
            .distinctBy { listOf(it.callerPackage, qualifiedWakeComponent(target, it.component), it.action, it.attr("type")) }
            .singleOrNull()?.takeIf { intent ->
                if (intent.component == null) {
                    intent.action != null && intent.action != "<redacted>"
                } else {
                    qualifiedWakeComponent(target, intent.component) != null
                }
            }
    }

    private val jobs by lazy { byUid("ScheduledJobStateChanged") { record -> record.chainUid().takeIf { record.number("state") == 1L } } }
    private val syncs by lazy { byUid("SyncStateChanged") { record -> record.chainUid().takeIf { record.number("state") == 1L } } }
    private val wakeups by lazy { byUid("WakeupAlarmOccurred") { it.chainUid() } }
    private val batches by lazy {
        kind("AlarmBatchDelivered").flatMap { record ->
            record.attr("uids").orEmpty().split(',').mapNotNull(String::toIntOrNull).map { (record.boot to it) to record }
        }.groupBy({ it.first }, { it.second }).mapValues { (_, records) -> records.sortedBy { it.elapsedNanos } }
    }
    private val dozeAlarms by lazy {
        byUid("AlarmScheduled") { record ->
            record.callerUid.takeIf { record.number("is_alarm_clock") == 1L || record.number("is_allow_while_idle") == 1L }
        }
    }

    fun scheduledWake(boot: String, uid: Int, target: String, elapsed: Long, component: String? = null): WakeTrigger? {
        val key = boot to uid
        val window = (elapsed - WakeRevives.JOIN_WINDOW)..(elapsed + WakeRevives.JOIN_WINDOW)
        // An idle-exempt schedule may corroborate a direct wakeup for this app. The schedule
        // atom has no alarm identifier, so a coarse UID batch cannot take precedence over a job or sync.
        if (dozeAlarms[key].orEmpty().any { it.elapsedNanos in window.first..elapsed } && wakeupAlarm(boot, uid, target, window) != null) {
            return alarmWake(boot, uid, target, elapsed, component)
        }
        jobs[key]?.lastOrNull { it.elapsedNanos in window }?.let {
            return WakeTrigger(ReviveTrigger.JOB, Confidence.JOINED, "android", component ?: it.attr("job_name"))
        }
        if (syncs[key].orEmpty().any { it.elapsedNanos in window }) return WakeTrigger(ReviveTrigger.SYNC, Confidence.JOINED, "android", component)
        return alarmWake(boot, uid, target, elapsed, component)
    }

    private fun wakeupAlarm(boot: String, uid: Int, pkg: String, window: LongRange): WakeRecord? = wakeups[boot to uid]?.lastOrNull {
        it.elapsedNanos in window && (it.targetPackage == pkg || (it.targetPackage == null && packageOf(uid) == pkg))
    }

    fun alarmWake(boot: String, uid: Int, target: String, elapsed: Long, component: String?, direct: WakeTrigger? = null): WakeTrigger? {
        val window = (elapsed - WakeRevives.JOIN_WINDOW)..(elapsed + WakeRevives.JOIN_WINDOW)
        fun batch(fromUid: Int) = batches[boot to fromUid]?.lastOrNull { it.elapsedNanos in window }

        // Alarm atoms can name the scheduler's uid even when the PendingIntent targets another app.
        val ownDelivery = wakeupAlarm(boot, uid, target, window) ?: batch(uid).takeIf { evidence.packages[uid].orEmpty().size <= 1 }
        val peerDelivery = if (ownDelivery == null) {
            pendingAlarmIntents(boot, target, elapsed, uid).mapNotNull { intent ->
                val creator = intent.callerPackage ?: return@mapNotNull null
                val creatorUid = intent.callerUid?.takeIf { it != uid && isRegularAppUid(it) } ?: return@mapNotNull null
                if (creator == target || (component != null && qualifiedWakeComponent(target, intent.component) != component)) return@mapNotNull null
                val alarm = wakeupAlarm(boot, creatorUid, creator, window) ?: batch(creatorUid).takeIf { packageOf(creatorUid) == creator }
                alarm?.let { it to creator }
            }.distinct().singleOrNull()
        } else {
            null
        }
        val delivery = ownDelivery ?: peerDelivery?.first
        val alarm = direct ?: delivery?.let { WakeTrigger(ReviveTrigger.ALARM, Confidence.JOINED, "android", it.attr("tag")) } ?: return null
        if (component != null) return alarm.copy(detail = "$target/$component")

        val creator = ownDelivery?.targetPackage?.takeIf { ownDelivery.kind == "WakeupAlarmOccurred" } ?: peerDelivery?.second
        val intent = pendingAlarmIntent(boot, target, elapsed, creator, uid) ?: return alarm
        val detail = qualifiedWakeComponent(target, intent.component)?.let { "$target/$it" }
        val caller = intent.callerPackage
        return when {
            caller != target && caller.isAppPackage() && intent.callerUid?.let(::isRegularAppUid) != false ->
                alarm.copy(trigger = ReviveTrigger.PENDING_INTENT, actor = caller, detail = detail ?: intent.action, confidence = Confidence.SNAPSHOT)

            detail != null -> alarm.copy(detail = detail)

            else -> alarm
        }
    }

    /** Wall clock of an elapsed instant, using the record that observed it as the reference. */
    fun wallNanos(record: WakeRecord, elapsed: Long) = record.wallMillis * 1_000_000 + (elapsed - record.elapsedNanos)

    // Special access the user granted. Bound services (IME, accessibility, notification listener,
    // wallpaper) are granted per component. Roles, companion devices and the always-on VPN package
    // are granted per package. SYSTEM_* roles are assigned by the platform, not by the user.
    private val bindings = kind("SystemBinding") + kind("WallpaperBinding")
    private val componentGrants = bindings.mapNotNull { record ->
        val pkg = record.targetPackage ?: return@mapNotNull null
        record.component?.takeIf { '/' in it }?.let { qualifiedWakeComponent(pkg, it) }?.let { pkg to it }
    }.groupBy({ it.first }, { it.second }).mapValues { it.value.toSet() }
    private val packageGrants = (
        bindings.filter { it.component?.contains('/') != true } +
            kind("RoleHolder").filterNot { it.action.orEmpty().startsWith("android.app.role.SYSTEM_") } +
            kind("CompanionAssociation")
        ).mapNotNull { it.targetPackage }.toSet()

    // The apps that could not notify at each snapshot, as `package/uid`.
    private val notificationsOff = kind(WAKE_NOTIFICATIONS_OFF).map { it.wallMillis to it.attr("apps").orEmpty().split(',').toSet() }
    private val overlays = kind(WAKE_OVERLAY_WINDOWS).map { it.wallMillis to it.attr("packages").orEmpty().split(',').toSet() }
    private val audioPlayback = kind(WAKE_AUDIO_PLAYBACK).mapNotNull { record ->
        val uids = record.attributesMap()["uids"] ?: return@mapNotNull null
        record.wallMillis to uids.split(',').mapNotNull(String::toIntOrNull).toSet()
    }

    /** The app could not notify at any snapshot taken in the interval. Without a snapshot nothing is known. */
    fun notificationsOff(pkg: String, uid: Int, fromWall: Long, toWall: Long): Boolean {
        val taken = notificationsOff.filter { it.first in fromWall..toWall }
        return taken.isNotEmpty() && taken.all { "$pkg/$uid" in it.second }
    }

    fun hasOverlayWindow(pkg: String, uid: Int, fromWall: Long, toWall: Long): Boolean = overlays.any { (wall, packages) ->
        wall in fromWall..toWall && ("$pkg/$uid" in packages || pkg in packages)
    }

    /** Without a playback snapshot, audio activity is unknown. */
    fun silentAudio(uid: Int, fromWall: Long, toWall: Long): Boolean {
        val taken = audioPlayback.filter { it.first in fromWall..toWall }
        return taken.isNotEmpty() && taken.none { uid in it.second }
    }

    fun grantedSpecialAccess(pkg: String, component: String?): Boolean {
        if (pkg in packageGrants) return true
        val components = componentGrants[pkg] ?: return false
        // Without a component the binding cannot be told apart from another service of the package.
        val qualified = component?.let { qualifiedWakeComponent(pkg, it) } ?: return true
        return qualified in components
    }

    val providerComponents = kind("HeldConnection").filter { it.source == "PROVIDERS" }.mapNotNull { record ->
        record.component?.let { qualifiedWakeComponent(record.targetPackage ?: it.substringBefore('/'), it) }
    }.toSet()
}
