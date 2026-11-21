// Copyright 2026 Blocker
// SPDX-License-Identifier: Apache-2.0

package com.merxury.blocker.core.domain.wake

import android.content.pm.PackageManager
import com.google.common.truth.Truth.assertThat
import com.merxury.blocker.core.controllers.root.api.ManagedComponentState
import com.merxury.blocker.core.controllers.wake.WakeAccess
import com.merxury.blocker.core.data.wake.WakeImport
import com.merxury.blocker.core.data.wake.WakeRepository
import com.merxury.blocker.core.database.wake.WakeProtection
import com.merxury.core.ifw.ManagedWakeRules
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argThat
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever

class WakeProtectionManagerTest {
    private val repository = mock<WakeRepository>()
    private val catalogue = mock<ReviewedWakePaths>()
    private val rules = mock<ManagedWakeRules>()
    private val components = mock<ManagedComponentState>()
    private val TestScope.manager get() = WakeProtectionManager(repository, catalogue, rules, components, StandardTestDispatcher(testScheduler))
    private val path = WakeProtection(
        "id", "client", "target", "target.Wake", "", "proof", false, false,
        method = "PM", previousState = PackageManager.COMPONENT_ENABLED_STATE_DEFAULT, pendingChange = true,
    )

    private suspend fun authorizedWith(vararg previous: WakeProtection, access: WakeAccess = WakeAccess.DIRECT_ROOT, profiles: List<Int> = listOf(0)) {
        whenever(repository.isOwnerProfile).thenReturn(true)
        whenever(catalogue.isCurrent(any())).thenReturn(true)
        whenever(repository.access()).thenReturn(access)
        whenever(repository.collect(any(), any())).thenReturn(WakeImport("boot", emptyList(), profiles))
        whenever(repository.firewallResets()).thenReturn(emptyMap())
        whenever(repository.protections()).thenReturn(previous.toList())
        whenever(repository.attributionRecords("boot")).thenReturn(emptyList())
        whenever(repository.observedComponents(any())).thenReturn(emptyList())
        whenever(catalogue.evaluate(any(), any(), any(), any(), any())).thenReturn(emptyList())
    }

    @Test fun waitsWithoutScanningOrChangingExistingRulesUntilPermissionArrives() = runTest {
        authorizedWith(path, access = WakeAccess.NONE)
        assertThat(manager.reconcile()).isTrue()
        verify(repository, never()).collect(any(), any())
        verify(repository, never()).protections()
        verify(repository).reportStatus(argThat { source == "authorization" && status == "WAITING_FOR_PERMISSION" })
        verifyNoInteractions(rules, components)

        val candidate = path.copy(caller = "target", configured = false, method = "IFW", pendingChange = false)
        whenever(repository.access()).thenReturn(WakeAccess.SHIZUKU_ROOT)
        whenever(catalogue.evaluate(any(), any(), any(), any(), any())).thenReturn(listOf(candidate))
        assertThat(manager.reconcile()).isTrue()
        verify(rules).replace(argThat { single().service == "target.Wake" })
    }

    @Test fun shellRecordsCandidatesWithoutWritingOrClearingExistingProtections() = runTest {
        authorizedWith(path.copy(configured = true), access = WakeAccess.SHIZUKU_SHELL)
        val candidate = path.copy(pathId = "new", configured = false, pendingChange = false)
        whenever(catalogue.evaluate(any(), any(), any(), any(), any())).thenReturn(listOf(candidate))
        assertThat(manager.reconcile()).isTrue()
        verify(repository).saveProtections(listOf(candidate))
        verify(repository).reportStatus(argThat { status == "OBSERVING_WITH_SHELL" })
        verifyNoInteractions(rules, components)
    }

