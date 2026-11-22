// Copyright 2026 Blocker
// SPDX-License-Identifier: Apache-2.0

package com.merxury.blocker.core.domain.wake

import com.merxury.blocker.core.data.wake.WakeDetectionEvidence
import com.merxury.blocker.core.database.wake.WakeRecord
import com.merxury.blocker.core.model.wake.qualifiedWakeComponent
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

internal fun appId(uid: Int) = uid % 100_000

internal fun isApp(uid: Int?) = uid != null && appId(uid) >= 10_000

/**
 * android.os.Process FIRST_APPLICATION_UID..LAST_APPLICATION_UID. SDK sandbox (20000-29999), app
 * zygote (90000-98999) and isolated (99000-99999) uids run code on behalf of a host app and are not
 * revives of it.
 */
internal fun isRegularApp(uid: Int?) = uid != null && appId(uid) in 10_000..19_999

/** Push services whose deliveries wake their clients. GMS also performs other requests, so only its c2dm delivery counts. */
internal object WakePushTransports {
    // Google Play services delivers FCM with the c2dm receive action. The vendor entries are the
    // Xiaomi push framework, OPPO/HeyTap system messages and the vivo push engine.
    private val transports = mapOf(
        "com.google.android.gms" to setOf("com.google.android.c2dm.intent.RECEIVE"),
        "com.xiaomi.xmsf" to null,
        "com.heytap.mcs" to null,
        "com.vivo.pushservice" to null,
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

    fun WakeRecord.attr(key: String): String? = parsed.getOrPut(id) {
        try {
            (Json.parseToJsonElement(this.attributes) as? JsonObject)?.mapValues { (_, value) -> (value as? JsonPrimitive)?.content.orEmpty() }.orEmpty()
        } catch (_: IllegalArgumentException) {
            emptyMap()
        }
    }[key]?.takeIf { it.isNotEmpty() }

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
