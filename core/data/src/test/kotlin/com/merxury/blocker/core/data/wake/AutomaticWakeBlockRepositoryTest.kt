// Copyright 2026 Blocker
// SPDX-License-Identifier: Apache-2.0

package com.merxury.blocker.core.data.wake

import androidx.room.Room
import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.merxury.blocker.core.data.device.PmBlockRecords
import com.merxury.blocker.core.database.record.BlockerRecordsDatabase
import com.merxury.blocker.core.database.wake.WakeHistoryDatabase
import com.merxury.blocker.core.database.wake.WakeProtection
import com.merxury.blocker.core.model.component.ComponentId
import com.merxury.blocker.core.model.component.ComponentKey
import com.merxury.blocker.core.model.component.UserId
import com.merxury.blocker.core.model.privileged.ChannelState
import com.merxury.blocker.core.model.privileged.PrivilegedIdentity
import com.merxury.blocker.core.model.wake.WakeBlockFailure
import com.merxury.blocker.core.testing.controller.FakePrivilegedChannel
import com.merxury.blocker.core.testing.util.TestWakeRunRequester
import com.merxury.core.ifw.ManagedWakeRules
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], manifest = Config.NONE)
class AutomaticWakeBlockRepositoryTest {
    private val context = RuntimeEnvironment.getApplication()
    private val wake = Room.inMemoryDatabaseBuilder(context, WakeHistoryDatabase::class.java).build()
    private val records = Room.inMemoryDatabaseBuilder(context, BlockerRecordsDatabase::class.java).build()
    private val history = wake.history()
    private val pmBlocks = PmBlockRecords(records.pmBlockRecordDao())
    private val runs = TestWakeRunRequester()
    private val channel = FakePrivilegedChannel()
    private val lock = WakeRuleLock()
    private val rules = mock<ManagedWakeRules>()
    private val protection = LocalWakeProtectionRepository(mock(), mock(), mock(), rules, mock(), channel, pmBlocks, mock(), lock, mock())
    private val repository = LocalAutomaticWakeBlockRepository(history, pmBlocks, lock, runs, protection)
    private val reviewed = WakeProtection("review", "peer.app", "target.app", "target.app.Wake", "", "example", false, true)

    @After fun close() {
        wake.close()
        records.close()
    }

    @Test fun givenReviewedAndUnknownIds_whenAllowingAndReblocking_thenKnownFlagsChangeAndRunsAreRequested() = runTest {
        whenever(rules.supportsPeerFilter()).thenReturn(true)
        history.putProtections(listOf(reviewed))
        repository.blocks.test {
            val initial = awaitItem().single()
            assertThat(initial.active).isTrue()
            assertThat(initial.allowed).isFalse()
            val allowed = repository.setBlocked(setOf("review", "missing"), blocked = false)
            assertThat(allowed.outcome.done).containsExactly("review")
            assertThat(allowed.changedIds).containsExactly("review")
            assertThat(allowed.outcome.failure).isEqualTo(WakeBlockFailure.NotBlockable(listOf("missing")))
            assertThat(history.readProtections()).containsExactly(reviewed.copy(disabledByUser = true))
            assertThat(runs.requests).isEqualTo(1)
            val allowedRow = awaitItem().single()
            assertThat(allowedRow.active).isFalse()
            assertThat(allowedRow.allowed).isTrue()

            val unchanged = repository.setBlocked(setOf("review"), blocked = false)
            assertThat(unchanged.outcome.done).containsExactly("review")
            assertThat(unchanged.changedIds).isEmpty()

            assertThat(repository.setBlocked(setOf("review"), blocked = true).outcome.failure).isNull()
            assertThat(history.readProtections()).containsExactly(reviewed)
            assertThat(runs.requests).isEqualTo(3)
            assertThat(awaitItem().single()).isEqualTo(initial)
        }
    }

    @Test fun givenAnAllowedUnconfiguredProtection_whenReblocking_thenItIsVisibleBeforeReconciliation() = runTest {
        whenever(rules.supportsPeerFilter()).thenReturn(true)
        val allowed = reviewed.copy(configured = false, disabledByUser = true)
        history.putProtections(listOf(allowed))

        repository.blocks.test {
            assertThat(awaitItem().single().allowed).isTrue()
            assertThat(repository.setBlocked(setOf(allowed.pathId), blocked = true).outcome.failure).isNull()
            val pending = awaitItem().single()
            assertThat(pending.active).isTrue()
            assertThat(pending.allowed).isFalse()
            assertThat(history.readProtections().single().configured).isFalse()
        }
    }

