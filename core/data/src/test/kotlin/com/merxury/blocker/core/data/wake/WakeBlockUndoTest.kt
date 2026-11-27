// Copyright 2026 Blocker
// SPDX-License-Identifier: Apache-2.0

package com.merxury.blocker.core.data.wake

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.os.Build
import android.provider.Settings
import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.merxury.blocker.core.data.device.ComponentStatusRepository
import com.merxury.blocker.core.data.device.DeviceRepositoryTestBase
import com.merxury.blocker.core.data.device.PmBlockClaimObserver
import com.merxury.blocker.core.data.device.PmBlockRecords
import com.merxury.blocker.core.data.device.installedApp
import com.merxury.blocker.core.data.history.OperationScope
import com.merxury.blocker.core.data.wake.WakeBlockTestValues.ACTION
import com.merxury.blocker.core.data.wake.WakeBlockTestValues.ACTIVITY
import com.merxury.blocker.core.data.wake.WakeBlockTestValues.COMPONENT
import com.merxury.blocker.core.data.wake.WakeBlockTestValues.FIREWALL
import com.merxury.blocker.core.data.wake.WakeBlockTestValues.OTHER_ACTION
import com.merxury.blocker.core.data.wake.WakeBlockTestValues.OTHER_RECEIVER
import com.merxury.blocker.core.data.wake.WakeBlockTestValues.OTHER_SERVICE
import com.merxury.blocker.core.data.wake.WakeBlockTestValues.PEER
import com.merxury.blocker.core.data.wake.WakeBlockTestValues.PEER_UID
import com.merxury.blocker.core.data.wake.WakeBlockTestValues.PROVIDER
import com.merxury.blocker.core.data.wake.WakeBlockTestValues.RECEIVER
import com.merxury.blocker.core.data.wake.WakeBlockTestValues.SERVICE
import com.merxury.blocker.core.data.wake.WakeBlockTestValues.TARGET
import com.merxury.blocker.core.database.wake.WakeBlockDao
import com.merxury.blocker.core.database.wake.WakeBlockEntity
import com.merxury.blocker.core.database.wake.WakeProtection
import com.merxury.blocker.core.model.ComponentType
import com.merxury.blocker.core.model.component.AppKey
import com.merxury.blocker.core.model.component.ComponentDeclaration
import com.merxury.blocker.core.model.component.ComponentId
import com.merxury.blocker.core.model.component.ComponentKey
import com.merxury.blocker.core.model.component.ComponentStatus
import com.merxury.blocker.core.model.component.ControlLayer
import com.merxury.blocker.core.model.component.ControlState
import com.merxury.blocker.core.model.component.IfwBlock
import com.merxury.blocker.core.model.component.PmSetting
import com.merxury.blocker.core.model.component.UserId
import com.merxury.blocker.core.model.control.ComponentOutcome
import com.merxury.blocker.core.model.control.ComponentWriteObserver
import com.merxury.blocker.core.model.control.ControlEvent
import com.merxury.blocker.core.model.control.ControlFailure
import com.merxury.blocker.core.model.control.ControlGoal
import com.merxury.blocker.core.model.control.ControlRequest
import com.merxury.blocker.core.model.control.ControlResult
import com.merxury.blocker.core.model.privileged.ChannelState
import com.merxury.blocker.core.model.privileged.PrivilegedIdentity
import com.merxury.blocker.core.model.privileged.UnavailableReason
import com.merxury.blocker.core.model.system.ReadFailure
import com.merxury.blocker.core.model.system.ReadResult
import com.merxury.blocker.core.model.wake.HoldCause
import com.merxury.blocker.core.model.wake.ReviveTrigger
import com.merxury.blocker.core.model.wake.WakeBlock
import com.merxury.blocker.core.model.wake.WakeBlockFailure
import com.merxury.blocker.core.model.wake.WakeBlockMethod
import com.merxury.blocker.core.model.wake.WakeBlockOutcome
import com.merxury.blocker.core.model.wake.WakePath
import com.merxury.blocker.core.privileged.PrivilegedChannel
import com.merxury.blocker.core.system.SystemPackageReader
import com.merxury.blocker.core.testing.controller.FakePrivilegedChannel
import com.merxury.blocker.core.testing.repository.device.FakeComponentStatusRepository
import com.merxury.blocker.core.testing.repository.wake.FakeAutomaticWakeBlockRepository
import com.merxury.blocker.core.testing.util.TestWakeRunRequester
import com.merxury.core.ifw.ManagedWakeRules
import com.merxury.core.ifw.WakeRuleFileKind
import com.merxury.core.ifw.WakeRuleWriter
import com.merxury.core.ifw.model.IfwComponentType
import com.merxury.core.ifw.model.IfwFilter
import com.merxury.core.ifw.xml.IfwXmlDeserializer
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.io.IOException

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], manifest = Config.NONE)
internal class WakeBlockUndoTest : WakeBlockTestFixture() {
    @Test fun givenOneAlreadyDisabledReceiver_whenUndoingBatch_thenOnlyTheNewlyDisabledReceiverIsUnblocked() = runTest {
        val repository = repository()
        val receiver = declaration(RECEIVER, ComponentType.RECEIVER)
        val other = declaration(OTHER_RECEIVER, ComponentType.RECEIVER)
        receivers.matches[TARGET to ACTION] = listOf(receiver, other)
        components.sendComponents(
            UserId.SYSTEM,
            listOf(
                ComponentStatus(receiver, ControlState(PmSetting.DISABLED, IfwBlock.NOT_BLOCKED)),
                ComponentStatus(other, ControlState(PmSetting.DEFAULT, IfwBlock.NOT_BLOCKED)),
            ),
        )

        val (outcome, undo) = repository.blockRecordingChanges(listOf(actionPath("group") to COMPONENT))
        assertThat(outcome.done).containsExactly("group")
        assertThat(undo.hasChanges).isTrue()
        assertThat(repository.undoBlockChanges(undo).done).containsExactly("group")
        assertThat(dao.all()).isEmpty()
        assertThat(components.component(ComponentKey(receiver.id, UserId.SYSTEM)).first()?.state?.pm).isEqualTo(PmSetting.DISABLED)
        assertThat(components.component(ComponentKey(other.id, UserId.SYSTEM)).first()?.state?.pm).isEqualTo(PmSetting.DEFAULT)
    }