    @Test fun olderRootShizukuLeavesFirewallJournalPendingUntilFileWritesCanBeAuthenticated() = runTest {
        authorizedWith(path.copy(method = "IFW", configured = true), access = WakeAccess.SHIZUKU_LEGACY_ROOT)
        assertThat(manager.reconcile()).isTrue()
        verify(repository).reportStatus(argThat { status == "IFW_REQUIRES_ROOT_OR_SHIZUKU_13" })
        verify(repository, never()).saveProtections(any())
        verifyNoInteractions(rules)
    }

    @Test fun givenUserServiceTimeoutWhenOnlyBinderDiagnosticsRemainThenRetryAndPreserveFirewall() = runTest {
        val configured = path.copy(method = "IFW", configured = true)
        for (access in listOf(WakeAccess.SHIZUKU_SHELL, WakeAccess.SHIZUKU_ROOT)) {
            authorizedWith(configured, access = access)
            whenever(repository.collect(any(), any())).thenReturn(WakeImport("boot", emptyList(), listOf(0), binderFallback = true))
            assertThat(manager.reconcile()).isFalse()
        }
        verify(repository, times(2)).reportStatus(argThat { status == "SHIZUKU_USER_SERVICE_UNAVAILABLE" })
        verify(repository, never()).saveProtections(any())
        verifyNoInteractions(rules, components)
    }

    @Test fun incompleteProfileInformationPreservesTheJournalAndRetries() = runTest {
        authorizedWith(path, profiles = emptyList())
        assertThat(manager.reconcile()).isFalse()
        verify(repository, never()).saveProtections(any())
        verifyNoInteractions(catalogue, rules, components)
    }

    @Test fun recoversInterruptedComponentWriteAndRestoresPreviousState() = runTest {
        authorizedWith(path)
        whenever(catalogue.sameInstallation(path)).thenReturn(true)
        whenever(catalogue.currentState(path)).thenReturn(PackageManager.COMPONENT_ENABLED_STATE_DISABLED)
        whenever(components.compareAndSet("target", "target.Wake", 2, 0)).thenReturn(true)
        assertThat(manager.reconcile()).isTrue()
        verify(components).compareAndSet("target", "target.Wake", 2, 0)
        verifyNoInteractions(rules)
    }

    @Test fun userChangeAndReplacementInstallationArePreserved() = runTest {
        val other = path.copy(pathId = "second", target = "replacement")
        authorizedWith(path, other)
        whenever(catalogue.sameInstallation(path)).thenReturn(true)
        whenever(catalogue.currentState(path)).thenReturn(PackageManager.COMPONENT_ENABLED_STATE_ENABLED)
        whenever(catalogue.sameInstallation(other)).thenReturn(false)
        assertThat(manager.reconcile()).isTrue()
        verifyNoInteractions(components)
    }

    @Test fun givenSecondaryProfileWhenReconcilingThenNoGlobalStateIsTouched() = runTest {
        authorizedWith(path.copy(configured = true))
        whenever(repository.isOwnerProfile).thenReturn(false)
        assertThat(manager.reconcile()).isTrue()
        verify(repository, never()).collect(any(), any())
        verifyNoInteractions(rules, components)
    }

    @Test fun givenPendingWriteWhenTheUserExplicitlyEnablesTheComponentThenTheJournalYields() = runTest {
        authorizedWith(path)
        whenever(catalogue.evaluate(any(), any(), any(), any(), any())).thenReturn(listOf(path))
        whenever(catalogue.currentState(path)).thenReturn(PackageManager.COMPONENT_ENABLED_STATE_ENABLED)
        assertThat(manager.reconcile()).isTrue()
        verify(repository).saveComponentProtection(path.copy(pendingChange = false, disabledByUser = true))
        verifyNoInteractions(components)
    }

