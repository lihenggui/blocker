// Copyright 2026 Blocker
// SPDX-License-Identifier: Apache-2.0

package com.merxury.blocker.core.data.wake

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.merxury.blocker.core.controllers.wake.WAKE_START_INFO
import com.merxury.blocker.core.controllers.wake.WAKE_START_INFO_SOURCE
import com.merxury.blocker.core.controllers.wake.WakeCapture
import com.merxury.blocker.core.controllers.wake.WakeCollector
import com.merxury.blocker.core.controllers.wake.WakeExemptionSnapshot
import com.merxury.blocker.core.controllers.wake.WakeProcStatsAssociation
import com.merxury.blocker.core.controllers.wake.WakeProcStatsHold
import com.merxury.blocker.core.controllers.wake.WakeProcStatsPeriod
import com.merxury.blocker.core.controllers.wake.WakeResume
import com.merxury.blocker.core.database.wake.WakeDetectionDao
import com.merxury.blocker.core.database.wake.WakeExemption
import com.merxury.blocker.core.database.wake.WakeHistoryDao
import com.merxury.blocker.core.database.wake.WakeHoldAssociation
import com.merxury.blocker.core.database.wake.WakeHoldPeriod
import com.merxury.blocker.core.database.wake.WakeHoldProcess
import com.merxury.blocker.core.database.wake.WakeRecord
import com.merxury.blocker.core.database.wake.WakeSourceStatus
import com.merxury.blocker.core.database.wake.WakeStartInfoSeen
import com.merxury.blocker.core.model.privileged.ChannelState
import com.merxury.blocker.core.model.privileged.PrivilegedIdentity
import com.merxury.blocker.core.model.privileged.UnavailableReason
import com.merxury.blocker.core.model.wake.WakeBatch
import com.merxury.blocker.core.model.wake.WakeObservation
import com.merxury.blocker.core.testing.controller.FakePrivilegedChannel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.argThat
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], manifest = Config.NONE)
class WakeRepositoryTest {
    private val history = mock<WakeHistoryDao>()
    private val index = mock<WakeIdentityIndex>()
    private val collector = mock<WakeCollector>()
    private val detection = mock<WakeDetectionDao>()
    private val channel = FakePrivilegedChannel(ChannelState.Unavailable(UnavailableReason.NOT_CHECKED))
    private val repository = WakeRepository(ApplicationProvider.getApplicationContext<Context>(), history, index, collector, detection, channel)
    private val now = 1_795_335_233_000L

    private fun start(signature: String) = WakeObservation(
        WAKE_START_INFO,
        1_000_000_000,
        targetUid = 10123,
        targetPackage = "example.app",
        processName = "example.app",
        startType = 3,
        attributes = mapOf("reason" to "ALARM", "signature" to signature),
    )

    private val period = WakeProcStatsPeriod(
        "2026-11-22-08-13-53",
        now - 1_000,
        now,
        listOf(WakeProcStatsHold("example.app", 10123, "example.app", 4, 5, 1, 2)),
        listOf(WakeProcStatsAssociation("example.app", 10123, "example.app.Sync", "peer.app", 10124, 9, 6)),
    )

    private suspend fun captureWith(vararg batches: WakeBatch, resumes: MutableList<WakeResume> = mutableListOf()) {
        whenever(collector.capture(any(), any(), any())).thenAnswer { invocation ->
            val resume = invocation.getArgument<suspend (String) -> WakeResume>(2)
            resumes += runBlocking { resume("7") }
            WakeCapture(
                "7", 2_000_000_000, now, emptyList(), listOf(0), listOf(WakeBatch("authorization", emptyList())) + batches, false,
                listOf(period), WakeExemptionSnapshot(mapOf("example.app" to setOf("user", "system")), mapOf("other.app" to 40), null),
            )
        }
        whenever(index.installedPackages()).thenReturn(emptyList())
        whenever(index.labels(any(), any(), any())).thenReturn(emptyMap())
        whenever(index.refresh(any(), any(), any())).thenReturn(emptyList())
    }

