// Copyright 2026 Blocker
// SPDX-License-Identifier: Apache-2.0

package com.merxury.blocker.core.domain.wake

import com.google.common.truth.Truth.assertThat
import com.merxury.blocker.core.data.device.BackgroundUsageFailure
import com.merxury.blocker.core.data.device.BackgroundUsageRepository
import com.merxury.blocker.core.data.device.BackgroundUsageResult
import com.merxury.blocker.core.data.wake.AutomaticWakeBlockRepository
import com.merxury.blocker.core.data.wake.WakeBlockRepository
import com.merxury.blocker.core.model.component.AppKey
import com.merxury.blocker.core.model.component.UserId
import com.merxury.blocker.core.model.system.BackgroundUsage
import com.merxury.blocker.core.model.wake.AutomaticWakeBlock
import com.merxury.blocker.core.model.wake.ReviveTrigger
import com.merxury.blocker.core.model.wake.WakeBlock
import com.merxury.blocker.core.model.wake.WakeBlockFailure
import com.merxury.blocker.core.model.wake.WakeBlockOutcome
import com.merxury.blocker.core.model.wake.WakePath
import com.merxury.blocker.core.model.wake.WakePathBlock
import com.merxury.blocker.core.model.wake.WakePathState
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Test

class WakeBlockUseCasesTest {
    @Test fun givenUserPaths_whenBlockingAndAllowing_thenEachUsesItsOwnMethod() = runTest {
        val f = WakeStateTestFixture(this)
        val setBlocked = SetWakePathsBlockedUseCase(f.manual, f.automatic)
        val service = finding("service").toWakePath()
        val broadcast = finding("broadcast", ReviveTrigger.PEER_BROADCAST, detail = "example.WAKE").toWakePath()
        val paths = listOf(service, broadcast)

        assertThat(setBlocked(paths.map { it.state(WakePathBlock.NONE) }, blocked = true)).isEqualTo(
            WakeBlockOutcome(setOf("service", "broadcast"), null),
        )
        assertThat(f.manual.requests).containsExactly(service to service.blockMethod, broadcast to broadcast.blockMethod)
        assertThat(setBlocked(paths.map { it.state(WakePathBlock.USER) }, blocked = false).done).containsExactly("service", "broadcast")
        assertThat(f.manual.unblockedPaths).containsExactlyElementsIn(paths)
        assertThat(f.manual.blocks.value).isEmpty()
        assertThat(f.automatic.changes).isEmpty()
    }

    @Test fun givenReviewedPaths_whenAllowingAndBlockingAgain_thenTheSameReviewIsToggled() = runTest {
        val f = WakeStateTestFixture(this)
        val setBlocked = SetWakePathsBlockedUseCase(f.manual, f.automatic)
        val path = finding("incoming").toWakePath()
        val matched = AutomaticWakeBlock("review", TEST_PEER, TEST_APP, "$TEST_APP.Wake", "example.BIND", active = true, allowed = false)
        val unapplied = matched.copy(pathId = "unapplied", active = false)
        val unmatched = AutomaticWakeBlock("unmatched", TEST_APP, TEST_PEER, "$TEST_PEER.Service", "", active = true, allowed = false)
        f.automatic.blocks.value = listOf(matched, unapplied, unmatched)

        assertThat(setBlocked(listOf(path.state(WakePathBlock.AUTOMATIC)), blocked = false).done).containsExactly("incoming")
        assertThat(setBlocked(listOf(unmatched), blocked = false).done).containsExactly("unmatched")
        assertThat(f.automatic.allowedIds).containsExactly("review", "unmatched")
        assertThat(setBlocked(listOf(path.state(WakePathBlock.NONE)), blocked = true).done).containsExactly("incoming")
        assertThat(setBlocked(listOf(unmatched), blocked = true).done).containsExactly("unmatched")
        assertThat(f.automatic.allowedIds).isEmpty()
        assertThat(f.automatic.blocks.value).containsExactly(matched, unapplied, unmatched)
        assertThat(f.automatic.changes).containsExactly(
            setOf("review") to false,
            setOf("unmatched") to false,
            setOf("review") to true,
            setOf("unmatched") to true,
        ).inOrder()
        assertThat(f.manual.requests).isEmpty()
        assertThat(f.manual.unblockedPaths).isEmpty()
    }