    @Test fun givenLapsedComponentRow_whenReapplyingAndUndoing_thenExactSettingAndOldRowReturn() = runTest {
        val repository = repository()
        val receiver = declaration(RECEIVER, ComponentType.RECEIVER).copy(enabledInManifest = false)
        val path = path("lapsed", trigger = ReviveTrigger.PEER_BROADCAST, component = RECEIVER, method = COMPONENT)
        val original = WakeBlockEntity(path.pathKey, TARGET, COMPONENT.name, null, null, RECEIVER, null, 12)
        dao.upsert(listOf(original))
        sendPm(receiver to PmSetting.ENABLED)

        val (outcome, undo) = repository.blockRecordingChanges(listOf(path to COMPONENT))

        assertThat(outcome).isEqualTo(WakeBlockOutcome(setOf(path.pathKey), null))
        assertThat(pm(receiver)).isEqualTo(PmSetting.DISABLED)
        assertThat(dao.all().single()).isNotEqualTo(original)
        assertThat(repository.undoBlockChanges(undo)).isEqualTo(outcome)
        assertThat(pm(receiver)).isEqualTo(PmSetting.ENABLED)
        assertThat(dao.all()).containsExactly(original)
    }

    @Test fun givenExistingActionBlockAndNewReceiver_whenReapplyingAndUndoing_thenOnlyNewReceiverIsRestored() = runTest {
        val repository = repository()
        val old = declaration(RECEIVER, ComponentType.RECEIVER)
        val added = declaration(OTHER_RECEIVER, ComponentType.RECEIVER)
        val path = actionPath("updated")
        receivers.matches[TARGET to ACTION] = listOf(old)
        repository.block(listOf(path to COMPONENT))
        val original = dao.all().single()
        receivers.matches[TARGET to ACTION] = listOf(old, added)

        val (outcome, undo) = repository.blockRecordingChanges(listOf(path to COMPONENT))

        assertThat(outcome).isEqualTo(WakeBlockOutcome(setOf(path.pathKey), null))
        assertThat(pm(added)).isEqualTo(PmSetting.DISABLED)
        assertThat(repository.undoBlockChanges(undo)).isEqualTo(outcome)
        assertThat(pm(old)).isEqualTo(PmSetting.DISABLED)
        assertThat(pm(added)).isEqualTo(PmSetting.DEFAULT)
        assertThat(dao.all()).containsExactly(original)
    }

