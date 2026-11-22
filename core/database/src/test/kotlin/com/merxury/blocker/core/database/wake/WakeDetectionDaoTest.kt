// Copyright 2026 Blocker
// SPDX-License-Identifier: Apache-2.0

package com.merxury.blocker.core.database.wake

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.merxury.blocker.core.model.wake.SYSTEM_WAKE_EVENTS
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], manifest = Config.NONE)
class WakeDetectionDaoTest {
    private val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), WakeHistoryDatabase::class.java)
        .allowMainThreadQueries().build()
    private val dao = db.detection()

    @After fun close() = db.close()

    @Test fun givenTwoReadsOfOnePeriod_whenStoring_thenTheLatestValuesReplaceTheEarlierOnes() = runTest {
        val period = WakeHoldPeriod("2026-11-22-08-13-53", 10, 20, 1)
        dao.replacePeriod(period, listOf(WakeHoldProcess(period.periodId, "a", 10001, "a", 5, 1), WakeHoldProcess(period.periodId, "b", 10002, "b", 9, 0)), emptyList())
        dao.replacePeriod(period.copy(endMillis = 30, checkedAt = 2), listOf(WakeHoldProcess(period.periodId, "a", 10001, "a", 7, 1)), listOf(WakeHoldAssociation(period.periodId, "a", 10001, "a.S", "system", 1000, 4, 3)))
        assertThat(dao.periods(0)).containsExactly(period.copy(endMillis = 30, checkedAt = 2))
        assertThat(dao.holdProcesses(0)).containsExactly(WakeHoldProcess(period.periodId, "a", 10001, "a", 7, 1))
        assertThat(dao.holdAssociations(0)).hasSize(1)
        assertThat(dao.latestPeriod()).isEqualTo(period.periodId)
        dao.replacePeriod(WakeHoldPeriod("2026-11-22-11-13-53", 30, 40, 3), emptyList(), emptyList())
        assertThat(dao.latestPeriod()).isEqualTo("2026-11-22-11-13-53")
        dao.expireEvidence(25)
        assertThat(dao.periods(0).map { it.periodId }).containsExactly(period.periodId, "2026-11-22-11-13-53")
        dao.expireEvidence(35)
        assertThat(dao.holdProcesses(0)).isEmpty()
        assertThat(dao.holdAssociations(0)).isEmpty()
    }

    @Test fun givenStartInfoSignatures_whenImported_thenTheyAreSeenUntilTheyExpire() = runTest {
        val history = db.history()
        val record = WakeRecord("info", "7", 1, 5_000, "START_INFO", "ApplicationStartInfo", null, 10001, null, "a", null, null, 3, null, "a", "{}")
        assertThat(dao.unseen(listOf("a", "b", "a"))).containsExactly("a", "b")
        history.importStartInfo(listOf(record), listOf(WakeStartInfoSeen("a", 10)), 0)
        assertThat(dao.unseen(listOf("a", "b"))).containsExactly("b")
        assertThat(history.recent().first().map { it.id }).containsExactly("info")
        dao.expireEvidence(15)
        assertThat(dao.unseen(listOf("a", "b"))).containsExactly("a", "b")
    }

    @Test fun givenRecomputedDays_whenReplacingDetection_thenOnlyThoseDaysChangeAndOldDaysExpire() = runTest {
        fun finding(day: Long, id: String = "f$day") = WakeFindingDay(id, day, "pkg", "UNKNOWN||pkg|", "UNKNOWN", null, null, null, null, null, "NONE", "DIRECT", null, 0, 1, 0, 0, day, 0)
        fun verdict(day: Long) = WakeVerdictDay(day, "pkg", "NORMAL", 1, 1, 0, 0, 0, 0, 0, 0)
        dao.replaceDetection(1, 3, (1L..3L).map { finding(it) }, (1L..3L).map(::verdict), 1)
        dao.replaceDetection(3, 4, listOf(finding(3, "new"), finding(4), finding(9)), listOf(verdict(4)), 2)
        assertThat(dao.findings("pkg").first().map { it.id }).containsExactly("f4", "new", "f2").inOrder()
        assertThat(dao.verdicts().first().map { it.day }).containsExactly(4L, 2L).inOrder()
        assertThat(dao.findings("other").first()).isEmpty()
    }

    @Test fun givenEventsAndHistory_whenResuming_thenTheNewestEventOfThisBootAndTheOldestImportAreFound() = runTest {
        val history = db.history()
        val event = WakeRecord("e1", "7", 1, 5_000, SYSTEM_WAKE_EVENTS, "am_proc_start", null, 10001, null, "a", null, null, null, 1, "a", "{}")
        history.importRecords(listOf(event, event.copy(id = "e2", wallMillis = 6_000), event.copy(id = "o", boot = "6", wallMillis = 9_000), event.copy(id = "s", source = "statsd", wallMillis = 8_000)), 0)
        assertThat(dao.latestEvent("7")).isEqualTo(6_000)
        assertThat(dao.latestEvent("8")).isNull()
        assertThat(dao.oldestRecord()).isEqualTo(5_000)
        history.expire(5_500)
        assertThat(dao.oldestRecord()).isEqualTo(6_000)
        assertThat(dao.oldestImported()).isEqualTo(5_000)
        assertThat(dao.detectionRecords(0).map { it.id }).containsExactly("e2", "o", "s")
    }
}
