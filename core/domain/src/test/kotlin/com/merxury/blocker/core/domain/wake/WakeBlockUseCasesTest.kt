// Copyright 2026 Blocker
// SPDX-License-Identifier: Apache-2.0

package com.merxury.blocker.core.domain.wake

import com.google.common.truth.Truth.assertThat
import com.merxury.blocker.core.data.device.AppRestrictionFailure
import com.merxury.blocker.core.data.device.BackgroundUsageFailure
import com.merxury.blocker.core.data.device.BackgroundUsageRepository
import com.merxury.blocker.core.data.device.BackgroundUsageResult
import com.merxury.blocker.core.data.wake.AutomaticWakeBlockRepository
import com.merxury.blocker.core.data.wake.WakeBlockRepository
import com.merxury.blocker.core.model.component.AppKey
import com.merxury.blocker.core.model.component.UserId
import com.merxury.blocker.core.model.privileged.CallFailure
import com.merxury.blocker.core.model.privileged.CallResult
import com.merxury.blocker.core.model.system.BackgroundUsage
import com.merxury.blocker.core.model.system.DeepRestrictionSnapshot
import com.merxury.blocker.core.model.wake.AutomaticWakeBlock
import com.merxury.blocker.core.model.wake.ReviveTrigger
import com.merxury.blocker.core.model.wake.WakeBlock
import com.merxury.blocker.core.model.wake.WakeBlockFailure
import com.merxury.blocker.core.model.wake.WakeBlockMethod
import com.merxury.blocker.core.model.wake.WakeBlockOutcome
import com.merxury.blocker.core.model.wake.WakePath
import com.merxury.blocker.core.model.wake.WakePathBlock
import com.merxury.blocker.core.model.wake.WakePathState
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Test

class WakeBlockUseCasesTest {
    @Test fun givenPartialBatchSuccess_whenBlockingAndUndoing_thenNoAppsAreStopped() = runTest {
        val f = WakeStateTestFixture(this)
        val setBlocked = SetWakePathsBlockedUseCase(f.manual, f.automatic, f.background)
        val paths = listOf(
            finding("one").toWakePath(),
            finding("two").toWakePath(),
            finding("failed", target = TEST_OTHER).toWakePath(),
            finding("already", target = TEST_OTHER).toWakePath(),
        )
        val reviewed = AutomaticWakeBlock("reviewed", TEST_APP, TEST_PEER, "$TEST_PEER.Wake", "", active = false, allowed = true)
        f.manual.excluded = setOf("failed")
        f.automatic.setBlocks(listOf(reviewed))
        f.appControl.running[UserId.SYSTEM] = mutableSetOf(TEST_APP, TEST_PEER, TEST_OTHER)
        f.appControl.running[UserId(10)] = mutableSetOf(TEST_APP)
        val states = paths.map { it.state(if (it.pathKey == "already") WakePathBlock.USER else WakePathBlock.NONE) }

        val result = setBlocked.blockWithUndo(states, listOf(reviewed))

        assertThat(result.outcome.done).containsExactly("one", "two", "reviewed")
        assertThat(result.outcome.failure).isEqualTo(WakeBlockFailure.NotBlockable(listOf("failed")))
        assertThat(f.appControl.calls).isEmpty()
        assertThat(f.appControl.running[UserId.SYSTEM]).containsExactly(TEST_APP, TEST_PEER, TEST_OTHER)
        assertThat(f.appControl.running[UserId(10)]).containsExactly(TEST_APP)
        assertThat(setBlocked.undo(result.undo).done).containsExactly("one", "two", "reviewed")
    }

