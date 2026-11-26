// Copyright 2026 Blocker
// SPDX-License-Identifier: Apache-2.0

package com.merxury.blocker.core.domain.wake

import com.google.common.truth.Truth.assertThat
import com.merxury.blocker.core.data.device.BackgroundUsageFailure
import com.merxury.blocker.core.data.device.BackgroundUsageRepository
import com.merxury.blocker.core.data.device.BackgroundUsageResult
import com.merxury.blocker.core.model.component.AppKey
import com.merxury.blocker.core.model.component.UserId
import com.merxury.blocker.core.model.system.BackgroundUsage
import com.merxury.blocker.core.model.wake.AutomaticWakeBlock
import com.merxury.blocker.core.model.wake.ReviveTrigger
import com.merxury.blocker.core.model.wake.WakeBlockFailure
import com.merxury.blocker.core.model.wake.WakeBlockOutcome
import com.merxury.blocker.core.model.wake.WakePath
import com.merxury.blocker.core.model.wake.WakePathBlock
import com.merxury.blocker.core.model.wake.WakePathState
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
        val matched = AutomaticWakeBlock("review", TEST_PEER, TEST_APP, "$TEST_APP.Wake", "", true)
        val unmatched = AutomaticWakeBlock("unmatched", TEST_APP, TEST_PEER, "$TEST_PEER.Service", "", true)
        f.automatic.blocks.value = listOf(matched, unmatched)

        assertThat(setBlocked(listOf(path.state(WakePathBlock.AUTOMATIC)), blocked = false).done).containsExactly("incoming")
        assertThat(setBlocked(listOf(unmatched), blocked = false).done).containsExactly("unmatched")
        assertThat(f.automatic.allowedIds).containsExactly("review", "unmatched")
        assertThat(setBlocked(listOf(path.state(WakePathBlock.NONE)), blocked = true).done).containsExactly("incoming")
        assertThat(setBlocked(listOf(unmatched), blocked = true).done).containsExactly("unmatched")
        assertThat(f.automatic.allowedIds).isEmpty()
        assertThat(f.manual.requests).isEmpty()
        assertThat(f.manual.unblockedPaths).isEmpty()
    }

    @Test fun givenMixedPaths_whenUserWriteFails_thenAutomaticOverridesStillApply() = runTest {
        val f = WakeStateTestFixture(this)
        val setBlocked = SetWakePathsBlockedUseCase(f.manual, f.automatic)
        val manual = finding("manual").toWakePath()
        val reviewed = finding("automatic", actor = TEST_OTHER, detail = ".Other").toWakePath()
        f.manual.excluded = setOf("manual")
        f.automatic.blocks.value = listOf(AutomaticWakeBlock("review", TEST_OTHER, TEST_APP, "$TEST_APP.Other", "", true))

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
        val unrelated = AutomaticWakeBlock("unrelated", TEST_PEER, TEST_OTHER, "$TEST_OTHER.Service", "", true)
        val caller = AutomaticWakeBlock("caller", TEST_APP, TEST_PEER, "$TEST_PEER.Service", "", true)
        val target = AutomaticWakeBlock("target", TEST_OTHER, TEST_APP, "$TEST_APP.Service", "", true)
        val allowed = target.copy(pathId = "allowed", active = false)
        f.automatic.blocks.value = listOf(unrelated, caller, target, allowed)
        f.automatic.allowedIds += "allowed"

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
