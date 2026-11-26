// Copyright 2026 Blocker
// SPDX-License-Identifier: Apache-2.0

package com.merxury.blocker.core.domain.wake

import com.google.common.truth.Truth.assertThat
import com.merxury.blocker.core.model.wake.Confidence
import com.merxury.blocker.core.model.wake.HoldCause
import com.merxury.blocker.core.model.wake.Justification
import com.merxury.blocker.core.model.wake.ReviveTrigger
import com.merxury.blocker.core.model.wake.WakeFinding
import com.merxury.blocker.core.model.wake.WakeVerdict
import org.junit.Test
import java.time.ZoneId
import java.time.ZoneOffset

class WakeVerdictTest {
    private val utc: ZoneId = ZoneOffset.UTC
    private val dayNanos = 86_400_000_000_000L
    private val minute = 60_000_000_000L

    private fun revive(
        target: String = "t",
        trigger: ReviveTrigger = ReviveTrigger.SYSTEM_BROADCAST,
        actor: String? = null,
        background: Boolean? = null,
        afterKill: String? = null,
        justification: Justification = Justification.NONE,
        at: Long = 10,
    ) = Detected(WakeFinding.revive(target, trigger, actor, null, background, afterKill, justification, Confidence.DIRECT, at))

    private fun hold(
        screenOff: Long,
        justification: Justification = Justification.NONE,
        at: Long = 10,
        cause: HoldCause = HoldCause.UNKNOWN,
    ) = Detected(WakeFinding.hold("t", cause, null, null, justification, Confidence.SNAPSHOT, at, screenOff), screenOff)

    private fun verdict(vararg detected: Detected, target: String = "t") = WakeDetection.verdicts(detected.toList(), utc).single { it.target == target }

    @Test fun givenUnjustifiedRevives_whenJudging_thenTenAreSuspicious() {
        assertThat(verdict(*Array(9) { revive() }).verdict).isEqualTo(WakeVerdict.NORMAL.name)
        assertThat(verdict(*Array(10) { revive() }).verdict).isEqualTo(WakeVerdict.SUSPICIOUS.name)
        assertThat(verdict(*Array(30) { revive(justification = Justification.PRODUCTIVE) }).verdict).isEqualTo(WakeVerdict.NORMAL.name)
    }

    @Test fun givenUnexplainedRevives_whenJudging_thenOnlyThoseAfterAQualifyingKillCount() {
        val unexplained = verdict(*Array(30) { revive(trigger = ReviveTrigger.UNKNOWN) })
        assertThat(unexplained.verdict).isEqualTo(WakeVerdict.NORMAL.name)
        assertThat(unexplained.revives).isEqualTo(30)
        assertThat(unexplained.noneRevives).isEqualTo(0)
        val killed = verdict(*Array(3) { revive(trigger = ReviveTrigger.UNKNOWN, afterKill = "empty #4") })
        assertThat(killed.afterKillNoneRevives).isEqualTo(3)
        assertThat(killed.verdict).isEqualTo(WakeVerdict.KEEP_ALIVE.name)
    }

    @Test fun givenRevivesAfterKills_whenJudging_thenOneUnjustifiedIsSuspiciousAndThreeAreKeepAlive() {
        // Reopening an app the user swiped away is not suspicious, but it is still counted.
        val reopened = verdict(revive(afterKill = "remove task", justification = Justification.USER_INITIATED))
        assertThat(reopened.verdict).isEqualTo(WakeVerdict.NORMAL.name)
        assertThat(reopened.afterKillRevives).isEqualTo(1)
        val sticky = ReviveTrigger.STICKY_RESTART
        assertThat(verdict(revive(trigger = sticky, afterKill = "empty")).verdict).isEqualTo(WakeVerdict.SUSPICIOUS.name)
        val two = verdict(*Array(2) { revive(trigger = sticky, afterKill = "empty") })
        assertThat(two.verdict).isEqualTo(WakeVerdict.SUSPICIOUS.name)
        val three = verdict(*Array(3) { revive(trigger = sticky, afterKill = "empty") })
        assertThat(three.verdict).isEqualTo(WakeVerdict.KEEP_ALIVE.name)
        assertThat(three.afterKillNoneRevives).isEqualTo(3)
    }

