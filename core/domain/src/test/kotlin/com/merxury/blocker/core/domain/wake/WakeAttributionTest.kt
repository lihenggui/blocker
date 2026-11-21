// Copyright 2026 Blocker
// SPDX-License-Identifier: Apache-2.0

package com.merxury.blocker.core.domain.wake

import com.google.common.truth.Truth.assertThat
import com.merxury.blocker.core.database.wake.WakeRecord
import com.merxury.blocker.core.model.wake.SYSTEM_WAKE_EVENTS
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], manifest = Config.NONE)
class WakeAttributionTest {
    private val request = WakeRecord(
        "request", "boot", 20, 1000, "statsd", "ServiceRequestEventReported",
        10011, 10022, "client.app", "target.app", ".Service", "wake", 3, null, null, "{}",
    )
    private val start = request.copy(
        id = "start",
        elapsedNanos = 30,
        kind = "ProcessStartTime",
        pid = 123,
        component = "target.app/.Service",
        attributes = """{"hosting_type_id":"16"}""",
    )

    @Test fun givenColdStartRequests_whenLinkingToProcessStarts_thenOnlyUniqueSameBootMatchesQualify() {
        assertThat(WakeAttribution.relationships(listOf(start, request)).single().requestId).isEqualTo("request")
        assertThat(WakeAttribution.relationships(listOf(start, request, request.copy(id = "other")))).isEmpty()
        assertThat(WakeAttribution.relationships(listOf(start, request.copy(boot = "another")))).isEmpty()
        assertThat(WakeAttribution.relationships(listOf(start, request.copy(startType = 2)))).isEmpty()
        assertThat(WakeAttribution.relationships(listOf(start, request.copy(component = ".Payment")))).isEmpty()
    }

    @Test fun givenRequestsOnWindowBoundaries_whenLinkingStarts_thenEqualTimestampsPreserveAmbiguity() {
        val window = 5_000_000_000L
        val middle = start.copy(elapsedNanos = 2 * window)
        for (edge in listOf(window, 3 * window)) {
            val match = request.copy(elapsedNanos = edge)
            val outside = request.copy(id = "outside", elapsedNanos = if (edge == window) edge - 1 else edge + 1)
            assertThat(WakeAttribution.relationships(listOf(middle, outside, match))).hasSize(1)
            assertThat(WakeAttribution.relationships(listOf(middle, match.copy(id = "duplicate"), outside, match))).isEmpty()
        }
    }

    @Test fun givenSequentialColdStarts_whenJoiningThem_thenIntermediateCallerMustBeTheSameLiveProcess() {
        val middle = start.copy(processName = "target.app")
        val witness = middle.copy(id = "system", source = SYSTEM_WAKE_EVENTS, kind = "am_proc_start", processName = "target.app:peer")
        val outgoing = request.copy(
            id = "next",
            elapsedNanos = 40,
            callerUid = request.targetUid,
            callerPackage = "target.app",
            targetUid = 10033,
            targetPackage = "third.app",
            attributes = """{"request_process_name":":peer","unique_caller_package":"target.app"}""",
        )
        val last = start.copy(
            id = "last",
            elapsedNanos = 50,
            pid = 456,
            targetUid = 10033,
            targetPackage = "third.app",
            callerUid = request.targetUid,
            component = "third.app/.Service",
            processName = "third.app",
        )
        fun links(vararg entries: WakeRecord) = entries.toList().let { WakeAttribution.continuations(it, WakeAttribution.relationships(it)) }
        val chain = links(request, middle, witness, outgoing, last).single()
        assertThat(links(request, middle, outgoing, last)).isEmpty()
        assertThat(links(request, middle, witness.copy(pid = 99), outgoing, last)).isEmpty()
        assertThat(links(request, middle, witness, witness.copy(id = "reused", elapsedNanos = 38), outgoing, last)).isEmpty()
        val reused = middle.copy(id = "reused-pid", elapsedNanos = 39, targetUid = 10888)
        assertThat(links(request, middle, witness, reused, outgoing, last)).isEmpty()
        val main = witness.copy(processName = "target.app")
        val bind = outgoing.copy(attributes = """{"unique_caller_package":"target.app","request_type":2}""")
        assertThat(links(request, middle, main, bind, last)).hasSize(1)
        assertThat(links(request, middle, main, bind.copy(attributes = "{}"), last)).isEmpty()
        assertThat(chain.incoming).isEqualTo(request.id)
        assertThat(chain.outgoing).isEqualTo(outgoing.id)
        assertThat(links(request, middle, witness, outgoing.copy(elapsedNanos = 10), last)).isEmpty()
        assertThat(links(request, middle, witness, outgoing.copy(attributes = "{}"), last)).isEmpty()
        assertThat(links(request, middle, witness, outgoing.copy(attributes = """{"request_process_name":"target.app:billing"}"""), last)).isEmpty()
        assertThat(links(request, middle, witness, outgoing, last, middle.copy(id = "death", processName = "target.app:peer", kind = "AppProcessDied", elapsedNanos = 36))).isEmpty()
        assertThat(links(request, middle, witness, outgoing, last.copy(boot = "other"))).isEmpty()
    }

    @Test fun givenJobEvents_whenLinkingExecution_thenOnlyAnUncancelledMatchingRegistrationQualifies() {
        fun job(id: String, state: Int, time: Long) = request.copy(
            id = id,
            kind = "ScheduledJobStateChanged",
            elapsedNanos = time,
            attributes = """{"attribution_node":"10011","job_id":"42","namespace_hash":"x","state":"$state"}""",
        )
        val registered = job("registered", 2, 1)
        val execution = job("ran", 1, 3)
        assertThat(WakeAttribution.relationships(listOf(registered, execution)).single().processId).isEqualTo("registered")
        assertThat(WakeAttribution.relationships(listOf(registered, job("cancel", 3, 2), execution))).isEmpty()
    }
}
