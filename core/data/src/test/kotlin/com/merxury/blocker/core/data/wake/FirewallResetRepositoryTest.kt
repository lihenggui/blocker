// Copyright 2026 Blocker
// SPDX-License-Identifier: Apache-2.0

package com.merxury.blocker.core.data.wake

import com.google.common.truth.Truth.assertThat
import com.merxury.core.ifw.IIntentFirewall
import com.merxury.core.ifw.IfwFileSystem
import com.merxury.core.ifw.ManagedWakeReset
import com.merxury.core.ifw.WakeResetTransport
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever

class FirewallResetRepositoryTest {
    private val authorization = mock<WakeAuthorizationRepository>()
    private val managed = mock<ManagedWakeReset>()
    private val manual = mock<IfwFileSystem>()
    private val firewall = mock<IIntentFirewall>()
    private val repository = FirewallResetRepository(authorization, managed, manual, firewall)

    @Test fun givenShizukuOnly_whenResettingManagedRules_thenManualStoreIsSkipped() = runTest {
        whenever(authorization.resetTransport()).thenReturn(WakeResetTransport.SHIZUKU_ROOT)
        whenever(managed.resetFirewall(null, WakeResetTransport.SHIZUKU_ROOT)).thenReturn(false)

        assertThat(repository.reset(null) { _, _, _ -> error("unexpected manual deletion") }).isEqualTo(0)

        verifyNoInteractions(manual)
        verify(firewall).resetCache()
    }

    @Test fun givenTwoManualRules_whenSecondDeletionFails_thenFailurePropagatesAndCacheIsCleared() = runTest {
        val transport = WakeResetTransport.DIRECT_ROOT
        whenever(authorization.resetTransport()).thenReturn(transport)
        whenever(managed.resetFirewall(null, transport)).thenReturn(true)
        whenever(authorization.canClearManualRules(transport)).thenReturn(true)
        whenever(manual.listRuleFiles()).thenReturn(listOf("a.pkg", "b.pkg"))
        whenever(manual.deleteRules("a.pkg")).thenReturn(true)
        val failure = IllegalStateException("storage failure")
        whenever(manual.deleteRules("b.pkg")).thenThrow(failure)
        val progress = mutableListOf<String>()

        val result = runCatching { repository.reset(null) { pkg, index, total -> progress += "$pkg $index/$total" } }

        assertThat(result.exceptionOrNull()).isSameInstanceAs(failure)
        assertThat(progress).containsExactly("a.pkg 1/2", "b.pkg 2/2").inOrder()
        verify(firewall).resetCache()
    }

    @Test fun givenManualRulesAndNoDirectRoot_whenManagedResetFinishes_thenMissingPermissionIsReported() = runTest {
        whenever(authorization.resetTransport()).thenReturn(WakeResetTransport.SHIZUKU_ROOT)
        whenever(managed.resetFirewall("example.pkg", WakeResetTransport.SHIZUKU_ROOT)).thenReturn(true)
        whenever(authorization.canClearManualRules(WakeResetTransport.SHIZUKU_ROOT)).thenReturn(false)

        assertThat(repository.reset("example.pkg") { _, _, _ -> error("unexpected manual deletion") }).isNull()

        verify(managed).resetFirewall("example.pkg", WakeResetTransport.SHIZUKU_ROOT)
        verifyNoInteractions(manual)
    }
}