    @Test fun givenAppsRunningInTwoProfiles_whenForceStopping_thenOnlyTheRequestedOwnerAppStops() = runTest {
        val f = WakeStateTestFixture(this)
        val forceStop = ForceStopAppUseCase(f.appControl)
        f.appControl.running[UserId.SYSTEM] = mutableSetOf(TEST_APP, TEST_PEER)
        f.appControl.running[UserId(10)] = mutableSetOf(TEST_APP)

        assertThat(forceStop(TEST_APP)).isEqualTo(CallResult.Success(Unit))
        assertThat(f.appControl.calls).containsExactly("forceStop ${AppKey(TEST_APP, UserId.SYSTEM)}")
        assertThat(f.appControl.running[UserId.SYSTEM]).containsExactly(TEST_PEER)
        assertThat(f.appControl.running[UserId(10)]).containsExactly(TEST_APP)

        val failure = CallFailure.SystemError("force stop refused")
        f.appControl.failures["forceStop"] = failure
        assertThat(forceStop(TEST_PEER)).isEqualTo(CallResult.Failure(failure))
        assertThat(f.appControl.running[UserId.SYSTEM]).containsExactly(TEST_PEER)
    }

    @Test fun givenUserPaths_whenBlockingAndAllowing_thenEachUsesItsOwnMethod() = runTest {
        val f = WakeStateTestFixture(this)
        val setBlocked = SetWakePathsBlockedUseCase(f.manual, f.automatic, f.background)
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
        val setBlocked = SetWakePathsBlockedUseCase(f.manual, f.automatic, f.background)
        val path = finding("incoming").toWakePath()
        val matched = AutomaticWakeBlock("review", TEST_PEER, TEST_APP, "$TEST_APP.Wake", "example.BIND", active = true, allowed = false)
        val unapplied = matched.copy(pathId = "unapplied", active = false)
        val unmatched = AutomaticWakeBlock("unmatched", TEST_APP, TEST_PEER, "$TEST_PEER.Service", "", active = true, allowed = false)
        f.automatic.setBlocks(listOf(matched, unapplied, unmatched))

        assertThat(setBlocked(listOf(path.state(WakePathBlock.AUTOMATIC)), blocked = false).done).containsExactly("incoming")
        assertThat(setBlocked(listOf(unmatched), blocked = false).done).containsExactly("unmatched")
        assertThat(f.automatic.allowedIds).containsExactly("review", "unmatched")
        assertThat(setBlocked(listOf(path.state(WakePathBlock.NONE)), blocked = true).done).containsExactly("incoming")
        assertThat(setBlocked(listOf(unmatched), blocked = true).done).containsExactly("unmatched")
        assertThat(f.automatic.allowedIds).isEmpty()
        assertThat(f.automatic.currentBlocks).containsExactly(matched, unapplied, unmatched)
        assertThat(f.automatic.changes).containsExactly(
            setOf("review") to false,
            setOf("unmatched") to false,
            setOf("review") to true,
            setOf("unmatched") to true,
        ).inOrder()
        assertThat(f.manual.requests).isEmpty()
        assertThat(f.manual.unblockedPaths).isEmpty()
    }

    @Test fun givenActiveMatchAndStaleAutomaticSelection_whenUndoingBatch_thenOnlyTheManualWriteIsReversed() = runTest {
        val f = WakeStateTestFixture(this)
        val setBlocked = SetWakePathsBlockedUseCase(f.manual, f.automatic, f.background)
        val path = finding("incoming").toWakePath().copy(action = "action")
        val active = AutomaticWakeBlock("active", TEST_PEER, TEST_APP, "$TEST_APP.Wake", "action", active = true, allowed = false)
        val allowed = active.copy(pathId = "allowed", action = "other", active = false, allowed = true)
        val staleUnmatched = active.copy(pathId = "unmatched", caller = "other")
        f.automatic.setBlocks(listOf(active, allowed, staleUnmatched))

        val result = setBlocked.blockWithUndo(
            listOf(path.state(WakePathBlock.NONE)),
            listOf(staleUnmatched.copy(active = false, allowed = true)),
        )
        assertThat(result.outcome.done).containsExactly("incoming", "unmatched")
        assertThat(f.automatic.allowedIds).containsExactly("allowed")
        assertThat(f.manual.requests).containsExactly(path to path.blockMethod)
        assertThat(f.manual.blocks.value).containsExactly(path.blockRow())

        setBlocked.undo(result.undo)
        assertThat(f.manual.blocks.value).isEmpty()
        assertThat(f.automatic.allowedIds).containsExactly("allowed")
        assertThat(f.automatic.currentBlocks).containsExactly(active, allowed, staleUnmatched)
    }