    @Test fun givenPlatformDeliveriesAfterKills_whenJudging_thenOnlyTheInformationalCounterIncludesThem() {
        for (trigger in listOf(ReviveTrigger.SYSTEM_BROADCAST, ReviveTrigger.PUSH_TRANSPORT)) {
            val day = verdict(*Array(5) { revive(trigger = trigger, afterKill = "empty #4") })
            assertThat(day.afterKillRevives).isEqualTo(5)
            assertThat(day.afterKillNoneRevives).isEqualTo(0)
            assertThat(day.verdict).isEqualTo(WakeVerdict.NORMAL.name)
            // The finding keeps its kill.
            assertThat(WakeDetection.daily(listOf(revive(trigger = trigger, afterKill = "empty #4")), utc).single().afterKill).isEqualTo("empty ##")
        }
        // Other triggers after a kill still count, unexplained ones included.
        val mixed = verdict(
            revive(trigger = ReviveTrigger.SYSTEM_BROADCAST, afterKill = "empty"),
            revive(trigger = ReviveTrigger.ALARM, afterKill = "empty"),
            revive(trigger = ReviveTrigger.UNKNOWN, afterKill = "empty"),
        )
        assertThat(mixed.afterKillRevives).isEqualTo(3)
        assertThat(mixed.afterKillNoneRevives).isEqualTo(2)
        assertThat(mixed.verdict).isEqualTo(WakeVerdict.SUSPICIOUS.name)
    }

    @Test fun givenRepeatedSilentPushes_whenJudging_thenTheyStayInHistoryWithoutRaisingTheVerdict() {
        val pushes = Array(12) { revive(trigger = ReviveTrigger.PUSH_TRANSPORT, afterKill = "empty") }

        val day = verdict(*pushes)
        assertThat(day.revives).isEqualTo(12)
        assertThat(day.afterKillRevives).isEqualTo(12)
        assertThat(day.noneRevives).isEqualTo(0)
        assertThat(day.afterKillNoneRevives).isEqualTo(0)
        assertThat(day.verdict).isEqualTo(WakeVerdict.NORMAL.name)
        val row = WakeDetection.daily(pushes.toList(), utc).single()
        assertThat(row.trigger).isEqualTo(ReviveTrigger.PUSH_TRANSPORT.name)
        assertThat(row.justification).isEqualTo(Justification.NONE.name)
        assertThat(row.count).isEqualTo(12)

        val mixed = verdict(*pushes, revive(trigger = ReviveTrigger.JOB, afterKill = "empty"))
        assertThat(mixed.noneRevives).isEqualTo(1)
        assertThat(mixed.afterKillNoneRevives).isEqualTo(1)
        assertThat(mixed.verdict).isEqualTo(WakeVerdict.SUSPICIOUS.name)
    }

    @Test fun givenSystemBoundHolds_whenJudging_thenTheyNeverCountTowardScreenOffHold() {
        for (justification in listOf(Justification.NONE, Justification.USER_GRANTED_ROLE)) {
            val day = verdict(hold(5 * 60 * minute, justification, cause = HoldCause.BOUND_BY_SYSTEM))
            assertThat(day.screenOffHoldNoneNanos).isEqualTo(0)
            assertThat(day.verdict).isEqualTo(WakeVerdict.NORMAL.name)
        }
        // The finding itself stays recorded.
        val recorded = WakeDetection.daily(listOf(hold(5 * 60 * minute, cause = HoldCause.BOUND_BY_SYSTEM)), utc).single()
        assertThat(recorded.hold).isEqualTo(HoldCause.BOUND_BY_SYSTEM.name)
        assertThat(recorded.justification).isEqualTo(Justification.NONE.name)
        assertThat(recorded.screenOffNanos).isEqualTo(5 * 60 * minute)
        // The same three hour hold is KEEP_ALIVE when a peer app holds the process.
        assertThat(verdict(hold(180 * minute, cause = HoldCause.BOUND_BY_SYSTEM)).verdict).isEqualTo(WakeVerdict.NORMAL.name)
        assertThat(verdict(hold(180 * minute, cause = HoldCause.BOUND_BY_PEER)).verdict).isEqualTo(WakeVerdict.KEEP_ALIVE.name)
        // Holds by a peer app or of unknown cause still count, alongside a system-bound one.
        val peer = verdict(hold(5 * 60 * minute, cause = HoldCause.BOUND_BY_SYSTEM), hold(30 * minute, cause = HoldCause.BOUND_BY_PEER))
        assertThat(peer.screenOffHoldNoneNanos).isEqualTo(30 * minute)
        assertThat(peer.verdict).isEqualTo(WakeVerdict.SUSPICIOUS.name)
    }