    @Test fun givenPartialBatchWithStoredRow_whenRetryingAndUndoing_thenStateReturnsToThePartialBatch() = runTest {
        val repository = repository()
        val first = declaration(RECEIVER, ComponentType.RECEIVER)
        val second = declaration(OTHER_RECEIVER, ComponentType.RECEIVER)
        val path = actionPath("partial")
        receivers.matches[TARGET to ACTION] = listOf(first, second)
        val refused = ControlFailure.StateNotConfirmed
        components.controlEvents = {
            sendPm(first to PmSetting.DISABLED, second to PmSetting.DEFAULT)
            val blocked = ComponentOutcome(first.key, ControlState(PmSetting.DISABLED, IfwBlock.NOT_BLOCKED), ControlResult.ACHIEVED, emptyMap())
            val failed = ComponentOutcome(second.key, ControlState(PmSetting.DEFAULT, IfwBlock.NOT_BLOCKED), ControlResult.FAILED, mapOf(ControlLayer.PACKAGE_MANAGER to refused))
            listOf(ControlEvent.PackageDone(TARGET, listOf(blocked, failed), setOf(first.id)))
        }

        val (partial, partialUndo) = repository.blockRecordingChanges(listOf(path to COMPONENT))
        val original = dao.all().single()
        assertThat(partial).isEqualTo(WakeBlockOutcome(emptySet(), WakeBlockFailure.SystemError(null)))
        assertThat(partialUndo.hasChanges).isTrue()
        components.controlEvents = null

        val (retried, undoRetry) = repository.blockRecordingChanges(listOf(path to COMPONENT))

        assertThat(retried).isEqualTo(WakeBlockOutcome(setOf(path.pathKey), null))
        assertThat(pm(second)).isEqualTo(PmSetting.DISABLED)
        assertThat(repository.undoBlockChanges(undoRetry)).isEqualTo(retried)
        assertThat(pm(first)).isEqualTo(PmSetting.DISABLED)
        assertThat(pm(second)).isEqualTo(PmSetting.DEFAULT)
        assertThat(dao.all()).containsExactly(original)
        assertThat(repository.undoBlockChanges(partialUndo)).isEqualTo(retried)
        assertThat(pm(first)).isEqualTo(PmSetting.DEFAULT)
        assertThat(dao.all()).isEmpty()
    }

    @Test fun givenLapsedOlderRow_whenUndoingAnotherPath_thenOlderRowDoesNotKeepTheReceiverDisabled() = runTest {
        val repository = repository()
        val receiver = declaration(RECEIVER, ComponentType.RECEIVER)
        val older = WakeBlockEntity("older", TARGET, COMPONENT.name, null, null, RECEIVER, null, 17)
        dao.upsert(listOf(older))

        val (_, undo) = repository.blockRecordingChanges(listOf(path("new", component = RECEIVER, method = COMPONENT) to COMPONENT))
        assertThat(pm(receiver)).isEqualTo(PmSetting.DISABLED)

        assertThat(repository.undoBlockChanges(undo)).isEqualTo(WakeBlockOutcome(setOf("new"), null))
        assertThat(pm(receiver)).isEqualTo(PmSetting.DEFAULT)
        assertThat(dao.all()).containsExactly(older)
    }

    @Test fun givenRuntimeSettingChangedAfterBlock_whenUndoing_thenOnlyStillDisabledSettingsAreRestored() = runTest {
        val repository = repository()
        val first = declaration(RECEIVER, ComponentType.RECEIVER)
        val second = declaration(OTHER_RECEIVER, ComponentType.RECEIVER)
        receivers.matches[TARGET to ACTION] = listOf(first, second)
        val (_, undo) = repository.blockRecordingChanges(listOf(actionPath("group") to COMPONENT))
        sendPm(first to PmSetting.ENABLED, second to PmSetting.DISABLED)

        assertThat(repository.undoBlockChanges(undo)).isEqualTo(WakeBlockOutcome(setOf("group"), null))
        assertThat(pm(first)).isEqualTo(PmSetting.ENABLED)
        assertThat(pm(second)).isEqualTo(PmSetting.DEFAULT)
        assertThat(dao.all()).isEmpty()
    }