    @Test fun givenLapsedComponentRowAndAllowedReview_whenBlockingAndUndoing_thenBothReturnToTheirPriorState() = runTest {
        val f = WakeStateTestFixture(this)
        val setBlocked = SetWakePathsBlockedUseCase(f.manual, f.automatic, f.background)
        val path = finding("broadcast", ReviveTrigger.PEER_BROADCAST, detail = "example.WAKE").toWakePath()
        val original = path.blockRow(at = 10)
        f.manual.blocks.value = listOf(original)
        val allowed = AutomaticWakeBlock("review", TEST_PEER, TEST_APP, "$TEST_APP.Other", "", active = false, allowed = true)
        f.automatic.setBlocks(listOf(allowed))

        val result = setBlocked.blockWithUndo(listOf(path.state(WakePathBlock.NONE)), listOf(allowed))

        assertThat(result.outcome).isEqualTo(WakeBlockOutcome(setOf(path.pathKey, allowed.pathId), null))
        assertThat(result.undo.hasChanges).isTrue()
        assertThat(f.manual.requests).containsExactly(path to WakeBlockMethod.COMPONENT)
        assertThat(f.manual.blocks.value.single()).isNotEqualTo(original)
        assertThat(f.automatic.allowedIds).isEmpty()
        assertThat(setBlocked.undo(result.undo)).isEqualTo(result.outcome)
        assertThat(f.manual.blocks.value).containsExactly(original)
        assertThat(f.automatic.currentBlocks).containsExactly(allowed)
    }

    @Test fun givenManualUndoFailsWithReviewedChange_whenUndoingBatch_thenReviewedChangeIsStillReversed() = runTest {
        val f = WakeStateTestFixture(this)
        val setBlocked = SetWakePathsBlockedUseCase(f.manual, f.automatic, f.background)
        val path = finding("manual").toWakePath()
        val allowed = AutomaticWakeBlock("review", TEST_PEER, TEST_APP, "$TEST_APP.Other", "", active = false, allowed = true)
        f.automatic.setBlocks(listOf(allowed))
        val result = setBlocked.blockWithUndo(listOf(path.state(WakePathBlock.NONE)), listOf(allowed))
        f.manual.excluded = setOf(path.pathKey)

        val undo = setBlocked.undo(result.undo)

        assertThat(undo).isEqualTo(WakeBlockOutcome(setOf(allowed.pathId), WakeBlockFailure.NotBlockable(listOf(path.pathKey))))
        assertThat(f.manual.blocks.value).containsExactly(path.blockRow())
        assertThat(f.automatic.currentBlocks).containsExactly(allowed)
    }

    @Test fun givenAnUnappliedReview_whenBlockingItsPath_thenAUserBlockIsSaved() = runTest {
        val f = WakeStateTestFixture(this)
        val setBlocked = SetWakePathsBlockedUseCase(f.manual, f.automatic, f.background)
        val path = finding("incoming").toWakePath()
        f.automatic.setBlocks(listOf(AutomaticWakeBlock("unapplied", TEST_PEER, TEST_APP, "$TEST_APP.Wake", "", active = false, allowed = false)))

        val result = setBlocked(listOf(path.state(WakePathBlock.NONE)), blocked = true)

        assertThat(result).isEqualTo(WakeBlockOutcome(setOf("incoming"), null))
        assertThat(f.manual.blocks.value).containsExactly(path.blockRow())
        assertThat(f.automatic.changes).isEmpty()
    }

