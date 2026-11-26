// Copyright 2026 Blocker
// SPDX-License-Identifier: Apache-2.0

package com.merxury.blocker.core.database.wake

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.cash.turbine.test
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
        dao.replaceDetection(1, 3, (1L..3L).map { finding(it) }, (1L..3L).map(::verdict), 1)
        dao.replaceDetection(3, 4, listOf(finding(3, "new"), finding(4), finding(9)), listOf(verdict(4)), 2)
        assertThat(dao.findingsInvolving("pkg", 0).first().map { it.id }).containsExactly("f4", "new", "f2").inOrder()
        assertThat(dao.verdictsSince(0).first().map { it.day }).containsExactly(4L, 2L).inOrder()
        assertThat(dao.findingsInvolving("other", 0).first()).isEmpty()
    }

    @Test fun givenEmptyFindings_whenInsertingAndRemovingTheOnlyFinding_thenExistenceUpdates() = runTest {
        dao.hasFindings().test {
            assertThat(awaitItem()).isFalse()
            dao.putFindings(listOf(finding(1)))
            assertThat(awaitItem()).isTrue()
            dao.clearFindings(1, 1)
            assertThat(awaitItem()).isFalse()
        }
    }

    @Test fun givenFindingsOnBothSidesOfTheCutoff_whenObservingAnApp_thenEitherRoleInTheWindowMatches() = runTest {
        val inbound = finding(2, "in", actor = "peer")
        val outbound = finding(3, "out", target = "peer", actor = "pkg")
        val self = finding(3, "self", actor = "pkg")
        val oldOutbound = outbound.copy(day = 1, id = "oldOut")
        val unrelated = finding(3, "other", target = "unrelated", actor = "peer")
        dao.findingsInvolving("pkg", 2).test {
            assertThat(awaitItem()).isEmpty()
            dao.replaceDetection(1, 3, listOf(finding(1), oldOutbound, inbound, outbound, self, unrelated), emptyList(), 0)
            assertThat(awaitItem()).containsExactly(inbound, outbound, self)
        }
    }

    @Test fun givenFindingsForSeveralApps_whenObservingSinceTheCutoff_thenAllInWindowMatch() = runTest {
        val atCutoff = finding(2)
        val later = finding(3, target = "other")
        dao.findingsSince(2).test {
            assertThat(awaitItem()).isEmpty()
            dao.replaceDetection(1, 3, listOf(finding(1), atCutoff, later), emptyList(), 0)
            assertThat(awaitItem()).containsExactly(later, atCutoff).inOrder()
        }
    }

    @Test fun givenVerdictsForSeveralApps_whenObservingSinceTheCutoff_thenAllInWindowMatch() = runTest {
        val atCutoff = verdict(2)
        val later = verdict(3).copy(target = "other")
        dao.verdictsSince(2).test {
            assertThat(awaitItem()).isEmpty()
            dao.replaceDetection(1, 3, emptyList(), listOf(verdict(1), atCutoff, later), 0)
            assertThat(awaitItem()).containsExactly(later, atCutoff).inOrder()
        }
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

    @Test fun givenStoredStealthEvidence_whenQueryingDetectionRecords_thenAllFourKindsAreIncludedInTheWindow() = runTest {
        val kinds = listOf("BalAllowed", "InteractiveStateChanged", "OverlayWindow", "AudioPlayback")
        val records = kinds.mapIndexed { index, kind ->
            WakeRecord(kind, "7", index.toLong(), 5_000, "source", kind, null, null, null, null, null, null, null, null, null, "{}")
        }
        val older = records.first().copy(id = "older", wallMillis = 4_999)
        val unrelated = records.first().copy(id = "unrelated", kind = "ProcessImportance")
        db.history().importRecords(records + older + unrelated, 0)

        assertThat(dao.detectionRecords(5_000).map { it.kind }).containsExactlyElementsIn(kinds).inOrder()
    }

    @Test fun givenWarmWakeAndAlarmEvidence_whenQueryingDetectionRecords_thenOnlyInWindowRowsAreIncluded() = runTest {
        val kinds = listOf("am_proc_died", "AppFreezeChanged", "AlarmScheduled", "PendingAlarm", "PendingIntentRegistration")
        val records = kinds.mapIndexed { index, kind ->
            WakeRecord(kind, "7", index.toLong(), 5_000, "source", kind, null, null, null, null, null, null, null, null, null, "{}")
        }
        val older = records.map { it.copy(id = "old-${it.id}", wallMillis = 4_999) }
        db.history().importRecords(records.reversed() + older, 0)

        assertThat(dao.detectionRecords(5_000)).containsExactlyElementsIn(records).inOrder()
    }

    @Test fun givenExemptionSnapshots_whenObservingTargets_thenOnlyCurrentMatchingRowsAreEmitted() = runTest {
        val app = WakeExemption("app", "system-excidle,user", 40, null, 1)
        dao.observeExemptions(listOf("app")).test {
            assertThat(awaitItem()).isEmpty()
            dao.replaceExemptions(listOf(app, app.copy(target = "other")))
            assertThat(awaitItem()).containsExactly(app)
            dao.replaceExemptions(listOf(app.copy(target = "other", checkedAt = 2)))
            assertThat(awaitItem()).isEmpty()
        }
    }

    @Test fun givenBindingSnapshots_whenObservingTargets_thenOnlyLatestRowsPerKindAndActionAreEmitted() = runTest {
        val oldA11y = binding("old-a11y", "SystemBinding", "ACCESSIBILITY", "app", 10)
        val listener = binding("listener", "SystemBinding", "LISTENERS", "app", 10)
        val admin = binding("admin", "DeviceAdmin", null, "app", 10)
        val widget = binding("widget", "WidgetProvider", null, "app", 10)
        val sync = binding("sync", "SyncAdapter", null, "app", 10)
        val unrelatedKind = binding("role", "RoleHolder", null, "app", 12)
        val history = db.history()
        history.importRecords(listOf(oldA11y, listener, admin, widget, sync, unrelatedKind), 0)

        dao.observeSpecialBindings(listOf("app")).test {
            assertThat(awaitItem()).containsExactly(oldA11y, listener, admin, widget, sync)
            // An app absent from the newest global snapshot lost its grant, even when a different app is now present.
            val newA11y = binding("new-a11y", "SystemBinding", "ACCESSIBILITY", "other", 20)
            val emptyAdmins = binding("empty-admins", "DeviceAdmin", null, null, 20)
            history.importRecords(listOf(newA11y, emptyAdmins), 0)
            assertThat(awaitItem()).containsExactly(listener, widget, sync)

            val replacementWidget = binding("replacement", "WidgetProvider", null, "app", 30)
            val secondWidget = binding("second", "WidgetProvider", null, "app", 30)
            history.importRecords(listOf(replacementWidget, secondWidget), 0)
            assertThat(awaitItem()).containsExactly(listener, replacementWidget, secondWidget, sync)
        }
        assertThat(dao.detectionRecords(0)).containsAtLeast(admin, widget, sync)
    }

    @Test fun givenOwnerAndWorkBindingSnapshots_whenObserving_thenOnlyOwnerSourcesParticipate() = runTest {
        val owner = binding("owner", "SystemBinding", "ACCESSIBILITY", "app", 10).copy(source = "ACCESSIBILITY")
        val work = owner.copy(id = "work", source = "ACCESSIBILITY/10", elapsedNanos = 20, wallMillis = 20)
        val workEmpty = work.copy(id = "work-empty", elapsedNanos = 30, wallMillis = 30, targetPackage = null, component = null)
        val otherSource = owner.copy(id = "other-source", source = "another-source", elapsedNanos = 40, wallMillis = 40)
        val history = db.history()
        history.importRecords(listOf(owner, work), 0)
        assertThat(dao.observeSpecialBindings(listOf("app")).first()).containsExactly(owner)

        history.importRecords(listOf(workEmpty, otherSource), 0)
        assertThat(dao.observeSpecialBindings(listOf("app")).first()).containsExactly(owner, otherSource)
    }

    @Test fun givenWallClockRollback_whenReadingLatestBindingSnapshot_thenAllRowsFromTheNewestCaptureWin() = runTest {
        val older = binding("old", "WidgetProvider", null, "app", 100)
        val first = older.copy(id = "first", boot = "8", elapsedNanos = 1, wallMillis = 50, component = "app/.First")
        val second = first.copy(id = "second", component = "app/.Second")
        val empty = first.copy(id = "empty", elapsedNanos = 2, wallMillis = 40, targetPackage = null, component = null)
        val history = db.history()
        history.importRecords(listOf(older), 0)
        history.importRecords(listOf(first, second), 0)
        assertThat(dao.observeSpecialBindings(listOf("app")).first()).containsExactly(first, second)

        history.importRecords(listOf(empty), 0)
        assertThat(dao.observeSpecialBindings(listOf("app")).first()).isEmpty()
    }

    private fun binding(id: String, kind: String, action: String?, target: String?, wall: Long) = WakeRecord(id, "7", wall, wall, "source", kind, null, null, null, target, target?.let { "$it/.$id" }, action, null, null, null, "{}")

    private fun finding(day: Long, id: String = "f$day", target: String = "pkg", actor: String? = null) = WakeFindingDay(id, day, target, "UNKNOWN|${actor.orEmpty()}|$target|", "UNKNOWN", null, actor, null, null, null, "NONE", "DIRECT", null, 0, 1, 0, 0, day, 0)

    private fun verdict(day: Long) = WakeVerdictDay(day, "pkg", "NORMAL", 1, 1, 0, 0, 0, 0, 0, 0)
}
