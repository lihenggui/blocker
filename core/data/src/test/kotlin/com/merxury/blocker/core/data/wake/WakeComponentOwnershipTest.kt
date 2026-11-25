// Copyright 2026 Blocker
// SPDX-License-Identifier: Apache-2.0

package com.merxury.blocker.core.data.wake

import com.google.common.truth.Truth.assertThat
import com.merxury.blocker.core.database.wake.WakeHistoryDao
import com.merxury.blocker.core.database.wake.WakeProtection
import com.merxury.blocker.core.privileged.wake.WakeCallRefusedException
import com.merxury.blocker.core.privileged.wake.WakeComponentJournal
import com.merxury.blocker.core.privileged.wake.WakeJournalUnreadableException
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class WakeComponentOwnershipTest {
    private val dao = mock<WakeHistoryDao>()
    private val journal = mock<WakeComponentJournal>()
    private val ownership = WakeComponentOwnership(dao, journal)
    private val owned = WakeProtection(
        "path", "caller", "package", "package.Service", "", "proof", false, true, "PM",
        "1/20/10", "1/20/10", "signer", 0,
    )

    @Test fun givenClearedAppStorage_whenRecoveringOwnership_thenInstallationAndOriginalStateAreRestored() = runTest {
        whenever(dao.readProtections()).thenReturn(emptyList())
        ownership.save(owned)
        val encoded = argumentCaptor<String>()
        verify(journal).write(encoded.capture())
        whenever(journal.read()).thenReturn(encoded.firstValue)
        ownership.recover()
        verify(dao, times(2)).putProtections(listOf(owned))
    }

    @Test fun givenReadableJournal_whenProbing_thenNothingIsThrownOrWritten() = runTest {
        whenever(journal.read()).thenReturn("[]")
        ownership.probeJournal()
        verify(journal, never()).write(any())
        verify(dao, never()).putProtections(any())
    }

    @Test fun givenChannelRefusesTheJournal_whenProbing_thenTheRefusalIsThrown() = runTest {
        whenever(journal.read()).thenAnswer { throw WakeCallRefusedException("Shizuku 12 cannot run ReadWakeJournal") }
        assertThat(runCatching { ownership.probeJournal() }.exceptionOrNull()).isInstanceOf(WakeCallRefusedException::class.java)
    }

    @Test fun givenJournalThisIdentityCannotRead_whenProbing_thenItIsReportedAndNothingIsDropped() = runTest {
        whenever(journal.read()).thenAnswer { throw WakeJournalUnreadableException("owned by 0:0 with mode 600, read as uid 1000") }
        assertThat(runCatching { ownership.probeJournal() }.exceptionOrNull()).isInstanceOf(WakeJournalUnreadableException::class.java)
        verify(journal, never()).write(any())
        verify(dao, never()).putProtections(any())
    }

    @Test fun givenNoJournalYet_whenRecoveringOwnership_thenLocalEntriesAreWrittenToIt() = runTest {
        whenever(dao.readProtections()).thenReturn(listOf(owned))
        whenever(journal.read()).thenReturn(null)
        ownership.recover()
        val encoded = argumentCaptor<String>()
        verify(journal).write(encoded.capture())
        assertThat(encoded.firstValue).contains("\"pathId\":\"path\"")
        verify(dao, never()).putProtections(any())
    }

    @Test fun givenInterruptedRelease_whenRecoveringOwnership_thenLocalEntryCannotReclaimComponent() = runTest {
        whenever(dao.readProtections()).thenReturn(listOf(owned))
        whenever(journal.read()).thenReturn("[]")
        ownership.recover()
        verify(dao).putProtections(listOf(owned.copy(configured = false)))
    }

    @Test fun givenManualOverrideInJournal_whenLocalWriteWasInterrupted_thenRecoveryPreservesUserChoice() = runTest {
        whenever(dao.readProtections()).thenReturn(listOf(owned))
        val override = owned.copy(configured = false, disabledByUser = true)
        ownership.save(override)
        val encoded = argumentCaptor<String>()
        verify(journal).write(encoded.capture())
        whenever(journal.read()).thenReturn(encoded.firstValue)

        ownership.recover()

        verify(dao, times(2)).putProtections(listOf(override))
    }

    @Test fun givenUnwritableJournal_whenPreparingChange_thenNoOwnershipIsClaimed() = runTest {
        whenever(dao.readProtections()).thenReturn(emptyList())
        whenever(journal.write(any())).thenThrow(IllegalStateException("full"))
        assertThat(runCatching { ownership.save(owned) }.isFailure).isTrue()
        verify(dao, never()).putProtections(any())
    }
}