    @Test fun givenAPathBlockedByBothOwners_whenAllowingIt_thenBothBlocksAreRemoved() = runTest {
        val f = WakeStateTestFixture(this)
        val setBlocked = SetWakePathsBlockedUseCase(f.manual, f.automatic, f.background)
        val path = finding("incoming").toWakePath()
        val reviewed = AutomaticWakeBlock("review", TEST_PEER, TEST_APP, "$TEST_APP.Wake", "", active = true, allowed = false)
        val unapplied = reviewed.copy(pathId = "unapplied", active = false)
        f.manual.blocks.value = listOf(path.blockRow())
        f.automatic.setBlocks(listOf(reviewed, unapplied))

        val result = setBlocked(listOf(path.state(WakePathBlock.USER)), blocked = false)

        assertThat(result).isEqualTo(WakeBlockOutcome(setOf("incoming"), null))
        assertThat(f.manual.blocks.value).isEmpty()
        assertThat(f.automatic.allowedIds).containsExactly("review")
    }

    @Test fun givenMixedPaths_whenUserWriteFails_thenAutomaticOverridesStillApply() = runTest {
        val f = WakeStateTestFixture(this)
        val setBlocked = SetWakePathsBlockedUseCase(f.manual, f.automatic, f.background)
        val manual = finding("manual").toWakePath()
        val reviewed = finding("automatic", actor = TEST_OTHER, detail = ".Other").toWakePath().copy(action = "example.BIND")
        f.manual.excluded = setOf("manual")
        val rule = AutomaticWakeBlock("review", TEST_OTHER, TEST_APP, "$TEST_APP.Other", "example.BIND", active = true, allowed = false)
        f.automatic.setBlocks(listOf(rule, rule.copy(pathId = "other-action", action = "example.OTHER")))

        val result = setBlocked(listOf(manual.state(WakePathBlock.USER), reviewed.state(WakePathBlock.AUTOMATIC)), blocked = false)

        assertThat(result).isEqualTo(WakeBlockOutcome(setOf("automatic"), WakeBlockFailure.NotBlockable(listOf("manual"))))
        assertThat(f.automatic.changes).containsExactly(setOf("review") to false)
    }

    @Test fun givenBackgroundPathsAcrossTargets_whenBlockingAndAllowing_thenWritesOncePerTargetAndCompletesEveryPath() = runTest {
        val f = WakeStateTestFixture(this)
        val setBlocked = SetWakePathsBlockedUseCase(f.manual, f.automatic, f.background)
        val app = AppKey(TEST_APP, UserId.SYSTEM)
        val peer = AppKey(TEST_PEER, UserId.SYSTEM)
        val paths = listOf(backgroundPath("alarm"), backgroundPath("job"), backgroundPath("peer", TEST_PEER))
        val expected = WakeBlockOutcome(setOf("alarm", "job", "peer"), null)

        assertThat(setBlocked(paths.map { it.state(WakePathBlock.NONE) }, blocked = true)).isEqualTo(expected)
        assertThat(f.background.observe(app).first()?.allowed).isFalse()
        assertThat(f.background.observe(peer).first()?.allowed).isFalse()
        assertThat(setBlocked(paths.map { it.state(WakePathBlock.USER) }, blocked = false)).isEqualTo(expected)
        assertThat(f.background.observe(app).first()?.allowed).isTrue()
        assertThat(f.background.observe(peer).first()?.allowed).isTrue()
        assertThat(f.background.writes).containsExactly(app to false, peer to false, app to true, peer to true).inOrder()
        assertThat(f.background.reads).isEmpty()
        assertThat(f.manual.requests).isEmpty()
        assertThat(f.manual.unblockedPaths).isEmpty()
        assertThat(f.automatic.changes).isEmpty()
    }

    @Test fun givenOneTargetWasAlreadyRestricted_whenBlockingAndUndoing_thenOnlyTheNewRestrictionIsReversed() = runTest {
        val f = WakeStateTestFixture(this)
        val setBlocked = SetWakePathsBlockedUseCase(f.manual, f.automatic, f.background)
        val app = AppKey(TEST_APP, UserId.SYSTEM)
        val peer = AppKey(TEST_PEER, UserId.SYSTEM)
        f.background.sendUsage(peer, BackgroundUsage(allowed = false, changeable = true))
        val paths = listOf(backgroundPath("alarm"), backgroundPath("job"), backgroundPath("peer", TEST_PEER))

        val result = setBlocked.blockWithUndo(paths.map { it.state(WakePathBlock.NONE) }, emptyList())

        assertThat(result.outcome).isEqualTo(WakeBlockOutcome(setOf("alarm", "job", "peer"), null))
        assertThat(result.undo.hasChanges).isTrue()
        assertThat(result.undo.restrictedPackages).containsExactly(TEST_APP)
        assertThat(f.background.reads).containsExactly(app, peer).inOrder()
        assertThat(setBlocked.undo(result.undo)).isEqualTo(WakeBlockOutcome(setOf("background:$TEST_APP"), null))
        assertThat(f.background.observe(peer).first()?.allowed).isFalse()
        assertThat(f.background.writes).containsExactly(app to false, peer to false, app to true).inOrder()
    }

