// Copyright 2026 Blocker
// SPDX-License-Identifier: Apache-2.0

package com.merxury.blocker.core.domain.wake

import com.google.common.truth.Truth.assertThat
import com.merxury.blocker.core.data.wake.WakeDetectionEvidence
import com.merxury.blocker.core.domain.wake.WakeDetectionTestValues.PEER
import com.merxury.blocker.core.domain.wake.WakeDetectionTestValues.PEER_UID
import com.merxury.blocker.core.domain.wake.WakeDetectionTestValues.SECOND
import com.merxury.blocker.core.domain.wake.WakeDetectionTestValues.START
import com.merxury.blocker.core.domain.wake.WakeDetectionTestValues.TARGET
import com.merxury.blocker.core.domain.wake.WakeDetectionTestValues.TARGET_UID
import com.merxury.blocker.core.domain.wake.WakeDetectionTestValues.WALL
import com.merxury.blocker.core.model.ComponentType
import com.merxury.blocker.core.model.component.UIDS_PER_USER
import com.merxury.blocker.core.model.wake.Confidence
import com.merxury.blocker.core.model.wake.HoldCause
import com.merxury.blocker.core.model.wake.Justification
import com.merxury.blocker.core.model.wake.OwnWakeKind
import com.merxury.blocker.core.model.wake.ReviveTrigger
import com.merxury.blocker.core.model.wake.SYSTEM_WAKE_EVENTS
import com.merxury.blocker.core.model.wake.WakeBlockMethod
import com.merxury.blocker.core.model.wake.WakeFinding
import com.merxury.blocker.core.model.wake.WakeHoldAssociation
import com.merxury.blocker.core.model.wake.WakeHoldPeriod
import com.merxury.blocker.core.model.wake.WakeHoldProcess
import com.merxury.blocker.core.model.wake.WakeRecord
import com.merxury.blocker.core.model.wake.WakeRelation
import com.merxury.blocker.core.model.wake.WakeVerdict
import com.merxury.blocker.core.privileged.wake.WAKE_AUDIO_PLAYBACK
import com.merxury.blocker.core.privileged.wake.WAKE_NOTIFICATIONS_OFF
import com.merxury.blocker.core.privileged.wake.WAKE_OVERLAY_WINDOWS
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Test
import java.time.ZoneOffset

internal object WakeDetectionTestValues {
    const val TARGET = "target.app"
    const val TARGET_UID = 10022
    const val PEER = "peer.app"
    const val PEER_UID = 10011
    const val WALL = 1_795_335_233_000L
    const val SECOND = 1_000_000_000L
    const val START = 100 * SECOND
}

internal fun wakeRecord(
    id: String,
    kind: String,
    elapsed: Long,
    source: String = "statsd",
    callerUid: Int? = null,
    targetUid: Int? = TARGET_UID,
    callerPackage: String? = null,
    targetPackage: String? = TARGET,
    component: String? = null,
    action: String? = null,
    startType: Int? = null,
    process: String? = null,
    attributes: Map<String, String> = emptyMap(),
    pid: Int? = null,
) = WakeRecord(
    id, "boot", elapsed, WALL + elapsed / 1_000_000, source, kind, callerUid, targetUid, callerPackage, targetPackage,
    component, action, startType, pid, process, JsonObject(attributes.mapValues { JsonPrimitive(it.value) }).toString(),
)

/** A start-info record and the event log's start it describes: start-info alone is never a revive. */
internal fun started(info: WakeRecord) = arrayOf(
    info,
    wakeRecord(
        "${info.id}-event",
        "am_proc_start",
        info.elapsedNanos,
        source = SYSTEM_WAKE_EVENTS,
        targetUid = info.targetUid,
        targetPackage = null,
        component = info.component?.let { "{$it}" },
        process = info.processName,
    ),
)

internal abstract class WakeDetectionTestFixture {
    protected val coldStart = wakeRecord("start", "ProcessStartTime", START, component = "$TARGET/.Wake", startType = 3, process = TARGET)
    protected val activityStart = coldStart.copy(attributes = """{"hosting_type_id":"1"}""")

    protected fun evidence(
        records: List<WakeRecord>,
        relations: List<WakeRelation> = emptyList(),
        periods: List<WakeHoldPeriod> = emptyList(),
        processes: List<WakeHoldProcess> = emptyList(),
        associations: List<WakeHoldAssociation> = emptyList(),
    ) = WakeDetectionEvidence(records, relations, emptyList(), periods, processes, associations, mapOf(TARGET_UID to setOf(TARGET), TARGET_UID + UIDS_PER_USER to setOf(TARGET), PEER_UID to setOf(PEER)))

    protected fun revive(vararg records: WakeRecord, relations: List<WakeRelation> = emptyList()): WakeFinding = WakeDetection.findings(evidence(records.toList(), relations)).single { it.trigger != null }

    protected fun joined(request: WakeRecord, vararg records: WakeRecord) = revive(coldStart, request, *records, relations = listOf(WakeRelation(request.id, coldStart.id, "MATCHING_CALLER")))

    protected fun request(kind: String, caller: Int, callerPackage: String? = null, action: String? = null, state: Pair<String, String>? = null) = wakeRecord("request", kind, START - SECOND, callerUid = caller, callerPackage = callerPackage, component = ".Wake", action = action, startType = 3, attributes = listOfNotNull(state).toMap())

    protected fun screenState(state: Int, elapsed: Long = START, boot: String = coldStart.boot) = wakeRecord("screen-$state-$elapsed-$boot", "InteractiveStateChanged", elapsed, targetUid = null, targetPackage = null, attributes = mapOf("state" to state.toString())).copy(boot = boot)

    protected fun bal(callerUid: Int, realUid: Int, elapsed: Long = START, component: String? = "$TARGET/.Wake") = wakeRecord("bal", "BalAllowed", elapsed, callerUid = callerUid, component = component, targetUid = null, targetPackage = null, attributes = mapOf("real_calling_uid" to realUid.toString()))
}