    @Test fun givenBackgroundPeers_whenJudging_thenFiveAreKeepAliveAndTheWakerIsCounted() {
        val four = Array(4) { revive(trigger = ReviveTrigger.PEER_SERVICE, actor = "waker", background = true) }
        assertThat(verdict(*four).verdict).isEqualTo(WakeVerdict.NORMAL.name)
        val five = four + revive(trigger = ReviveTrigger.PEER_BROADCAST, actor = "waker", background = true)
        assertThat(verdict(*five).verdict).isEqualTo(WakeVerdict.KEEP_ALIVE.name)
        // A visible caller or a different trigger does not count.
        val mixed = four + revive(trigger = ReviveTrigger.PEER_SERVICE, actor = "waker", background = false) + revive(trigger = ReviveTrigger.SELF_PROCESS, actor = "t")
        assertThat(verdict(*mixed).peerBackgroundNoneRevives).isEqualTo(4)
        val waker = verdict(*five, revive(target = "other", trigger = ReviveTrigger.PEER_SERVICE, actor = "waker", background = true), target = "waker")
        assertThat(waker.wakesOthers).isEqualTo(2)
        assertThat(waker.revives).isEqualTo(0)
        assertThat(waker.verdict).isEqualTo(WakeVerdict.NORMAL.name)
    }

    @Test fun givenScreenOffHolds_whenJudging_thenThirtyMinutesAreSuspiciousAndTwoHoursKeepAlive() {
        assertThat(verdict(hold(30 * minute - 1)).verdict).isEqualTo(WakeVerdict.NORMAL.name)
        assertThat(verdict(hold(30 * minute)).verdict).isEqualTo(WakeVerdict.SUSPICIOUS.name)
        assertThat(verdict(hold(60 * minute), hold(60 * minute)).verdict).isEqualTo(WakeVerdict.KEEP_ALIVE.name)
        assertThat(verdict(hold(180 * minute, Justification.USER_GRANTED_ROLE)).verdict).isEqualTo(WakeVerdict.NORMAL.name)
        // A two hour foreground service, such as music or navigation, is productive.
        assertThat(verdict(hold(120 * minute, Justification.PRODUCTIVE)).verdict).isEqualTo(WakeVerdict.NORMAL.name)
    }

    @Test fun givenTrimmedHistory_whenChoosingDaysToRecompute_thenTodayIsAlwaysRecomputed() {
        val day = 86_400_000L
        val now = 20 * day + 3_600_000
        val cutoff = now - 14 * day
        // Nothing trimmed: every complete retained day.
        assertThat(WakeDetection.window(now, cutoff, cutoff + 1, cutoff + 1, utc)).isEqualTo(WakeDetectionWindow(7, 20, 7))
        // Trimmed into day 15: days 16 to 20 are complete, earlier days keep their fuller rows.
        assertThat(WakeDetection.window(now, cutoff, 15 * day + 5, cutoff + 1, utc)).isEqualTo(WakeDetectionWindow(16, 20, 7))
        // Trimmed into today: today is still recomputed from what remains.
        assertThat(WakeDetection.window(now, cutoff, 20 * day + 5, cutoff + 1, utc)).isEqualTo(WakeDetectionWindow(20, 20, 7))
        assertThat(WakeDetection.window(now, cutoff, null, null, utc)).isEqualTo(WakeDetectionWindow(7, 20, 7))
    }

    @Test fun givenFindingsOnTwoDays_whenAggregating_thenEachDayAndClassificationHasItsOwnRow() {
        val findings = listOf(
            revive(afterKill = "empty #17", at = 10),
            revive(afterKill = "empty #18", at = 20),
            revive(at = 30),
            revive(at = dayNanos + 5),
            hold(5 * minute, at = 40),
        )
        val rows = WakeDetection.daily(findings, utc)
        val killed = rows.single { it.afterKill != null }
        assertThat(killed.count).isEqualTo(2)
        assertThat(killed.afterKill).isEqualTo("empty ##")
        assertThat(killed.lastSeenNanos).isEqualTo(20)
        assertThat(rows.filter { it.trigger == "SYSTEM_BROADCAST" }.map { it.day to it.count }).containsExactly(0L to 2L, 0L to 1L, 1L to 1L)
        val held = rows.single { it.hold != null }
        assertThat(held.screenOffNanos).isEqualTo(5 * minute)
        assertThat(held.trigger).isNull()
        assertThat(rows.map { it.id }.distinct()).hasSize(rows.size)
        assertThat(WakeDetection.verdicts(findings, utc).map { it.day }.distinct()).containsExactly(0L, 1L)
    }
}