    @Test fun givenBackgroundWriteFailures_whenBlocking_thenReportsTheCorrespondingWakeFailure() = runTest {
        val f = WakeStateTestFixture(this)
        val setBlocked = SetWakePathsBlockedUseCase(f.manual, f.automatic, f.background)
        val paths = listOf(backgroundPath("alarm"), backgroundPath("job")).map { it.state(WakePathBlock.NONE) }
        val notBlockable = WakeBlockFailure.NotBlockable(listOf("alarm", "job"))
        val failures = mapOf(
            BackgroundUsageFailure.NO_ACCESS to WakeBlockFailure.NoAccess,
            BackgroundUsageFailure.NOT_CHANGEABLE to WakeBlockFailure.BackgroundNotChangeable,
            BackgroundUsageFailure.NOT_INSTALLED to notBlockable,
            BackgroundUsageFailure.UNSUPPORTED to notBlockable,
            BackgroundUsageFailure.SYSTEM_ERROR to WakeBlockFailure.SystemError(null),
        )

        for ((error, failure) in failures) {
            f.background.writeFailure = error
            val result = setBlocked.blockWithUndo(paths, emptyList())
            assertThat(result.outcome).isEqualTo(WakeBlockOutcome(emptySet(), failure))
            assertThat(result.undo.hasChanges).isFalse()
        }
    }

    @Test fun givenBackgroundWriteReturnsAnUnchangedSetting_whenBlocking_thenReportsNotChangeableWithoutUndo() = runTest {
        val f = WakeStateTestFixture(this)
        val background = object : BackgroundUsageRepository by f.background {
            override suspend fun setAllowed(app: AppKey, allowed: Boolean) = f.background.read(app)
        }
        val setBlocked = SetWakePathsBlockedUseCase(f.manual, f.automatic, background)

        val result = setBlocked.blockWithUndo(listOf(backgroundPath("alarm").state(WakePathBlock.NONE)), emptyList())

        assertThat(result.outcome).isEqualTo(WakeBlockOutcome(emptySet(), WakeBlockFailure.BackgroundNotChangeable))
        assertThat(result.undo.hasChanges).isFalse()
    }

    @Test fun givenBackgroundUndoLosesAccess_whenUndoingMixedBlocks_thenReportsFailureAndKeepsCompletedRuleUndo() = runTest {
        val f = WakeStateTestFixture(this)
        val setBlocked = SetWakePathsBlockedUseCase(f.manual, f.automatic, f.background)
        val paths = listOf(finding("incoming").toWakePath(), backgroundPath("alarm"))
        val result = setBlocked.blockWithUndo(paths.map { it.state(WakePathBlock.NONE) }, emptyList())
        f.background.writeFailure = BackgroundUsageFailure.NO_ACCESS

        assertThat(setBlocked.undo(result.undo)).isEqualTo(WakeBlockOutcome(setOf("incoming"), WakeBlockFailure.NoAccess))
        assertThat(f.manual.blocks.value).isEmpty()
        assertThat(f.background.observe(AppKey(TEST_APP, UserId.SYSTEM)).first()?.allowed).isFalse()
    }