    @Test fun givenBlockedPath_whenUndoHasNoAccess_thenReportsFailureAndKeepsRowAndClaim() = runTest {
        val device = object : DeviceRepositoryTestBase() {}
        try {
            val receiver = declaration(RECEIVER, ComponentType.RECEIVER)
            val repository = repository(componentRepository = journaledComponents(device.pmBlocks), claimRecords = device.pmBlocks)
            val path = path("receiver", component = RECEIVER, method = COMPONENT)
            val (_, undo) = repository.blockRecordingChanges(listOf(path to COMPONENT))
            val row = dao.all().single()
            val claim = device.pmBlocks.of(UserId.SYSTEM).single()

            channel.connectResult = ChannelState.Unavailable(UnavailableReason.DENIED)
            val outcome = repository.undoBlockChanges(undo)

            assertThat(outcome).isEqualTo(WakeBlockOutcome(emptySet(), WakeBlockFailure.NoAccess))
            assertThat(dao.all()).containsExactly(row)
            assertThat(device.pmBlocks.of(UserId.SYSTEM)).containsExactly(claim)
            assertThat(pm(receiver)).isEqualTo(PmSetting.DISABLED)
        } finally {
            device.closeDatabase()
        }
    }

    @Test fun givenSharedReceiverAndOtherPaths_whenUndoPartlyFails_thenKeepsOnlyFailedRowsAndClaims() = runTest {
        val device = object : DeviceRepositoryTestBase() {}
        try {
            val first = declaration(RECEIVER, ComponentType.RECEIVER)
            val second = declaration(OTHER_RECEIVER, ComponentType.RECEIVER)
            receivers.matches[TARGET to ACTION] = listOf(first, second)
            val repository = repository(componentRepository = journaledComponents(device.pmBlocks), claimRecords = device.pmBlocks)
            val paths = listOf(actionPath("group"), path("first", component = RECEIVER, method = COMPONENT), path("second", component = OTHER_RECEIVER, method = COMPONENT))
            val (_, undo) = repository.blockRecordingChanges(paths.map { it to COMPONENT })
            val originalRows = dao.all().filter { it.pathKey != "first" }
            components.controlEvents = { request ->
                check(request.goal == ControlGoal.UNBLOCK)
                sendPm(first to PmSetting.DEFAULT, second to PmSetting.DISABLED)
                val restored = ComponentOutcome(first.key, ControlState(PmSetting.DEFAULT, IfwBlock.NOT_BLOCKED), ControlResult.ACHIEVED, emptyMap())
                val refusal = mapOf(ControlLayer.PACKAGE_MANAGER to ControlFailure.WriteFailed("test restore failed"))
                val failed = ComponentOutcome(second.key, ControlState(PmSetting.DISABLED, IfwBlock.NOT_BLOCKED), ControlResult.FAILED, refusal)
                listOf(ControlEvent.PackageDone(TARGET, listOf(restored, failed), setOf(first.id)))
            }

            val outcome = repository.undoBlockChanges(undo)

            assertThat(outcome).isEqualTo(WakeBlockOutcome(setOf("first"), WakeBlockFailure.SystemError("test restore failed")))
            assertThat(dao.all()).containsExactlyElementsIn(originalRows)
            assertThat(device.pmBlocks.of(UserId.SYSTEM)).containsExactly(second.key)
            assertThat(pm(first)).isEqualTo(PmSetting.DEFAULT)
            assertThat(pm(second)).isEqualTo(PmSetting.DISABLED)

            components.controlEvents = null
            assertThat(repository.undoBlockChanges(undo)).isEqualTo(WakeBlockOutcome(setOf("group", "second"), null))
            assertThat(dao.all()).isEmpty()
            assertThat(device.pmBlocks.of(UserId.SYSTEM)).isEmpty()
            assertThat(pm(second)).isEqualTo(PmSetting.DEFAULT)
        } finally {
            device.closeDatabase()
        }
    }

