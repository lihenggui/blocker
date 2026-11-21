// Copyright 2026 Blocker
// SPDX-License-Identifier: Apache-2.0

package com.merxury.blocker.core.data.wake

import com.google.common.truth.Truth.assertThat
import com.merxury.blocker.core.controllers.wake.WakeComponentJournal
import com.merxury.blocker.core.database.wake.WakeHistoryDao
import com.merxury.blocker.core.database.wake.WakeProtection
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
