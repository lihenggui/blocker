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
    private val repository = WakeRepository(mock(), dao, mock(), mock(), journal)
    private val owned = WakeProtection(
        "path", "caller", "package", "package.Service", "", "proof", false, true, "PM",
        "1/20/10", "1/20/10", "signer", 0,
    )

    @Test fun givenClearedAppStorageWhenRecoveringThenTheOwnedInstallationAndOriginalStateReturn() = runTest {
        whenever(dao.readProtections()).thenReturn(emptyList())
        repository.saveComponentProtection(owned)
        val encoded = argumentCaptor<String>()
        verify(journal).write(encoded.capture())
        whenever(journal.read()).thenReturn(encoded.firstValue)
        repository.recoverComponentOwnership()
        verify(dao, times(2)).putProtections(listOf(owned))
    }

    @Test fun givenInterruptedReleaseWhenRecoveringThenALocalEntryCannotReclaimTheComponent() = runTest {
        whenever(dao.readProtections()).thenReturn(listOf(owned))
        whenever(journal.read()).thenReturn("[]")
        repository.recoverComponentOwnership()
        verify(dao).putProtections(listOf(owned.copy(configured = false)))
    }

    @Test fun givenUnwritableJournalWhenPreparingAChangeThenNoLocalOwnershipIsClaimed() = runTest {
        whenever(dao.readProtections()).thenReturn(emptyList())
        whenever(journal.write(any())).thenThrow(IllegalStateException("full"))
        assertThat(runCatching { repository.saveComponentProtection(owned) }.isFailure).isTrue()
        verify(dao, never()).putProtections(any())
    }
}