    @Test fun givenPeerReblocksPath_whenUndoingOriginalBatch_thenNewRowAndClaimSurvive() = runTest {
        val device = object : DeviceRepositoryTestBase() {}
        try {
            val receiver = declaration(RECEIVER, ComponentType.RECEIVER)
            val repository = repository(componentRepository = journaledComponents(device.pmBlocks), claimRecords = device.pmBlocks)
            val path = path("shared", component = RECEIVER, method = COMPONENT)
            val (_, undo) = repository.blockRecordingChanges(listOf(path to COMPONENT))
            val originalClaim = device.pmBlocks.recordsFor(AppKey(TARGET, UserId.SYSTEM)).single()

            assertThat(repository.unblockPaths(listOf(path)).done).containsExactly("shared")
            assertThat(repository.block(listOf(path to COMPONENT)).done).containsExactly("shared")
            val newRow = dao.all().single()
            val newClaim = device.pmBlocks.recordsFor(AppKey(TARGET, UserId.SYSTEM)).single()
            assertThat(newClaim.recordedAt).isGreaterThan(originalClaim.recordedAt)

            assertThat(repository.undoBlockChanges(undo)).isEqualTo(WakeBlockOutcome(emptySet(), null))
            assertThat(dao.all()).containsExactly(newRow)
            assertThat(device.pmBlocks.recordsFor(AppKey(TARGET, UserId.SYSTEM))).containsExactly(newClaim)
            assertThat(pm(receiver)).isEqualTo(PmSetting.DISABLED)
        } finally {
            device.closeDatabase()
        }
    }

    @Test fun givenNewAndPriorClaims_whenAnotherScreenReclaimsPm_thenUndoLeavesTheNewClaimsAndSettings() = runTest {
        val device = object : DeviceRepositoryTestBase() {}
        try {
            val first = declaration(RECEIVER, ComponentType.RECEIVER)
            val second = declaration(OTHER_RECEIVER, ComponentType.RECEIVER)
            receivers.matches[TARGET to ACTION] = listOf(first, second)
            val app = AppKey(TARGET, UserId.SYSTEM)
            val status = journaledComponents(device.pmBlocks)
            val repository = repository(componentRepository = status, claimRecords = device.pmBlocks)
            device.pmBlocks.record(listOf(second.key))
            val prior = device.pmBlocks.recordsFor(app).single()
            val (_, undo) = repository.blockRecordingChanges(listOf(actionPath("group") to COMPONENT))
            val batchClaims = device.pmBlocks.recordsFor(app)
            assertThat(batchClaims).hasSize(2)
            assertThat(batchClaims).contains(prior)

            val block = ControlRequest(UserId.SYSTEM, listOf(first, second), ControlGoal.BLOCK, setOf(ControlLayer.PACKAGE_MANAGER))
            status.control(block.copy(goal = ControlGoal.UNBLOCK), keepKnownState = false, writeObserver = null, operation = OperationScope.Unrecorded).first()
            status.control(block, keepKnownState = false, writeObserver = null, operation = OperationScope.Unrecorded).first()
            val replacements = device.pmBlocks.recordsFor(app)
            assertThat(replacements).hasSize(2)
            assertThat(replacements).containsNoneIn(batchClaims)

            assertThat(repository.undoBlockChanges(undo)).isEqualTo(WakeBlockOutcome(setOf("group"), null))
            assertThat(dao.all()).isEmpty()
            assertThat(device.pmBlocks.recordsFor(app)).containsExactlyElementsIn(replacements)
            assertThat(pm(first)).isEqualTo(PmSetting.DISABLED)
            assertThat(pm(second)).isEqualTo(PmSetting.DISABLED)
        } finally {
            device.closeDatabase()
        }
    }