    @Test fun givenAnAppWithStoredAndAutomaticBlocks_whenRestoringAndUndoing_thenOnlyCompletedChangesAreReversed() = runTest {
        val f = WakeStateTestFixture(this)
        val background = FakeBackgroundUsageRepository(allowed = false)
        val restore = RestoreAppWakeChangesUseCase(f.activity, f.manual, f.automatic, background, f.restriction)
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
        f.automatic.setBlocks(listOf(unrelated, caller, target, allowed))

        val result = restore(TEST_APP)

        assertThat(result.outcome.done).containsExactly("no-longer-observed", "outbound", "old-outbound", "caller", "target", "background:$TEST_APP")
        assertThat(result.outcome.failure).isEqualTo(WakeBlockFailure.NotBlockable(listOf("failed")))
        assertThat(f.manual.blocks.value).containsExactly(failed)
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

    @Test fun givenUnsupportedBackgroundUsage_whenRestoring_thenOtherChangesSucceed() = runTest {
        val f = WakeStateTestFixture(this)
        val block = finding("incoming").toWakePath().blockRow()
        f.manual.blocks.value = listOf(block)
        val background = FakeBackgroundUsageRepository(allowed = false, failure = BackgroundUsageFailure.UNSUPPORTED)
        val restore = RestoreAppWakeChangesUseCase(f.activity, f.manual, f.automatic, background, f.restriction)

        val result = restore(TEST_APP)

        assertThat(result.outcome).isEqualTo(WakeBlockOutcome(setOf(block.pathKey), null))
        assertThat(restore.undo(requireNotNull(result.undo))).isEqualTo(result.outcome)
        assertThat(f.manual.blocks.value).containsExactly(block)
        assertThat(background.writes).isEmpty()
    }

    @Test fun givenOwnedAndUnownedDeepRestrictions_whenRestoringAndUndoing_thenOnlyOwnedValuesChange() = runTest {
        val f = WakeStateTestFixture(this)
        val app = AppKey(TEST_APP, UserId.SYSTEM)
        val unowned = AppKey(TEST_PEER, UserId.SYSTEM)
        val restore = RestoreAppWakeChangesUseCase(f.activity, f.manual, f.automatic, f.background, f.restriction)
        val baseline = DeepRestrictionSnapshot(30, 0, 1)
        val restricted = DeepRestrictionSnapshot(45, 1, 1)
        val partlyRestricted = restricted.copy(standbyBucket = 20)
        f.restriction.sendSnapshot(app, baseline)
        f.restriction.sendSnapshot(unowned, restricted)
        f.restriction.setDeepRestricted(app, true)
        f.restriction.sendSnapshot(app, partlyRestricted)

        assertThat(restore(TEST_PEER).undo).isNull()
        assertThat(f.restriction.snapshot(unowned)).isEqualTo(restricted)

        val result = restore(TEST_APP)

        assertThat(result.outcome).isEqualTo(WakeBlockOutcome(setOf("deep-background:$TEST_APP"), null))
        assertThat(f.restriction.snapshot(app)).isEqualTo(baseline.copy(standbyBucket = 20))
        val undo = requireNotNull(result.undo)
        assertThat(undo.deepRestrictionRestore?.reapply).isEqualTo(partlyRestricted)
        assertThat(restore.undo(undo)).isEqualTo(result.outcome)
        assertThat(f.restriction.snapshot(app)).isEqualTo(partlyRestricted)
        assertThat(f.restriction.writes).containsExactly(app to true, app to false, app to true).inOrder()
        assertThat(f.background.writes).isEmpty()
    }

    @Test fun givenDeepRestrictionRestoreFails_whenOtherBlocksAreRemoved_thenUndoOnlyRecordsCompletedChanges() = runTest {
        val f = WakeStateTestFixture(this)
        val app = AppKey(TEST_APP, UserId.SYSTEM)
        val restore = RestoreAppWakeChangesUseCase(f.activity, f.manual, f.automatic, f.background, f.restriction)
        f.restriction.setDeepRestricted(app, true)
        f.restriction.writes.clear()
        f.restriction.writeFailure = AppRestrictionFailure.SYSTEM_ERROR
        f.manual.blocks.value = listOf(finding("path").toWakePath().blockRow())

        val result = restore(TEST_APP)

        assertThat(result.outcome.done).containsExactly("path")
        assertThat(result.outcome.failure).isInstanceOf(WakeBlockFailure.SystemError::class.java)
        val undo = requireNotNull(result.undo)
        assertThat(undo.deepRestrictionRestore).isNull()
        assertThat(restore.undo(undo)).isEqualTo(WakeBlockOutcome(setOf("path"), null))
        assertThat(f.restriction.writes).containsExactly(app to false)
    }

    @Test fun givenNoRestoreChanges_whenBackgroundReadFails_thenNoUndoOrSettingWriteIsReported() = runTest {
        val f = WakeStateTestFixture(this)
        val background = FakeBackgroundUsageRepository(allowed = false, failure = BackgroundUsageFailure.NO_ACCESS)
        val restore = RestoreAppWakeChangesUseCase(f.activity, f.manual, f.automatic, background, f.restriction)

        val result = restore(TEST_APP)

        assertThat(result.outcome).isEqualTo(WakeBlockOutcome(emptySet(), WakeBlockFailure.NoAccess))
        assertThat(result.undo).isNull()
        assertThat(background.writes).isEmpty()
    }

    @Test fun givenUserBlocksWereRemoved_whenRestoreIsCancelled_thenRemainingChangesAndUndoAreReturned() = runTest {
        val f = WakeStateTestFixture(this)
        val block = finding("incoming").toWakePath().blockRow()
        f.manual.blocks.value = listOf(block)
        f.automatic.setBlocks(listOf(AutomaticWakeBlock("review", TEST_PEER, TEST_APP, "$TEST_APP.Wake", "", active = true, allowed = false)))
        val background = FakeBackgroundUsageRepository(allowed = false)
        val removed = CompletableDeferred<Unit>()
        val resume = CompletableDeferred<Unit>()
        val manual = object : WakeBlockRepository by f.manual {
            override suspend fun removeAppBlocks(packageName: String, outboundPathKeys: Set<String>): Pair<WakeBlockOutcome, List<WakeBlock>> = f.manual.removeAppBlocks(packageName, outboundPathKeys).also {
                removed.complete(Unit)
                resume.await()
            }
        }
        val restore = RestoreAppWakeChangesUseCase(f.activity, manual, f.automatic, background, f.restriction)
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
        f.automatic.setBlocks(listOf(AutomaticWakeBlock("review", TEST_PEER, TEST_APP, "$TEST_APP.Wake", "", active = true, allowed = false)))
        val background = FakeBackgroundUsageRepository(allowed = false)
        val reinstated = CompletableDeferred<Unit>()
        val resume = CompletableDeferred<Unit>()
        val automatic = object : AutomaticWakeBlockRepository by f.automatic {
            override suspend fun setBlocked(pathIds: Set<String>, blocked: Boolean): AutomaticWakeBlockRepository.ChangeResult = f.automatic.setBlocked(pathIds, blocked).also {
                reinstated.complete(Unit)
                resume.await()
            }
        }
        val restore = RestoreAppWakeChangesUseCase(f.activity, f.manual, automatic, background, f.restriction)
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

private fun backgroundPath(key: String, target: String = TEST_APP) = finding(key, ReviveTrigger.ALARM, target, actor = null, detail = null).toWakePath()

private class FakeBackgroundUsageRepository(
    allowed: Boolean,
    private val failure: BackgroundUsageFailure? = null,
) : BackgroundUsageRepository {
    private val state = MutableStateFlow(allowed)
    val allowed get() = state.value
    val writes = mutableListOf<Pair<AppKey, Boolean>>()

    override fun observe(app: AppKey) = state.map { BackgroundUsage(it, changeable = true).takeIf { failure == null } }

    override suspend fun read(app: AppKey): BackgroundUsageResult = failure?.let { BackgroundUsageResult.Failure(it) } ?: BackgroundUsageResult.Success(BackgroundUsage(allowed, changeable = true))

    override suspend fun setAllowed(app: AppKey, allowed: Boolean): BackgroundUsageResult {
        writes += app to allowed
        state.value = allowed
        return read(app)
    }
}
