// Copyright 2026 Blocker
// SPDX-License-Identifier: Apache-2.0

package com.merxury.blocker.core.database.wake

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.merxury.blocker.core.model.wake.SYSTEM_WAKE_EVENTS
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], manifest = Config.NONE)
class WakeHistoryDaoTest {
    @Test fun givenPartialIdentity_whenRefreshing_thenSignaturesAreFilledAndManifestIsRetained() = runTest {
        val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), WakeHistoryDatabase::class.java)
            .allowMainThreadQueries().build()
        try {
            val dao = db.history()
            val row = WakeIdentity(10012, "pkg", "version", 1, "", "[]", 1, 2)
            dao.index(listOf(row))
            dao.index(listOf(row.copy(signatures = "signer", components = "[123]", lastSeen = 3)))
            val identity = dao.identities(1).single()
            assertThat(identity.components).isEmpty()
            assertThat(identity.signatures).isEqualTo("signer")
            dao.index(listOf(identity.copy(lastSeen = 4)))
            db.openHelper.readableDatabase.query("SELECT components,lastSeen FROM WakeIdentity").use {
                assertThat(it.moveToFirst()).isTrue()
                assertThat(it.getString(0)).isEqualTo("[123]")
                assertThat(it.getLong(1)).isEqualTo(4)
            }
        } finally {
            db.close()
        }
    }

    @Test fun givenBidirectionalRequests_whenQueryingRecurringPeers_thenTwoColdStartsEachWayAreRequired() = runTest {
        val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), WakeHistoryDatabase::class.java)
            .allowMainThreadQueries().build()
        try {
            val dao = db.history()
            val forward = WakeRecord("a", "b", 10, 10, "statsd", "ServiceRequestEventReported", 10123, 10124, "client", "target", ".Service", null, 3, null, null, "{}")
            val reverse = forward.copy(id = "c", callerUid = 10124, targetUid = 10123, callerPackage = "target", targetPackage = "client")
            val requests = listOf(forward, forward.copy(id = "b"), reverse, reverse.copy(id = "d"), reverse.copy(id = "warm", startType = 1))
            val starts = requests.map { it.copy(id = it.id + "start", kind = "ProcessStartTime") }
            dao.importRecords(requests + starts, 0)
            val links = requests.map { WakeRelation(it.id, it.id + "start", "MATCHING_CALLER") }
            dao.putRelations(links.filter { it.requestId != "d" })
            assertThat(dao.recurringPeers().first()).isEmpty()
            dao.putRelations(links)
            assertThat(dao.recurringPeers().first().single()).isEqualTo(WakeReciprocalPair("client", "target", 2, 2))
            dao.replaceRelations(requests.map { it.id }, links, listOf(WakeContinuation("c", "a")))
            assertThat(dao.chains().first().single().middle).isEqualTo("target")
            dao.replaceRelations(requests.map { it.id }, emptyList(), emptyList())
            assertThat(dao.chains().first()).isEmpty()
        } finally {
            db.close()
        }
    }

    @Test fun givenManyRelations_whenReplacingThem_thenDeletesRespectSqliteBindLimit() = runTest {
        val sizes = mutableListOf<Int>()
        val callback = RoomDatabase.QueryCallback { sql, args ->
            if (sql.startsWith("DELETE FROM WakeContinuation WHERE incoming IN")) sizes.add(args.size)
        }
        val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), WakeHistoryDatabase::class.java)
            .setQueryCallback(callback, Runnable::run).allowMainThreadQueries().build()
        try {
            db.history().replaceRelations((1..700).map(Int::toString), emptyList(), emptyList())
            assertThat(sizes.sum()).isEqualTo(1400)
            assertThat(sizes.max()).isAtMost(999)
        } finally {
            db.close()
        }
    }

    @Test fun givenBindingSnapshots_whenSelectingProtectionEvidence_thenOnlyServiceRecordsWithActionsQualify() = runTest {
        val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), WakeHistoryDatabase::class.java)
            .allowMainThreadQueries().build()
        try {
            val dao = db.history()
            val binding = WakeRecord("binding", "boot", 10, 10, "SERVICES", "HeldConnection", 10111, 10122, "a", "b", "b.Service", "peer.WAKE", null, null, null, "{}")
            dao.insertRecords(listOf(binding, binding.copy(id = "provider", source = "PROVIDERS"), binding.copy(id = "unknown", action = null)))

            assertThat(dao.componentRequests(10)).containsExactly(binding)
        } finally {
            db.close()
        }
    }

    @Test fun givenMixedEventSources_whenQueryingComponentStarts_thenOnlyVerifiedSystemEventsQualify() = runTest {
        val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), WakeHistoryDatabase::class.java)
            .allowMainThreadQueries().build()
        try {
            val dao = db.history()
            val old = WakeRecord("old", "boot", 4, 1000, "EVENTS", "am_proc_start", null, 10124, null, "pkg", ".Service", null, null, 5, "pkg", "{}")
            dao.insertRecords(listOf(old, old.copy(id = "uptime", source = "EVENTS/system"), old.copy(id = "verified", source = SYSTEM_WAKE_EVENTS)))
            assertThat(dao.componentRequests(0).map { it.id }).containsExactly("verified")
        } finally {
            db.close()
        }
    }

    @Test fun givenOverlappingReports_whenReimportingAfterExpiry_thenTotalsCountEachEventOnce() = runTest {
        val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), WakeHistoryDatabase::class.java)
            .allowMainThreadQueries().build()
        try {
            val dao = db.history()
            val event = WakeRecord(
                "a", "boot", 4, 1000, "statsd", "ServiceRequestEventReported", 10123, 10124,
                "client", "target", ".Service", "action", 3, null, null, "{}",
            )
            dao.importRecords(listOf(event, event), 0)
            assertThat(dao.paths().first().single().requests).isEqualTo(1)
            dao.expire(1001)
            dao.importRecords(listOf(event), 0)
            assertThat(dao.totals().first().single().count).isEqualTo(1)
            assertThat(dao.recent().first()).isEmpty()
        } finally {
            db.close()
        }
    }
}
