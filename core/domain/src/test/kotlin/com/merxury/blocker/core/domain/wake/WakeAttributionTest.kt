// Copyright 2026 Blocker
// SPDX-License-Identifier: Apache-2.0

package com.merxury.blocker.core.domain.wake

import com.google.common.truth.Truth.assertThat
import com.merxury.blocker.core.database.wake.WakeRecord
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

    @Test fun requiresUniqueComponentAndBootScopedProcessEvidence() {
        assertThat(WakeAttribution.relationships(listOf(start, request)).single().requestId).isEqualTo("request")
        assertThat(WakeAttribution.relationships(listOf(start, request, request.copy(id = "other")))).isEmpty()
        assertThat(WakeAttribution.relationships(listOf(start, request.copy(boot = "another")))).isEmpty()
        assertThat(WakeAttribution.relationships(listOf(start, request.copy(startType = 2)))).isEmpty()
        assertThat(WakeAttribution.relationships(listOf(start, request.copy(component = ".Payment")))).isEmpty()
    }

    @Test fun joinsSequentialColdStartsOnlyForTheSameLiveCallerProcess() {
        val middle = start.copy(processName = "target.app:peer")
        val outgoing = request.copy(
            id = "next",
            elapsedNanos = 40,
            callerUid = request.targetUid,
            callerPackage = "target.app",
            targetUid = 10033,
            targetPackage = "third.app",
            attributes = """{"request_process_name":"target.app:peer"}""",
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
        val chain = links(request, middle, outgoing, last).single()
        assertThat(chain.incoming).isEqualTo(request.id)
        assertThat(chain.outgoing).isEqualTo(outgoing.id)
        assertThat(links(request, middle, outgoing.copy(elapsedNanos = 10), last)).isEmpty()
        assertThat(links(request, middle, outgoing.copy(attributes = "{}"), last)).isEmpty()
        assertThat(links(request, middle, outgoing.copy(attributes = """{"request_process_name":"target.app:billing"}"""), last)).isEmpty()
        assertThat(links(request, middle, outgoing, last, middle.copy(id = "death", kind = "AppProcessDied", elapsedNanos = 36))).isEmpty()
        assertThat(links(request, middle, outgoing, last.copy(boot = "other"))).isEmpty()
    }

    @Test fun jobsRequireMatchingRegistrationAndCancelBreaksTheLink() {
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
