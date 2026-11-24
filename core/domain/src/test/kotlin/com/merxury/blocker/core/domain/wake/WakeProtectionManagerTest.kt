// Copyright 2026 Blocker
// SPDX-License-Identifier: Apache-2.0

package com.merxury.blocker.core.domain.wake

import android.content.pm.PackageManager
import com.google.common.truth.Truth.assertThat
import com.merxury.blocker.core.controllers.wake.ManagedComponentState
import com.merxury.blocker.core.controllers.wake.WakeCallRefusedException
import com.merxury.blocker.core.controllers.wake.WakeJournalUnreadableException
import com.merxury.blocker.core.data.wake.ReviewedWakePaths
import com.merxury.blocker.core.data.wake.WakeComponentOwnership
import com.merxury.blocker.core.data.wake.WakeDetectionEvidence
import com.merxury.blocker.core.data.wake.WakeImport
import com.merxury.blocker.core.data.wake.WakeProtectionRepository
import com.merxury.blocker.core.data.wake.WakeRepository
import com.merxury.blocker.core.database.wake.WakeProtection
import com.merxury.blocker.core.model.component.UserId
import com.merxury.blocker.core.model.privileged.ChannelState
import com.merxury.blocker.core.model.privileged.PrivilegedIdentity
import com.merxury.blocker.core.model.privileged.UnavailableReason
import com.merxury.blocker.core.testing.controller.FakePrivilegedChannel
import com.merxury.core.ifw.ManagedWakeRules
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.argThat
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.inOrder
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever

class WakeProtectionManagerTest {
    private val repository = mock<WakeRepository>()
    private val ownership = mock<WakeComponentOwnership>()
    private val catalogue = mock<ReviewedWakePaths>()
    private val rules = mock<ManagedWakeRules>()
    private val components = mock<ManagedComponentState>()
    private val channel = FakePrivilegedChannel()
    private val userBlocks = FakePmBlockRecordDao()
    private val TestScope.manager get() = WakeProtectionManager(
        repository,
        WakeProtectionRepository(repository, ownership, catalogue, rules, components, channel, userBlocks),
        StandardTestDispatcher(testScheduler),
    )
    private val path = WakeProtection(
        "id", "client", "target", "target.Wake", "", "proof", false, false,
        method = "PM", previousState = PackageManager.COMPONENT_ENABLED_STATE_DEFAULT, pendingChange = true,
    )