    @Test fun givenManualOverrideWhenAFreshInstallationIsReviewedThenItsOwnEvidenceCanApply() = runTest {
        authorizedWith(path.copy(disabledByUser = true, pendingChange = false, targetRevision = "old"))
        val fresh = path.copy(pendingChange = false, previousState = null, targetRevision = "new")
        whenever(catalogue.evaluate(any(), any(), any(), any(), any())).thenReturn(listOf(fresh))
        whenever(catalogue.currentState(fresh)).thenReturn(PackageManager.COMPONENT_ENABLED_STATE_DEFAULT)
        whenever(components.compareAndSet("target", "target.Wake", 0, 2)).thenReturn(true)
        assertThat(manager.reconcile()).isTrue()
        verify(repository).saveComponentProtection(fresh.copy(configured = true, previousState = 0))
    }

    @Test fun givenOwnedComponentWhenItsInstallationUpdatesThenRestorationStateSurvives() = runTest {
        val old = path.copy(targetRevision = "old", configured = true, pendingChange = false)
        val fresh = old.copy(targetRevision = "new", configured = false, previousState = null)
        val retained = fresh.copy(configured = true, previousState = 0)
        authorizedWith(old)
        whenever(catalogue.evaluate(any(), any(), any(), any(), any())).thenReturn(listOf(fresh))
        whenever(catalogue.sameInstallation(old)).thenReturn(true)
        whenever(catalogue.currentState(retained)).thenReturn(PackageManager.COMPONENT_ENABLED_STATE_DISABLED)
        assertThat(manager.reconcile()).isTrue()
        verify(repository).saveComponentProtection(retained)
        verifyNoInteractions(components)
    }

    @Test fun givenPackageReplacedAfterCollectionWhenApplyingThenThereIsNoComponentWrite() = runTest {
        authorizedWith(path)
        whenever(catalogue.evaluate(any(), any(), any(), any(), any())).thenReturn(listOf(path))
        whenever(catalogue.isCurrent(path)).thenReturn(false)
        assertThat(manager.reconcile()).isTrue()
        verifyNoInteractions(components)
    }

    @Test fun failedRestoreKeepsTheJournalForRetry() = runTest {
        authorizedWith(path)
        whenever(catalogue.sameInstallation(path)).thenReturn(true)
        whenever(catalogue.currentState(path)).thenReturn(PackageManager.COMPONENT_ENABLED_STATE_DISABLED)
        whenever(components.compareAndSet("target", "target.Wake", 2, 0)).thenReturn(false)
        assertThat(manager.reconcile()).isFalse()
        verify(repository, never()).saveProtections(any())
    }

    @Test fun frameworkRejectingPeerChecksKeepsThatPathObservationOnly() = runTest {
        val candidate = path.copy(method = "IFW", pendingChange = false, previousState = null)
        authorizedWith()
        whenever(catalogue.evaluate(any(), any(), any(), any(), any())).thenReturn(listOf(candidate))
        whenever(rules.supportsPeerFilter()).thenReturn(false)
        assertThat(manager.reconcile()).isTrue()
        verify(rules).replace(emptyList())
        verify(repository).reportStatus(argThat { source == "protection" && status == "PEER_FILTER_UNAVAILABLE" })
        verifyNoInteractions(components)
    }

    @Test fun givenManualResetWhenClearingManagedPathsThenTheJournalAndFileAgree() = runTest {
        val first = path.copy(method = "IFW", configured = true, pendingChange = false)
        val second = first.copy(pathId = "second", target = "other")
        authorizedWith(first, second)
        val manager = manager
        manager.resetFirewall(first.target)
        verify(repository).resetFirewall(first.target)
        verify(rules).removePackage(first.target)
        manager.resetFirewall(null)
        verify(repository).resetFirewall(null)
        verify(rules).replace(emptyList())
        verifyNoInteractions(components)
    }

    @Test fun removingLastFirewallPathClearsOnlyManagedRules() = runTest {
        authorizedWith(path.copy(method = "IFW", configured = true, pendingChange = false))
        assertThat(manager.reconcile()).isTrue()
        verify(rules).replace(emptyList())
        verifyNoInteractions(components)
        val recorded = argumentCaptor<List<WakeProtection>>()
        verify(repository).saveProtections(recorded.capture())
        assertThat(recorded.firstValue.single().configured).isFalse()
    }
}