    @Test fun givenAutomaticBlocksAndPriorClaim_whenUndoingBatch_thenOnlyTheNewClaimIsReleased() = runTest {
        val device = object : DeviceRepositoryTestBase() {}
        try {
            val owner = UserId.SYSTEM
            val first = declaration(RECEIVER, ComponentType.RECEIVER)
            val second = declaration(OTHER_RECEIVER, ComponentType.RECEIVER)
            val matches = listOf(first, second)
            device.system.install(installedApp(TARGET), first, second)
            matches.forEach { device.system.put(owner, it, PmSetting.DISABLED) }
            device.pmBlocks.record(listOf(second.key))
            val priorClaim = device.recordDao.getByUser(owner.value).single()
            val history = device.wakeDb.history()
            history.putProtections(
                matches.map {
                    WakeProtection(it.id.className, PEER, TARGET, it.id.className, ACTION, "receiver", false, true, method = "PM", previousState = 0)
                },
            )
            val protections = LocalAutomaticWakeBlockRepository(history, device.pmBlocks, lock, TestWakeRunRequester(), mock())
            device.componentControl.apply = {
                val outcomes = matches.map { declaration ->
                    ComponentOutcome(declaration.key, ControlState(PmSetting.DISABLED, IfwBlock.NOT_BLOCKED), ControlResult.ACHIEVED, emptyMap())
                }
                flowOf(ControlEvent.PackageDone(TARGET, outcomes))
            }
            val componentRepository = device.components(backgroundScope, claimedWakeBlocks = { _, candidates -> candidates.toList() })
            val repository = repository(privilegedChannel = device.channel, componentRepository = componentRepository, claimRecords = device.pmBlocks, protections = protections)
            receivers.matches[TARGET to ACTION] = matches
            assertThat(protections.ownedComponents.first()).containsExactly(first.id)

            val (outcome, undo) = repository.blockRecordingChanges(listOf(actionPath("group") to COMPONENT))

            assertThat(outcome).isEqualTo(WakeBlockOutcome(setOf("group"), null))
            assertThat(protections.ownedComponents.first()).isEmpty()
            assertThat(device.pmBlocks.of(owner)).containsExactly(first.key, second.key)
            assertThat(repository.undoBlockChanges(undo)).isEqualTo(outcome)
            assertThat(protections.ownedComponents.first()).containsExactly(first.id)
            assertThat(device.recordDao.getByUser(owner.value)).containsExactly(priorClaim)
            assertThat(device.system.settings[owner to first.id]).isEqualTo(PmSetting.DISABLED)
        } finally {
            device.closeDatabase()
        }
    }

    @Test fun givenNewAndExistingFirewallRows_whenUndoing_thenOldRuleAndRowReturnAndNewOneIsRemoved() = runTest {
        val repository = repository()
        val existing = firewallRow("existing", TARGET)
        repository.restoreBlocks(
            listOf(
                WakeBlock(existing.pathKey, existing.target, FIREWALL, ComponentType.SERVICE, existing.sender, existing.component, null, existing.blockedAt),
            ),
        )
        val originalFile = writer.userWrites.single()

        val (outcome, undo) = repository.blockRecordingChanges(listOf(path("existing") to FIREWALL, path("new", component = OTHER_SERVICE) to FIREWALL))

        assertThat(outcome).isEqualTo(WakeBlockOutcome(setOf("existing", "new"), null))
        assertThat(writer.userWrites.last()).isNotEqualTo(originalFile)
        assertThat(repository.undoBlockChanges(undo)).isEqualTo(outcome)
        assertThat(dao.all()).containsExactly(existing)
        assertThat(writer.userWrites.last()).isEqualTo(originalFile)
    }

    @Test fun givenComponentAndFirewallWrites_whenFirewallUndoFails_thenComponentIsStillRestored() = runTest {
        val repository = repository()
        val receiver = declaration(RECEIVER, ComponentType.RECEIVER)
        val (_, undo) = repository.blockRecordingChanges(listOf(path("service") to FIREWALL, path("receiver", component = RECEIVER, method = COMPONENT) to COMPONENT))
        val firewallWrite = writer.userWrites.single()
        writer.failWrites = true

        val outcome = repository.undoBlockChanges(undo)

        assertThat(outcome).isEqualTo(WakeBlockOutcome(setOf("receiver"), WakeBlockFailure.SystemError("test write failed")))
        assertThat(pm(receiver)).isEqualTo(PmSetting.DEFAULT)
        assertThat(dao.all().map { it.pathKey }).containsExactly("service")
        assertThat(writer.userWrites).containsExactly(firewallWrite)
    }

    @Test fun givenBlockWasReplaced_whenUndoingBatch_thenReplacementIsPreserved() = runTest {
        val repository = repository()
        val (outcome, undo) = repository.blockRecordingChanges(listOf(path("path") to FIREWALL))
        assertThat(outcome.done).containsExactly("path")
        val replacement = dao.all().single().copy(blockedAt = 5)
        dao.upsert(listOf(replacement))

        assertThat(repository.undoBlockChanges(undo).done).isEmpty()
        assertThat(dao.all()).containsExactly(replacement)
    }
}