    @Test fun givenCapture_whenCollecting_thenOnlyUnseenStartsAreImportedAndMarkedSeenTogether() = runTest {
        val resumes = mutableListOf<WakeResume>()
        captureWith(WakeBatch(WAKE_START_INFO_SOURCE, listOf(start("seen"), start("fresh"))), resumes = resumes)
        whenever(detection.latestEvent("7")).thenReturn(now - 60_000)
        whenever(detection.latestPeriod()).thenReturn("2026-11-22-05-13-53")
        whenever(detection.unseen(any())).thenReturn(setOf("fresh"))

        repository.collect(false, PrivilegedIdentity.SHELL)

        assertThat(resumes).containsExactly(WakeResume(now - 60_000, "2026-11-22-05-13-53"))
        verify(detection).unseen(listOf("seen", "fresh"))
        val imported = argumentCaptor<List<WakeRecord>>()
        verify(history).importStartInfo(imported.capture(), eq(listOf(WakeStartInfoSeen("fresh", now))), eq(now - WAKE_HISTORY_RETENTION_MILLIS))
        val record = imported.firstValue.single()
        assertThat(record.kind).isEqualTo(WAKE_START_INFO)
        assertThat(record.source).isEqualTo(WAKE_START_INFO_SOURCE)
        assertThat(record.attributes).isEqualTo("""{"reason":"ALARM"}""")
        assertThat(record.wallMillis).isEqualTo(now - 1_000)
        verify(detection).replacePeriod(
            WakeHoldPeriod(period.id, now - 1_000, now, now),
            listOf(WakeHoldProcess(period.id, "example.app", 10123, "example.app", 4, 5, 1, 2)),
            listOf(WakeHoldAssociation(period.id, "example.app", 10123, "example.app.Sync", "peer.app", 10124, 9, 6)),
        )
        verify(detection).replaceExemptions(
            listOf(WakeExemption("example.app", "system,user", null, null, now), WakeExemption("other.app", "", 40, null, now)),
        )
        verify(detection).expireEvidence(now - WAKE_HISTORY_RETENTION_MILLIS)
        verify(history).status(WakeSourceStatus(EVIDENCE_STATUS, now, "OK"))
    }

    @Test fun givenBrokenDetectionTables_whenCollecting_thenCollectionStillCompletes() = runTest {
        val resumes = mutableListOf<WakeResume>()
        captureWith(WakeBatch(WAKE_START_INFO_SOURCE, listOf(start("fresh"))), resumes = resumes)
        whenever(detection.latestEvent(any())).thenThrow(IllegalStateException("no such table"))
        whenever(detection.unseen(any())).thenThrow(IllegalStateException("no such table"))

        val result = repository.collect(false, PrivilegedIdentity.SHELL)

        // The resume point falls back to the boot start, and only a detection status records the failure.
        assertThat(resumes).containsExactly(WakeResume(null, null))
        assertThat(result.boot).isEqualTo("7")
        verify(history, times(2)).status(argThat { source == EVIDENCE_STATUS && status == "no such table" })
        verify(history, never()).importStartInfo(any(), any(), any())
        verify(detection, never()).replacePeriod(any(), any(), any())
    }

    @Test fun givenUpgradeWithOldRootGrantFlag_whenRepositoryOpens_thenOnlyThatFlagIsRemoved() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val preferences = context.getSharedPreferences("wake_protection", Context.MODE_PRIVATE)
        check(preferences.edit().putBoolean("previous_root_grant", true).putLong("last_diagnostic", 42).putBoolean("rules_pending", false).commit())

        val upgraded = WakeRepository(context, history, index, collector, detection, channel)

        assertThat(preferences.contains("previous_root_grant")).isFalse()
        assertThat(upgraded.lastDiagnostic).isEqualTo(42)
        assertThat(upgraded.rulesPending).isFalse()
    }

    @Test fun givenChannelReadyAsAnyIdentity_whenAskingReadAccess_thenThatIdentityWithoutAPrompt() = runTest {
        for (identity in PrivilegedIdentity.entries) {
            channel.setState(ChannelState.Ready(identity))
            assertThat(repository.readAccess()).isEqualTo(identity)
        }
        assertThat(channel.connectCount).isEqualTo(0)
    }

    @Test fun givenChannelUnavailable_whenAskingReadAccess_thenNoneWithoutAPrompt() = runTest {
        for (reason in UnavailableReason.entries) {
            channel.setState(ChannelState.Unavailable(reason))
            assertThat(repository.readAccess()).isNull()
        }
        assertThat(channel.connectCount).isEqualTo(0)
    }

    @Test fun givenCaptureTheChannelCouldNotFinish_whenCollecting_thenWhatWasReadIsStoredAndTheImportSaysSo() = runTest {
        whenever(collector.capture(any(), any(), any())).thenReturn(
            WakeCapture(
                "7",
                2_000_000_000,
                now,
                emptyList(),
                listOf(0),
                listOf(WakeBatch("authorization", emptyList()), WakeBatch("USERS", emptyList()), WakeBatch("SERVICES", emptyList(), listOf("privileged channel unavailable"))),
                channelUnavailable = true,
            ),
        )
        whenever(index.installedPackages()).thenReturn(emptyList())
        whenever(index.labels(any(), any(), any())).thenReturn(emptyMap())
        whenever(index.refresh(any(), any(), any())).thenReturn(emptyList())

        val result = repository.collect(false, PrivilegedIdentity.ROOT)

        assertThat(result.channelUnavailable).isTrue()
        verify(collector).capture(eq(false), eq(PrivilegedIdentity.ROOT), any())
        verify(history).status(WakeSourceStatus("USERS", now, "OK"))
        verify(history).status(WakeSourceStatus("SERVICES", now, "privileged channel unavailable"))
        verify(history).status(WakeSourceStatus("profile scope", now, "OK"))
    }
}