    @Test fun givenReviewedPathsAndStaleSelection_whenUndoingBatch_thenPreExistingBlocksStayActive() = runTest {
        val f = WakeStateTestFixture(this)
        val setBlocked = SetWakePathsBlockedUseCase(f.manual, f.automatic)
        val path = finding("incoming").toWakePath()
        val active = AutomaticWakeBlock("active", TEST_PEER, TEST_APP, "$TEST_APP.Wake", "action", active = true, allowed = false)
        val allowed = active.copy(pathId = "allowed", action = "other", active = false, allowed = true)
        val staleUnmatched = active.copy(pathId = "unmatched", caller = "other")
        f.automatic.blocks.value = listOf(active, allowed, staleUnmatched)

        val result = setBlocked.blockWithUndo(
            listOf(path.state(WakePathBlock.NONE)),
            listOf(staleUnmatched.copy(active = false, allowed = true)),
        )
        assertThat(result.outcome.done).containsExactly("incoming", "unmatched")
        assertThat(f.automatic.allowedIds).isEmpty()
        assertThat(f.manual.requests).isEmpty()

        setBlocked.undo(result.undo)
        assertThat(f.automatic.allowedIds).containsExactly("allowed")
        assertThat(f.automatic.blocks.value).containsExactly(active, allowed, staleUnmatched)
    }

    @Test fun givenAnUnappliedReview_whenBlockingItsPath_thenAUserBlockIsSaved() = runTest {
        val f = WakeStateTestFixture(this)
        val setBlocked = SetWakePathsBlockedUseCase(f.manual, f.automatic)
        val path = finding("incoming").toWakePath()
        f.automatic.blocks.value = listOf(AutomaticWakeBlock("unapplied", TEST_PEER, TEST_APP, "$TEST_APP.Wake", "", active = false, allowed = false))

        val result = setBlocked(listOf(path.state(WakePathBlock.NONE)), blocked = true)

        assertThat(result).isEqualTo(WakeBlockOutcome(setOf("incoming"), null))
        assertThat(f.manual.blocks.value).containsExactly(path.blockRow())
        assertThat(f.automatic.changes).isEmpty()
    }

    @Test fun givenAPathBlockedByBothOwners_whenAllowingIt_thenBothBlocksAreRemoved() = runTest {
        val f = WakeStateTestFixture(this)
        val setBlocked = SetWakePathsBlockedUseCase(f.manual, f.automatic)
        val path = finding("incoming").toWakePath()
        val reviewed = AutomaticWakeBlock("review", TEST_PEER, TEST_APP, "$TEST_APP.Wake", "", active = true, allowed = false)
        val unapplied = reviewed.copy(pathId = "unapplied", active = false)
        f.manual.blocks.value = listOf(path.blockRow())
        f.automatic.blocks.value = listOf(reviewed, unapplied)

        val result = setBlocked(listOf(path.state(WakePathBlock.USER)), blocked = false)

        assertThat(result).isEqualTo(WakeBlockOutcome(setOf("incoming"), null))
        assertThat(f.manual.blocks.value).isEmpty()
        assertThat(f.automatic.allowedIds).containsExactly("review")
    }

    @Test fun givenMixedPaths_whenUserWriteFails_thenAutomaticOverridesStillApply() = runTest {
        val f = WakeStateTestFixture(this)
        val setBlocked = SetWakePathsBlockedUseCase(f.manual, f.automatic)
        val manual = finding("manual").toWakePath()
        val reviewed = finding("automatic", actor = TEST_OTHER, detail = ".Other").toWakePath().copy(action = "example.BIND")
        f.manual.excluded = setOf("manual")
        val rule = AutomaticWakeBlock("review", TEST_OTHER, TEST_APP, "$TEST_APP.Other", "example.BIND", active = true, allowed = false)
        f.automatic.blocks.value = listOf(rule, rule.copy(pathId = "other-action", action = "example.OTHER"))

        val result = setBlocked(listOf(manual.state(WakePathBlock.USER), reviewed.state(WakePathBlock.AUTOMATIC)), blocked = false)

        assertThat(result).isEqualTo(WakeBlockOutcome(setOf("automatic"), WakeBlockFailure.NotBlockable(listOf("manual"))))
        assertThat(f.automatic.changes).containsExactly(setOf("review") to false)
    }

