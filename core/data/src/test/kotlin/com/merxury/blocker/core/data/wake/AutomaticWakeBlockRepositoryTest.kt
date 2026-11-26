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
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
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
    private val protection = WakeProtectionRepository(mock(), mock(), mock(), mock(), mock(), channel, pmBlocks, mock(), lock, mock())
    private val repository = LocalAutomaticWakeBlockRepository(history, pmBlocks, lock, runs, protection)
    private val reviewed = WakeProtection("review", "peer.app", "target.app", "target.app.Wake", "", "example", false, true)

    @After fun close() {
        wake.close()
        records.close()
    }

    @Test fun givenReviewedAndUnknownIds_whenAllowingAndReblocking_thenKnownFlagsChangeAndRunsAreRequested() = runTest {
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

    @Test fun givenInboundOutboundAndPreviouslyAllowedRows_whenAllowingForApp_thenOnlyChangedFlagsAreReturned() = runTest {
        val outbound = reviewed.copy(pathId = "out", caller = reviewed.target, target = reviewed.caller, configured = false)
        val alreadyAllowed = reviewed.copy(pathId = "already", disabledByUser = true, configured = false)
        val unrelated = reviewed.copy(pathId = "other", target = "other.app")
        history.putProtections(listOf(reviewed, outbound, alreadyAllowed, unrelated))

        val outcome = repository.allowForApp(reviewed.target)

        assertThat(outcome.done).containsExactly("review", "out")
        assertThat(outcome.failure).isNull()
        assertThat(runs.requests).isEqualTo(1)
        assertThat(history.readProtections()).containsExactly(
            reviewed.copy(disabledByUser = true),
            outbound.copy(disabledByUser = true),
            alreadyAllowed,
            unrelated,
        )
        assertThat(repository.allowForApp(reviewed.target).done).isEmpty()
        assertThat(runs.requests).isEqualTo(1)
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