    @Test fun givenShellOnlyAccess_whenReblocking_thenTheAllowedRowAndRunQueueDoNotChange() = runTest {
        val allowed = reviewed.copy(configured = false, disabledByUser = true)
        history.putProtections(listOf(allowed))
        channel.setState(ChannelState.Ready(PrivilegedIdentity.SHELL))

        val result = repository.setBlocked(setOf(allowed.pathId), blocked = true)

        assertThat(result.outcome.failure).isEqualTo(WakeBlockFailure.NoAccess)
        assertThat(result.outcome.done).isEmpty()
        assertThat(result.changedIds).isEmpty()
        assertThat(history.readProtections()).containsExactly(allowed)
        assertThat(runs.requests).isEqualTo(0)
    }

    @Test fun givenUnsupportedSenderFilters_whenReblockingPeerFirewallPaths_thenOnlySupportedPathsAreRequested() = runTest {
        whenever(rules.supportsPeerFilter()).thenReturn(false)
        val peer = reviewed.copy(configured = false, disabledByUser = true)
        val self = peer.copy(pathId = "self", caller = peer.target)
        history.putProtections(listOf(peer, self))

        val result = repository.setBlocked(setOf(peer.pathId, self.pathId), blocked = true)

        assertThat(result.outcome.failure).isEqualTo(WakeBlockFailure.SenderFilterUnsupported(listOf(peer.pathId)))
        assertThat(result.outcome.done).containsExactly(self.pathId)
        assertThat(result.changedIds).containsExactly(self.pathId)
        assertThat(history.readProtections()).containsExactly(peer, self.copy(disabledByUser = false, pendingChange = true))
    }

    @Test fun givenOnlyHiddenCandidatesInObserveOnlyMode_whenRestoring_thenNoAccessIsNeeded() = runTest {
        val hidden = reviewed.copy(configured = false)
        history.putProtections(listOf(hidden))
        channel.setState(ChannelState.Ready(PrivilegedIdentity.SHELL))

        val outcome = repository.allowForApp(reviewed.target)

        assertThat(outcome.failure).isNull()
        assertThat(outcome.done).isEmpty()
        assertThat(history.readProtections()).containsExactly(hidden)
        assertThat(channel.quietConnectCount).isEqualTo(0)
    }

    @Test fun givenVisibleAndHiddenRows_whenRestoringAndUndoing_thenOnlyVisibleBlocksChange() = runTest {
        whenever(rules.supportsPeerFilter()).thenReturn(true)
        val outbound = reviewed.copy(pathId = "out", caller = reviewed.target, target = reviewed.caller, configured = false, pendingChange = true)
        val hidden = reviewed.copy(pathId = "hidden", configured = false)
        val alreadyAllowed = reviewed.copy(pathId = "already", disabledByUser = true, configured = false)
        val unrelated = reviewed.copy(pathId = "other", target = "other.app")
        history.putProtections(listOf(reviewed, outbound, hidden, alreadyAllowed, unrelated))

        val outcome = repository.allowForApp(reviewed.target)

        assertThat(outcome.done).containsExactly("review", "out")
        assertThat(outcome.failure).isNull()
        assertThat(runs.requests).isEqualTo(1)
        assertThat(history.readProtections()).containsExactly(
            reviewed.copy(disabledByUser = true),
            outbound.copy(disabledByUser = true, pendingChange = false),
            hidden,
            alreadyAllowed,
            unrelated,
        )
        assertThat(repository.allowForApp(reviewed.target).done).isEmpty()
        assertThat(runs.requests).isEqualTo(1)

        val undo = repository.setBlocked(outcome.done, blocked = true)
        assertThat(undo.changedIds).containsExactly(reviewed.pathId, outbound.pathId)
        assertThat(history.readProtections()).containsExactly(reviewed, outbound, hidden, alreadyAllowed, unrelated)
    }

    @Test fun givenAutomaticPackageManagerBlock_whenUserRecordsTheSameComponent_thenOwnershipChanges() = runTest {
        history.putProtections(listOf(reviewed.copy(method = "PM", previousState = 0)))
        val id = ComponentId(reviewed.target, reviewed.component)
        repository.ownedComponents.test {
            assertThat(awaitItem()).containsExactly(id)
            pmBlocks.record(listOf(ComponentKey(id, UserId.SYSTEM)))
            assertThat(awaitItem()).isEmpty()
        }
    }
}