    @Test fun givenAnAppWithStoredAndAutomaticBlocks_whenRestoringAndUndoing_thenOnlyCompletedChangesAreReversed() = runTest {
        val f = WakeStateTestFixture(this)
        val background = FakeBackgroundUsageRepository(allowed = false)
        val restore = RestoreAppWakeChangesUseCase(f.activity, f.manual, f.automatic, background)
        val outbound = finding("outbound", target = TEST_PEER, actor = TEST_APP)
        f.findings.value = listOf(outbound)
        val incoming = finding("no-longer-observed").toWakePath().blockRow(at = 10)
        val outgoing = outbound.toWakePath().blockRow(at = 20)
        val oldOutgoing = finding("old-outbound", target = TEST_PEER, actor = TEST_APP).toWakePath().blockRow(at = 30)
        val failed = incoming.copy(pathKey = "failed")
        f.manual.blocks.value = listOf(incoming, outgoing, oldOutgoing, failed)
        f.manual.excluded = setOf("failed")
        val unrelated = AutomaticWakeBlock("unrelated", TEST_PEER, TEST_OTHER, "$TEST_OTHER.Service", "", active = true, allowed = false)
        val caller = AutomaticWakeBlock("caller", TEST_APP, TEST_PEER, "$TEST_PEER.Service", "", active = true, allowed = false)
        val target = AutomaticWakeBlock("target", TEST_OTHER, TEST_APP, "$TEST_APP.Service", "", active = true, allowed = false)
        val allowed = target.copy(pathId = "allowed", active = false, allowed = true)
        f.automatic.blocks.value = listOf(unrelated, caller, target, allowed)

        val result = restore(TEST_APP)

        assertThat(result.outcome.done).containsExactly("no-longer-observed", "outbound", "caller", "target", "background:$TEST_APP")
        assertThat(result.outcome.failure).isEqualTo(WakeBlockFailure.NotBlockable(listOf("failed")))
        assertThat(f.manual.blocks.value).containsExactly(oldOutgoing, failed)
        assertThat(f.automatic.allowedIds).containsExactly("caller", "target", "allowed")
        assertThat(background.allowed).isTrue()

        assertThat(restore.undo(requireNotNull(result.undo))).isEqualTo(WakeBlockOutcome(result.outcome.done, null))

        assertThat(f.manual.blocks.value).containsExactly(incoming, outgoing, oldOutgoing, failed)
        assertThat(f.automatic.allowedIds).containsExactly("allowed")
        assertThat(background.writes).containsExactly(
            AppKey(TEST_APP, UserId.SYSTEM) to true,
            AppKey(TEST_APP, UserId.SYSTEM) to false,
        ).inOrder()
    }

    @Test fun givenNoRestoreChanges_whenBackgroundReadFails_thenNoUndoOrSettingWriteIsReported() = runTest {
        val f = WakeStateTestFixture(this)
        val background = FakeBackgroundUsageRepository(allowed = false, failure = BackgroundUsageFailure.NO_ACCESS)
        val restore = RestoreAppWakeChangesUseCase(f.activity, f.manual, f.automatic, background)

        val result = restore(TEST_APP)

        assertThat(result.outcome).isEqualTo(WakeBlockOutcome(emptySet(), WakeBlockFailure.NoAccess))
        assertThat(result.undo).isNull()
        assertThat(background.writes).isEmpty()
    }