    /** [state] is the privileged channel's, which both reads evidence and makes the changes. */
    private suspend fun authorizedWith(
        vararg previous: WakeProtection,
        state: ChannelState = ChannelState.Ready(PrivilegedIdentity.ROOT),
        profiles: List<Int> = listOf(0),
    ) {
        channel.setState(state)
        whenever(repository.isOwnerProfile).thenReturn(true)
        whenever(catalogue.isCurrent(any())).thenReturn(true)
        // As WakeRepository answers: the identity the channel is ready as, without a prompt.
        whenever(repository.readAccess()).thenAnswer { (channel.state.value as? ChannelState.Ready)?.identity }
        whenever(repository.collect(any(), any())).thenReturn(WakeImport("boot", emptyList(), profiles))
        whenever(repository.firewallResets()).thenReturn(emptyMap())
        whenever(repository.protections()).thenReturn(previous.toList())
        whenever(repository.attributionRecords("boot")).thenReturn(emptyList())
        whenever(repository.observedComponents(any())).thenReturn(emptyList())
        whenever(catalogue.evaluate(any(), any(), any(), any(), any())).thenReturn(emptyList())
        whenever(repository.detectionEvidence(any(), any())).thenReturn(WakeDetectionEvidence(emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), emptyMap()))
    }

    @Test fun givenChannelUnavailable_whenReconciling_thenScanningAndProtectionWaitForAuthorization() = runTest {
        for (reason in UnavailableReason.entries) {
            authorizedWith(path, state = ChannelState.Unavailable(reason))
            assertThat(manager.reconcile()).isTrue()
        }
        verify(repository, never()).collect(any(), any())
        verify(repository, never()).protections()
        verify(repository, times(UnavailableReason.entries.size)).reportStatus(argThat { source == "authorization" && status == "WAITING_FOR_PERMISSION" })
        verifyNoInteractions(rules, components, ownership)

        val candidate = path.copy(caller = "target", configured = false, method = "IFW", pendingChange = false)
        channel.setState(ChannelState.Ready(PrivilegedIdentity.ROOT))
        whenever(catalogue.evaluate(any(), any(), any(), any(), any())).thenReturn(listOf(candidate))
        assertThat(manager.reconcile()).isTrue()
        verify(repository).collect(any(), eq(PrivilegedIdentity.ROOT))
        verify(rules).replace(argThat { single().service == "target.Wake" })
    }

    @Test fun givenShellChannel_whenReconciling_thenEvidenceIsReadAsTheShellAndProtectionsArePreserved() = runTest {
        authorizedWith(path.copy(configured = true), state = ChannelState.Ready(PrivilegedIdentity.SHELL))
        val candidate = path.copy(pathId = "new", configured = false, pendingChange = false)
        whenever(catalogue.evaluate(any(), any(), any(), any(), any())).thenReturn(listOf(candidate))
        assertThat(manager.reconcile()).isTrue()
        verify(repository).collect(any(), eq(PrivilegedIdentity.SHELL))
        verify(repository).saveProtections(listOf(candidate))
        verify(repository).reportStatus(argThat { status == "OBSERVING_WITH_SHELL" })
        verifyNoInteractions(rules, components)
    }

    @Test fun givenChannelLostAfterReadAccess_whenReconciling_thenObservesOnlyAndSaysWhy() = runTest {
        for (reason in UnavailableReason.entries) {
            authorizedWith(path.copy(method = "IFW", configured = true), state = ChannelState.Unavailable(reason))
            // The channel answered for reads, then went away before the write access was decided.
            whenever(repository.readAccess()).thenReturn(PrivilegedIdentity.ROOT)
            assertThat(manager.reconcile()).isTrue()
            verify(repository).reportStatus(argThat { source == "protection" && status == "OBSERVING_WITHOUT_CHANNEL_${reason.name}" })
        }
        verify(repository, never()).saveProtections(any())
        verify(repository, never()).markRulesPending(any())
        verifyNoInteractions(rules, components, ownership)
    }

    @Test fun givenRootOrSystemChannel_whenReconciling_thenProtectionIsApplied() = runTest {
        for (identity in listOf(PrivilegedIdentity.ROOT, PrivilegedIdentity.SYSTEM)) {
            val candidate = path.copy(pathId = "ifw-$identity", caller = "target", method = "IFW", pendingChange = false, previousState = null)
            authorizedWith(state = ChannelState.Ready(identity))
            whenever(catalogue.evaluate(any(), any(), any(), any(), any())).thenReturn(listOf(candidate))
            assertThat(manager.reconcile()).isTrue()
        }
        verify(ownership, times(2)).recover()
        verify(rules, times(2)).replace(argThat { single().service == "target.Wake" })
        verify(repository, times(2)).reportStatus(argThat { source == "protection" && status == "OK" })
    }

    @Test fun givenRootChannelThatRefusesProcessCalls_whenReconciling_thenObservesNothingWithoutRetrying() = runTest {
        // Shizuku before 13 runs as root but refuses every call that needs its own process: the
        // journal probe, and each read, so nothing is read, not even the users.
        authorizedWith(path.copy(method = "IFW", configured = true), profiles = emptyList())
        whenever(ownership.probeJournal()).thenAnswer { throw WakeCallRefusedException("Shizuku 12 cannot run ReadWakeJournal") }
        whenever(repository.collect(any(), any())).thenReturn(WakeImport("boot", emptyList(), emptyList(), everyReadRefused = true))

        assertThat(manager.reconcile()).isTrue()
        assertThat(manager.resetFirewall(null)).isFalse()

        verify(repository).reportStatus(argThat { source == "protection" && status == "OBSERVING_NOTHING_READS_REFUSED" })
        verify(repository, never()).reportStatus(argThat { status == "PROFILE_SCOPE_UNAVAILABLE" })
        verify(ownership, never()).recover()
        verify(repository, never()).markRulesPending(any())
        verify(repository, never()).saveProtections(any())
        verifyNoInteractions(rules, components)
    }

    @Test fun givenRootChannelThatRefusesOnlyFileCalls_whenReconciling_thenObservesOnlyWithoutRetrying() = runTest {
        // A channel that reads but cannot reach the journal.
        authorizedWith(path.copy(method = "IFW", configured = true))
        whenever(ownership.probeJournal()).thenAnswer { throw WakeCallRefusedException("cannot run ReadWakeJournal") }

        assertThat(manager.reconcile()).isTrue()

        verify(repository).reportStatus(argThat { source == "protection" && status == "OBSERVING_WITHOUT_FILE_CALLS" })
        verify(ownership, never()).recover()
        verifyNoInteractions(rules, components)
    }

    @Test fun givenJournalThisIdentityCannotRead_whenReconciling_thenEvidenceIsCollectedAndProtectionObservesOnly() = runTest {
        // Root wrote the journal with mode 0600; the user then chose Shizuku running as the system uid.
        authorizedWith(path.copy(configured = true, pendingChange = false), state = ChannelState.Ready(PrivilegedIdentity.SYSTEM))
        whenever(ownership.probeJournal()).thenAnswer { throw WakeJournalUnreadableException("owned by 0:0 with mode 600, read as uid 1000") }

        assertThat(manager.reconcile()).isTrue()

        verify(repository).collect(any(), any())
        verify(repository).saveDetection(any(), any(), any(), any(), any())
        verify(repository).reportStatus(argThat { source == "protection" && status == "OBSERVING_WITH_UNREADABLE_JOURNAL" })
        verify(ownership, never()).recover()
        verify(ownership, never()).save(any())
        verifyNoInteractions(rules, components)
    }

    @Test fun givenJournalThisIdentityCannotRead_whenResettingFirewall_thenManagedRulesAreRemovedAndTheJournalIsLeft() = runTest {
        authorizedWith(path.copy(configured = true, pendingChange = false), state = ChannelState.Ready(PrivilegedIdentity.SYSTEM))
        whenever(ownership.probeJournal()).thenAnswer { throw WakeJournalUnreadableException("owned by 0:0 with mode 600, read as uid 1000") }

        assertThat(manager.resetFirewall(null)).isTrue()

        verify(rules).reset(null)
        verify(repository).resetFirewall(null)
        verify(ownership, never()).recover()
        verify(ownership, never()).save(any())
        verifyNoInteractions(components)
    }

    @Test fun givenAnyChannelState_whenReconciling_thenNoPromptIsRequested() = runTest {
        val states = PrivilegedIdentity.entries.map { ChannelState.Ready(it) } + UnavailableReason.entries.map { ChannelState.Unavailable(it) }
        for (state in states) {
            authorizedWith(path.copy(method = "IFW", configured = true, pendingChange = false), state = state)
            manager.reconcile()
            manager.resetFirewall(null)
        }
        assertThat(channel.connectCount).isEqualTo(0)
    }

    @Test fun givenPrivilegedProcessUnreachableDuringCapture_whenReconciling_thenRetriesWithoutChangingAnything() = runTest {
        // The user service did not attach: the channel stays ready, its reads failed.
        val configured = path.copy(method = "IFW", configured = true)
        for (identity in PrivilegedIdentity.entries) {
            authorizedWith(configured, state = ChannelState.Ready(identity))
            whenever(repository.collect(any(), any())).thenReturn(WakeImport("boot", emptyList(), listOf(0), channelUnavailable = true))
            assertThat(manager.reconcile()).isFalse()
        }
        verify(repository, times(PrivilegedIdentity.entries.size)).reportStatus(argThat { source == "protection" && status == "OBSERVING_WITHOUT_CHANNEL_FAILED" })
        verify(repository, never()).saveProtections(any())
        verify(ownership, never()).recover()
        verifyNoInteractions(rules, components)
    }

    @Test fun givenChannelGoneDuringCapture_whenReconciling_thenRetriesAndSaysWhy() = runTest {
        authorizedWith(path.copy(method = "IFW", configured = true))
        whenever(repository.collect(any(), any())).thenAnswer {
            channel.setState(ChannelState.Unavailable(UnavailableReason.NOT_RUNNING))
            WakeImport("boot", emptyList(), listOf(0), channelUnavailable = true)
        }

        assertThat(manager.reconcile()).isFalse()

        verify(repository).reportStatus(argThat { source == "protection" && status == "OBSERVING_WITHOUT_CHANNEL_NOT_RUNNING" })
        verifyNoInteractions(rules, components)
    }

    @Test fun givenUnknownProfiles_whenReconciling_thenJournalIsPreservedForRetry() = runTest {
        authorizedWith(path, profiles = emptyList())
        assertThat(manager.reconcile()).isFalse()
        verify(repository, never()).saveProtections(any())
        verifyNoInteractions(catalogue, rules, components)
    }

    @Test fun givenInterruptedComponentWrite_whenPathNoLongerQualifies_thenOriginalStateIsRestored() = runTest {
        authorizedWith(path)
        whenever(catalogue.sameInstallation(path)).thenReturn(true)
        whenever(catalogue.currentState(path)).thenReturn(PackageManager.COMPONENT_ENABLED_STATE_DISABLED)
        whenever(components.compareAndSet(eq("target"), eq("target.Wake"), eq(UserId.SYSTEM), eq(2), eq(0), any())).thenReturn(true)
        assertThat(manager.reconcile()).isTrue()
        verify(components).compareAndSet(eq("target"), eq("target.Wake"), eq(UserId.SYSTEM), eq(2), eq(0), any())
        verifyNoInteractions(rules)
    }

    @Test fun givenUserChangeOrReinstall_whenRestoring_thenComponentStateIsPreserved() = runTest {
        val other = path.copy(pathId = "second", target = "replacement")
        authorizedWith(path, other)
        whenever(catalogue.sameInstallation(path)).thenReturn(true)
        whenever(catalogue.currentState(path)).thenReturn(PackageManager.COMPONENT_ENABLED_STATE_ENABLED)
        whenever(catalogue.sameInstallation(other)).thenReturn(false)
        assertThat(manager.reconcile()).isTrue()
        verifyNoInteractions(components)
    }

    @Test fun givenSecondaryProfile_whenReconciling_thenGlobalProtectionsArePreserved() = runTest {
        authorizedWith(path.copy(configured = true))
        whenever(repository.isOwnerProfile).thenReturn(false)
        assertThat(manager.reconcile()).isTrue()
        verify(repository, never()).collect(any(), any())
        verifyNoInteractions(rules, components)
    }

    @Test fun givenPendingWrite_whenUserEnablesComponent_thenJournalRecordsManualOverride() = runTest {
        authorizedWith(path)
        whenever(catalogue.evaluate(any(), any(), any(), any(), any())).thenReturn(listOf(path))
        whenever(catalogue.currentState(path)).thenReturn(PackageManager.COMPONENT_ENABLED_STATE_ENABLED)
        assertThat(manager.reconcile()).isTrue()
        verify(ownership).save(path.copy(pendingChange = false, disabledByUser = true))
        verifyNoInteractions(components)
    }

    @Test fun givenManualOverride_whenReviewingFreshInstallation_thenNewEvidenceCanApply() = runTest {
        authorizedWith(path.copy(disabledByUser = true, pendingChange = false, targetRevision = "old"))
        val fresh = path.copy(pendingChange = false, previousState = null, targetRevision = "new")
        whenever(catalogue.evaluate(any(), any(), any(), any(), any())).thenReturn(listOf(fresh))
        whenever(catalogue.currentState(fresh)).thenReturn(PackageManager.COMPONENT_ENABLED_STATE_DEFAULT)
        whenever(components.compareAndSet(eq("target"), eq("target.Wake"), eq(UserId.SYSTEM), eq(0), eq(2), any())).thenReturn(true)
        assertThat(manager.reconcile()).isTrue()
        verify(ownership).save(fresh.copy(configured = true, previousState = 0))
    }

    @Test fun givenOwnedComponent_whenInstallationUpdates_thenOriginalStateIsRetained() = runTest {
        val old = path.copy(targetRevision = "old", configured = true, pendingChange = false)
        val fresh = old.copy(targetRevision = "new", configured = false, previousState = null)
        val retained = fresh.copy(configured = true, previousState = 0)
        authorizedWith(old)
        whenever(catalogue.evaluate(any(), any(), any(), any(), any())).thenReturn(listOf(fresh))
        whenever(catalogue.sameInstallation(old)).thenReturn(true)
        whenever(catalogue.currentState(retained)).thenReturn(PackageManager.COMPONENT_ENABLED_STATE_DISABLED)
        assertThat(manager.reconcile()).isTrue()
        verify(ownership).save(retained)
        verifyNoInteractions(components)
    }

    @Test fun givenPackageReplacedAfterCollection_whenApplyingProtection_thenComponentStateIsPreserved() = runTest {
        authorizedWith(path)
        whenever(catalogue.evaluate(any(), any(), any(), any(), any())).thenReturn(listOf(path))
        whenever(catalogue.isCurrent(path)).thenReturn(false)
        assertThat(manager.reconcile()).isTrue()
        verifyNoInteractions(components)
    }

    @Test fun givenFailedRestore_whenReconciling_thenJournalIsRetainedForRetry() = runTest {
        authorizedWith(path)
        whenever(catalogue.sameInstallation(path)).thenReturn(true)
        whenever(catalogue.currentState(path)).thenReturn(PackageManager.COMPONENT_ENABLED_STATE_DISABLED)
        whenever(components.compareAndSet(eq("target"), eq("target.Wake"), eq(UserId.SYSTEM), eq(2), eq(0), any())).thenReturn(false)
        assertThat(manager.reconcile()).isFalse()
        verify(repository, never()).saveProtections(any())
    }

    @Test fun givenUnsupportedPeerFilter_whenReconciling_thenPeerPathRemainsObservationOnly() = runTest {
        val candidate = path.copy(method = "IFW", pendingChange = false, previousState = null)
        authorizedWith()
        whenever(catalogue.evaluate(any(), any(), any(), any(), any())).thenReturn(listOf(candidate))
        whenever(rules.supportsPeerFilter()).thenReturn(false)
        assertThat(manager.reconcile()).isTrue()
        verify(rules).replace(emptyList())
        verify(repository).reportStatus(argThat { source == "protection" && status == "PEER_FILTER_UNAVAILABLE" })
        verifyNoInteractions(components)
    }

    @Test fun givenManagedRules_whenResettingFirewall_thenJournalAndFileUseTheSameScope() = runTest {
        val first = path.copy(method = "IFW", configured = true, pendingChange = false)
        val second = first.copy(pathId = "second", target = "other")
        authorizedWith(first, second)
        val manager = manager
        assertThat(manager.resetFirewall(first.target)).isTrue()
        verify(repository).resetFirewall(first.target)
        verify(rules).reset(first.target)
        assertThat(manager.resetFirewall(null)).isTrue()
        verify(repository).resetFirewall(null)
        verify(rules).reset(null)
        verifyNoInteractions(components)
    }

    @Test fun givenChannelThatCannotWrite_whenResettingFirewall_thenNothingChanges() = runTest {
        for (state in listOf(ChannelState.Ready(PrivilegedIdentity.SHELL), ChannelState.Unavailable(UnavailableReason.DENIED))) {
            authorizedWith(path.copy(method = "IFW", configured = true, pendingChange = false), state = state)
            assertThat(manager.resetFirewall(null)).isFalse()
        }
        verify(repository, never()).resetFirewall(anyOrNull())
        verify(repository, never()).markRulesPending(any())
        verifyNoInteractions(rules)
    }

    @Test fun givenReplacedFirewallPeer_whenCollectionFails_thenStaleTargetIsClearedBeforeRetry() = runTest {
        val stale = path.copy(method = "IFW", configured = true, pendingChange = false)
        val current = stale.copy(pathId = "other", target = "unrelated")
        authorizedWith(stale, current)
        whenever(catalogue.isCurrent(stale)).thenReturn(false)
        whenever(repository.collect(any(), any())).thenThrow(IllegalStateException("recorder unavailable"))

        assertThat(manager.reconcile()).isFalse()

        verify(rules).reset(stale.target)
        verify(rules, never()).reset(current.target)
        verify(repository).saveProtections(listOf(stale.copy(configured = false)))
        verify(repository, never()).resetFirewall(any())
    }

    @Test fun givenAttributedEvidence_whenReconciling_thenDetectionRunsBeforeEnforcement() = runTest {
        authorizedWith(path.copy(method = "IFW", configured = true, pendingChange = false))
        assertThat(manager.reconcile()).isTrue()
        val order = inOrder(repository, rules)
        order.verify(repository).saveAttribution(any(), any(), any())
        order.verify(repository).saveDetection(any(), any(), any(), any(), any())
        order.verify(rules).replace(emptyList())
        verify(repository).reportStatus(argThat { source == "detection" && status == "OK" })
    }

    @Test fun givenDetectionFailure_whenReconciling_thenItIsReportedAndEnforcementContinues() = runTest {
        authorizedWith(path.copy(method = "IFW", configured = true, pendingChange = false))
        whenever(repository.detectionEvidence(any(), any())).thenThrow(IllegalStateException("evidence unreadable"))
        assertThat(manager.reconcile()).isTrue()
        verify(repository).reportStatus(argThat { source == "detection" && status == "evidence unreadable" })
        verify(repository, never()).saveDetection(any(), any(), any(), any(), any())
        verify(rules).replace(emptyList())
        verify(repository).reportStatus(argThat { source == "protection" && status == "OK" })
    }

    @Test fun givenLastFirewallPathRemoved_whenReconciling_thenManagedRulesAreCleared() = runTest {
        authorizedWith(path.copy(method = "IFW", configured = true, pendingChange = false))
        assertThat(manager.reconcile()).isTrue()
        verify(rules).replace(emptyList())
        verifyNoInteractions(components)
        val recorded = argumentCaptor<List<WakeProtection>>()
        verify(repository).saveProtections(recorded.capture())
        assertThat(recorded.firstValue.single().configured).isFalse()
    }
}