    @Test fun givenUserBlocksWereRemoved_whenRestoreIsCancelled_thenRemainingChangesAndUndoAreReturned() = runTest {
        val f = WakeStateTestFixture(this)
        val block = finding("incoming").toWakePath().blockRow()
        f.manual.blocks.value = listOf(block)
        f.automatic.blocks.value = listOf(AutomaticWakeBlock("review", TEST_PEER, TEST_APP, "$TEST_APP.Wake", "", active = true, allowed = false))
        val background = FakeBackgroundUsageRepository(allowed = false)
        val removed = CompletableDeferred<Unit>()
        val resume = CompletableDeferred<Unit>()
        val manual = object : WakeBlockRepository by f.manual {
            override suspend fun removeAppBlocks(packageName: String, outboundPathKeys: Set<String>): Pair<WakeBlockOutcome, List<WakeBlock>> = f.manual.removeAppBlocks(packageName, outboundPathKeys).also {
                removed.complete(Unit)
                resume.await()
            }
        }
        val restore = RestoreAppWakeChangesUseCase(f.activity, manual, f.automatic, background)
        var result: RestoreAppWakeChangesUseCase.Result? = null
        val caller = launch { result = restore(TEST_APP) }

        removed.await()
        caller.cancel()
        resume.complete(Unit)
        caller.join()

        assertThat(f.automatic.allowedIds).containsExactly("review")
        assertThat(background.allowed).isTrue()
        val completed = requireNotNull(result)
        assertThat(completed.outcome).isEqualTo(WakeBlockOutcome(setOf("incoming", "review", "background:$TEST_APP"), null))
        assertThat(restore.undo(requireNotNull(completed.undo))).isEqualTo(completed.outcome)
        assertThat(f.manual.blocks.value).containsExactly(block)
    }

    @Test fun givenAutomaticBlocksWereReinstated_whenUndoIsCancelled_thenBackgroundUsageAndOutcomeAreRestored() = runTest {
        val f = WakeStateTestFixture(this)
        val block = finding("incoming").toWakePath().blockRow()
        f.manual.blocks.value = listOf(block)
        f.automatic.blocks.value = listOf(AutomaticWakeBlock("review", TEST_PEER, TEST_APP, "$TEST_APP.Wake", "", active = true, allowed = false))
        val background = FakeBackgroundUsageRepository(allowed = false)
        val reinstated = CompletableDeferred<Unit>()
        val resume = CompletableDeferred<Unit>()
        val automatic = object : AutomaticWakeBlockRepository by f.automatic {
            override suspend fun setBlocked(pathIds: Set<String>, blocked: Boolean): WakeBlockOutcome = f.automatic.setBlocked(pathIds, blocked).also {
                reinstated.complete(Unit)
                resume.await()
            }
        }
        val restore = RestoreAppWakeChangesUseCase(f.activity, f.manual, automatic, background)
        val restored = restore(TEST_APP)
        var outcome: WakeBlockOutcome? = null
        val caller = launch { outcome = restore.undo(requireNotNull(restored.undo)) }

        reinstated.await()
        caller.cancel()
        resume.complete(Unit)
        caller.join()

        assertThat(background.allowed).isFalse()
        assertThat(f.manual.blocks.value).containsExactly(block)
        assertThat(f.automatic.allowedIds).isEmpty()
        assertThat(outcome).isEqualTo(restored.outcome)
    }
}

private fun WakePath.state(block: WakePathBlock) = WakePathState(this, block, null, false)

private class FakeBackgroundUsageRepository(
    var allowed: Boolean,
    private val failure: BackgroundUsageFailure? = null,
) : BackgroundUsageRepository {
    val writes = mutableListOf<Pair<AppKey, Boolean>>()

    override suspend fun read(app: AppKey): BackgroundUsageResult = failure?.let { BackgroundUsageResult.Failure(it) } ?: BackgroundUsageResult.Success(BackgroundUsage(allowed, changeable = true))

    override suspend fun setAllowed(app: AppKey, allowed: Boolean): BackgroundUsageResult {
        writes += app to allowed
        this.allowed = allowed
        return read(app)
    }
}
