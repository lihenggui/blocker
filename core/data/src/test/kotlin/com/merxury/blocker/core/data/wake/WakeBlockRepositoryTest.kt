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
internal class WakeBlockRepositoryTest : WakeBlockTestFixture() {
    @Test fun givenKnownTriggersAndHolds_whenBlocking_thenStoredSendersMatchTheWakePaths() = runTest {
        val repository = repository()
        val paths = listOf(
            path("service"),
            path("peer-bind", trigger = null, hold = HoldCause.BOUND_BY_PEER),
            path("own-bind", trigger = null, hold = HoldCause.BOUND_BY_PEER, actor = TARGET),
            path("self", trigger = ReviveTrigger.SELF_PROCESS, actor = null),
        )
        repository.blocks.test {
            assertThat(awaitItem()).isEmpty()
            val outcome = repository.block(paths.map { it to FIREWALL })
            assertThat(outcome.done).containsExactlyElementsIn(paths.map(WakePath::pathKey))
            assertThat(outcome.failure).isNull()
            assertThat(awaitItem().associate { it.pathKey to it.sender }).containsExactly(
                "service",
                PEER,
                "peer-bind",
                PEER,
                "own-bind",
                TARGET,
                "self",
                TARGET,
            )
        }
        assertThat(writer.userWrites).hasSize(1)
        assertThat(writer.firewallRules()).hasSize(2)
        assertThat(channel.connectCount).isEqualTo(1)
    }

    @Test fun givenOtherTriggersAndHolds_whenRequestingFirewall_thenNoRuleIsBroadenedToAnySender() = runTest {
        val repository = repository()
        val triggers = ReviveTrigger.entries - setOf(ReviveTrigger.PEER_SERVICE, ReviveTrigger.SELF_PROCESS)
        val holds = HoldCause.entries - HoldCause.BOUND_BY_PEER
        val paths = triggers.map { path("trigger-${it.name}", trigger = it) } +
            holds.map { path("hold-${it.name}", trigger = null, hold = it) } +
            path("unknown-sender", actor = null)
        val outcome = repository.block(paths.map { it to FIREWALL })
        assertThat(outcome.failure).isEqualTo(WakeBlockFailure.NotBlockable(paths.map(WakePath::pathKey)))
        assertThat(outcome.done).isEmpty()
        assertThat(writer.userWrites).isEmpty()
    }

    @Test fun givenNamedPeerBroadcastAndActivity_whenBlocking_thenOnlyTheActivityIsSenderScoped() = runTest {
        val repository = repository()
        val broadcast = path("receiver", trigger = ReviveTrigger.PEER_BROADCAST, component = RECEIVER, action = ACTION, method = COMPONENT)
        val activity = path("activity", trigger = ReviveTrigger.PEER_ACTIVITY, component = ACTIVITY)

        assertThat(repository.block(listOf(broadcast to COMPONENT, activity to FIREWALL)))
            .isEqualTo(WakeBlockOutcome(setOf("receiver", "activity"), null))
        assertThat(dao.all().associate { it.pathKey to it.method })
            .containsExactly("receiver", COMPONENT.name, "activity", FIREWALL.name)
        assertThat(writer.firewallRules().single().filters).containsExactly(
            IfwFilter.ComponentFilter("$TARGET/$ACTIVITY"),
            IfwFilter.SenderPackage(PEER),
        )

        assertThat(repository.unblockPaths(listOf(broadcast))).isEqualTo(WakeBlockOutcome(setOf("receiver"), null))
        assertThat(writer.firewallRules().single().componentType).isEqualTo(IfwComponentType.ACTIVITY)
        assertThat(repository.unblockPaths(listOf(activity))).isEqualTo(WakeBlockOutcome(setOf("activity"), null))
        assertThat(writer.userWrites.last()).isNull()
        assertThat(dao.all()).isEmpty()
    }

    @Test fun givenPeerSharesItsUid_whenBlocking_thenNoSenderPackageRuleIsWritten() = runTest {
        val repository = repository()
        shadowOf(context.packageManager).setPackagesForUid(PEER_UID, PEER, "another.package")

        assertThat(repository.block(listOf(path("service") to FIREWALL)))
            .isEqualTo(WakeBlockOutcome(emptySet(), WakeBlockFailure.NotBlockable(listOf("service"))))
        assertThat(writer.userWrites).isEmpty()
        assertThat(dao.all()).isEmpty()
    }

    @Test fun givenPeerBroadcastOrActivityWithoutValidSenderAndComponent_whenBlocking_thenRejectThePath() = runTest {
        val repository = repository()
        val broadcast = path("broadcast", trigger = ReviveTrigger.PEER_BROADCAST, component = RECEIVER)
        val activity = path("activity", trigger = ReviveTrigger.PEER_ACTIVITY, component = ACTIVITY)
        val invalid = listOf(
            broadcast.copy(pathKey = "action-only", component = null, action = ACTION),
            broadcast.copy(pathKey = "wrong-type", component = ACTIVITY),
            broadcast.copy(pathKey = "invalid-sender", actor = "android"),
            activity.copy(pathKey = "self", actor = TARGET),
            activity.copy(pathKey = "unknown", actor = null),
            activity.copy(pathKey = "wrong-class", component = ".Activity"),
        )

        assertThat(repository.block(invalid.map { it to FIREWALL }))
            .isEqualTo(WakeBlockOutcome(emptySet(), WakeBlockFailure.NotBlockable(invalid.map { it.pathKey })))
        assertThat(dao.all()).isEmpty()
        assertThat(writer.userWrites).isEmpty()
    }

    @Test fun givenManifestTypesAndConflictingHints_whenBlockingWithFirewall_thenOnlyDeclaredServicesAreAccepted() = runTest {
        val repository = repository()
        val requested = listOf(
            path("no-type", component = "target.pkg.Missing"),
            path("service", hint = ComponentType.PROVIDER),
            path("receiver", component = RECEIVER, hint = ComponentType.SERVICE, action = ACTION),
            path("provider", component = PROVIDER),
            path("activity", component = ACTIVITY),
            path("no-selector", component = null),
            path("self-action", component = null, trigger = ReviveTrigger.SELF_PROCESS, action = ACTION),
            path("self-receiver", component = RECEIVER, trigger = ReviveTrigger.SELF_PROCESS),
            path("unsupported", method = WakeBlockMethod.NONE),
        )
        val outcome = repository.block(requested.map { it to FIREWALL })
        assertThat(outcome.failure).isEqualTo(
            WakeBlockFailure.NotBlockable(listOf("no-type", "receiver", "provider", "activity", "no-selector", "self-action", "self-receiver", "unsupported")),
        )
        assertThat(outcome.done).containsExactly("service")
        assertThat(repository.blocks.first().associate { it.pathKey to it.componentType })
            .containsExactly("service", ComponentType.SERVICE)
        assertThat(components.refreshedApps.toSet()).containsExactly(AppKey(TARGET, UserId.SYSTEM))
    }

    @Test fun givenShellOrNoChannel_whenBlockingOrUnblockingFirewall_thenRowsAndFileAreLeftAlone() = runTest {
        val repository = repository()
        val path = path("service")
        assertThat(repository.block(listOf(path to FIREWALL)).failure).isNull()
        for ((state, failure) in listOf(
            ChannelState.Ready(PrivilegedIdentity.SHELL) to WakeBlockFailure.NeedsRoot,
            ChannelState.Unavailable(UnavailableReason.DENIED) to WakeBlockFailure.NoAccess,
        )) {
            channel.connectResult = state
            assertThat(repository.block(listOf(path("other") to FIREWALL)).failure).isEqualTo(failure)
            assertThat(repository.unblock(listOf(path.pathKey)).failure).isEqualTo(failure)
        }
        assertThat(dao.all().map { it.pathKey }).containsExactly(path.pathKey)
        assertThat(writer.userWrites).hasSize(1)
    }

    @Test fun givenUnsupportedPackageFilter_whenBlockingPeersAndOwnUid_thenOnlyOwnUidRuleIsApplied() = runTest {
        val repository = repository(peerFilter = false)
        val outcome = repository.block(
            listOf(path("p1"), path("self", trigger = ReviveTrigger.SELF_PROCESS), path("p2", component = OTHER_SERVICE)).map { it to FIREWALL },
        )
        assertThat(outcome.failure).isEqualTo(WakeBlockFailure.SenderFilterUnsupported(listOf("p1", "p2")))
        assertThat(outcome.done).containsExactly("self")
        assertThat(dao.all().single().sender).isEqualTo(TARGET)
    }

    @Test fun givenUserFileWriteFailure_whenBlockingOrUnblocking_thenRowsStayAndComponentSuccessIsKept() = runTest {
        val repository = repository()
        val first = path("first")
        repository.block(listOf(first to FIREWALL))
        val initialXml = writer.userWrites.single()
        writer.failWrites = true
        val attempted = path("second")
        val pm = path("pm", component = PROVIDER, trigger = ReviveTrigger.PEER_PROVIDER, method = COMPONENT)
        val outcome = repository.block(listOf(attempted to FIREWALL, pm to COMPONENT, path("bad", component = ACTIVITY) to FIREWALL))
        assertThat(outcome.failure).isEqualTo(WakeBlockFailure.SystemError("test write failed"))
        assertThat(outcome.done).containsExactly(pm.pathKey)
        assertThat(repository.unblock(listOf(first.pathKey)).failure).isEqualTo(WakeBlockFailure.SystemError("test write failed"))
        assertThat(writer.userWrites).containsExactly(initialXml)
        assertThat(dao.all().map { it.pathKey }).containsExactly(first.pathKey, pm.pathKey)
    }

    @Test fun givenComponentBlocksSharingAComponent_whenUnblocking_thenOnlyTheLastRemovalEnablesIt() = runTest {
        val repository = repository()
        val first = path("first", component = PROVIDER, trigger = ReviveTrigger.PEER_PROVIDER, method = COMPONENT)
        val second = first.copy(pathKey = "second")
        assertThat(repository.block(listOf(first to COMPONENT, second to COMPONENT)).done).containsExactly("first", "second")
        val key = ComponentKey(ComponentId(TARGET, PROVIDER), UserId.SYSTEM)
        assertThat(components.component(key).first()?.state?.pm).isEqualTo(PmSetting.DISABLED)
        assertThat(repository.blocks.first().map { it.componentType }).containsExactly(null, null)
        assertThat(repository.unblock(listOf(first.pathKey)).done).containsExactly("first")
        assertThat(components.component(key).first()?.state?.pm).isEqualTo(PmSetting.DISABLED)
        assertThat(repository.unblock(listOf(second.pathKey)).done).containsExactly("second")
        assertThat(components.component(key).first()?.state?.pm).isEqualTo(PmSetting.DEFAULT)
        assertThat(components.requests.map { it.goal }).containsExactly(ControlGoal.BLOCK, ControlGoal.BLOCK, ControlGoal.UNBLOCK).inOrder()
        assertThat(components.requests.all { it.layers == setOf(ControlLayer.PACKAGE_MANAGER) && it.user == UserId.SYSTEM }).isTrue()
        assertThat(writer.userWrites).isEmpty()
    }

    @Test fun givenComponentRemovedFromManifest_whenUnblocking_thenItsRowIsDeletedWithoutAControlRequest() = runTest {
        val repository = repository()
        val path = path("removed", component = PROVIDER, method = COMPONENT)
        assertThat(repository.block(listOf(path to COMPONENT)).done).containsExactly(path.pathKey)
        val remaining = components.components(AppKey(TARGET, UserId.SYSTEM)).first().filterNot { it.declaration.id.className == PROVIDER }
        components.sendComponents(UserId.SYSTEM, remaining)

        assertThat(repository.unblock(listOf(path.pathKey))).isEqualTo(WakeBlockOutcome(setOf(path.pathKey), null))
        assertThat(dao.all()).isEmpty()
        assertThat(components.requests.map { it.goal }).containsExactly(ControlGoal.BLOCK)
    }

    @Test fun givenPackageMissingForOwner_whenUnblocking_thenItsRowIsDeletedWithoutAControlRequest() = runTest {
        val repository = repository()
        val path = path("uninstalled", component = PROVIDER, method = COMPONENT)
        assertThat(repository.block(listOf(path to COMPONENT)).done).containsExactly(path.pathKey)
        components.refreshFailure = ReadFailure.PackageNotFound(AppKey(TARGET, UserId.SYSTEM))

        assertThat(repository.unblock(listOf(path.pathKey))).isEqualTo(WakeBlockOutcome(setOf(path.pathKey), null))
        assertThat(dao.all()).isEmpty()
        assertThat(components.requests.map { it.goal }).containsExactly(ControlGoal.BLOCK)
    }

    @Test fun givenComponentWriteInPackageResult_whenBlockingFails_thenFailureAndUndoAreBothAvailable() = runTest {
        assertUnconfirmedWriteCanBeUndone(acknowledgeBeforeReadBack = false)
    }

    @Test fun givenComponentWriteAcknowledgedWithoutReadBack_whenBlockingFails_thenFailureAndUndoAreBothAvailable() = runTest {
        assertUnconfirmedWriteCanBeUndone(acknowledgeBeforeReadBack = true)
    }

    private suspend fun TestScope.assertUnconfirmedWriteCanBeUndone(acknowledgeBeforeReadBack: Boolean) {
        val path = path("component", component = PROVIDER, method = COMPONENT)
        val provider = declaration(PROVIDER, ComponentType.PROVIDER)
        val pmWrites = if (acknowledgeBeforeReadBack) emptySet() else setOf(provider.id)
        val observed = object : ComponentStatusRepository by components {
            override fun control(request: ControlRequest, keepKnownState: Boolean, writeObserver: ComponentWriteObserver?, operation: OperationScope) = components.control(request, keepKnownState, writeObserver, operation).onStart {
                if (acknowledgeBeforeReadBack && request.goal == ControlGoal.BLOCK) writeObserver?.onWrite(listOf(provider.id))
            }
        }
        val repository = repository(componentRepository = observed)
        val key = ComponentKey(provider.id, UserId.SYSTEM)
        components.controlEvents = {
            val disabled = ControlState(PmSetting.DISABLED, IfwBlock.NOT_BLOCKED)
            components.sendComponents(it.user, listOf(ComponentStatus(provider, disabled)))
            val outcome = ComponentOutcome(
                key,
                disabled.copy(pm = PmSetting.UNKNOWN),
                ControlResult.FAILED,
                mapOf(ControlLayer.PACKAGE_MANAGER to ControlFailure.StateNotConfirmed),
            )
            listOf(ControlEvent.PackageDone(TARGET, listOf(outcome), pmWrites))
        }

        assertThat(repository.block(listOf(path to COMPONENT))).isEqualTo(WakeBlockOutcome(emptySet(), WakeBlockFailure.SystemError(null)))
        assertThat(dao.all().single().component).isEqualTo(PROVIDER)

        components.controlEvents = null
        assertThat(repository.unblock(listOf(path.pathKey)).done).containsExactly(path.pathKey)
        assertThat(components.component(key).first()?.state?.pm).isEqualTo(PmSetting.DEFAULT)
        assertThat(dao.all()).isEmpty()
    }

    @Test fun givenShellComponentRefusal_whenBlocking_thenNoRowIsStored() = runTest {
        val repository = repository()
        channel.connectResult = ChannelState.Ready(PrivilegedIdentity.SHELL)
        components.controlEvents = {
            val state = ControlState(PmSetting.DEFAULT, IfwBlock.NOT_BLOCKED)
            val key = ComponentKey(it.components.single().id, it.user)
            val failures = mapOf(ControlLayer.PACKAGE_MANAGER to ControlFailure.NotPermitted("denied", PrivilegedIdentity.SHELL))
            listOf(ControlEvent.PackageDone(TARGET, listOf(ComponentOutcome(key, state, ControlResult.FAILED, failures))))
        }
        val outcome = repository.block(listOf(path("component") to COMPONENT))
        assertThat(outcome.failure).isEqualTo(WakeBlockFailure.NeedsRoot)
        assertThat(dao.all()).isEmpty()
        assertThat(writer.userWrites).isEmpty()
    }

    @Test fun givenActionWithTwoReceivers_whenBlocking_thenBothAreDisabledInOneRequestForOnePath() = runTest {
        val repository = repository()
        val matches = listOf(declaration(RECEIVER, ComponentType.RECEIVER), declaration(OTHER_RECEIVER, ComponentType.RECEIVER))
        receivers.matches[TARGET to ACTION] = matches
        val outcome = repository.block(listOf(actionPath("broadcast") to COMPONENT))

        assertThat(outcome.done).containsExactly("broadcast")
        assertThat(outcome.failure).isNull()
        assertThat(components.requests.single()).isEqualTo(ControlRequest(UserId.SYSTEM, matches, ControlGoal.BLOCK, setOf(ControlLayer.PACKAGE_MANAGER)))
        val row = repository.blocks.first().single()
        assertThat(row.method).isEqualTo(COMPONENT)
        assertThat(row.component).isNull()
        assertThat(row.action).isEqualTo(ACTION)
        assertThat(writer.userWrites).isEmpty()
    }

    @Test fun givenThePackageActionResolvesOneReceiver_whenChangingThePath_thenOnlyThatReceiverIsChanged() = runTest {
        val repository = repository()
        val action = Intent.ACTION_PACKAGE_ADDED
        val receiver = declaration(RECEIVER, ComponentType.RECEIVER)
        receivers.matches[TARGET to action] = listOf(receiver)
        receivers.matches[TARGET to Intent.ACTION_PACKAGE_REMOVED] = listOf(declaration(OTHER_RECEIVER, ComponentType.RECEIVER))
        val path = actionPath("package-added").copy(action = action)

        assertThat(repository.block(listOf(path to COMPONENT)).done).containsExactly(path.pathKey)
        assertThat(repository.unblockPaths(listOf(path)).done).containsExactly(path.pathKey)
        assertThat(components.requests.map { it.components }).containsExactly(listOf(receiver), listOf(receiver))
        assertThat(writer.userWrites).isEmpty()
    }

    @Test fun givenActionWithoutMatchingReceivers_whenBlocking_thenNoRequestOrRowIsCreated() = runTest {
        val repository = repository()
        val outcome = repository.block(listOf(actionPath("missing") to COMPONENT))

        assertThat(outcome.failure).isEqualTo(WakeBlockFailure.NotBlockable(listOf("missing")))
        assertThat(outcome.done).isEmpty()
        assertThat(components.requests).isEmpty()
        assertThat(dao.all()).isEmpty()
    }

    @Test fun givenSharedActionAndNamedBlocks_whenUnblocking_thenOnlyUncoveredReceiversAreEnabled() = runTest {
        val repository = repository()
        val receiver = declaration(RECEIVER, ComponentType.RECEIVER)
        val other = declaration(OTHER_RECEIVER, ComponentType.RECEIVER)
        receivers.matches[TARGET to ACTION] = listOf(receiver, other)
        val named = actionPath("named").copy(component = RECEIVER)
        val first = actionPath("first")
        val second = actionPath("second").copy(actor = "another.sender")
        assertThat(repository.block(listOf(first, second, named).map { it to COMPONENT }).done).hasSize(3)

        assertThat(repository.unblock(listOf("first")).done).containsExactly("first")
        assertThat(components.requests).hasSize(3)
        assertThat(repository.unblock(listOf("second")).done).containsExactly("second")
        assertThat(components.requests.last().components).containsExactly(other)
        assertThat(repository.unblock(listOf("named")).done).containsExactly("named")
        assertThat(components.requests.last().components).containsExactly(receiver)
    }

    @Test fun givenReceiverCoveredByTwoActions_whenUnblocking_thenRemainingActionStillKeepsItDisabled() = runTest {
        val repository = repository()
        val receiver = declaration(RECEIVER, ComponentType.RECEIVER)
        val other = declaration(OTHER_RECEIVER, ComponentType.RECEIVER)
        receivers.matches[TARGET to ACTION] = listOf(receiver, other)
        receivers.matches[TARGET to OTHER_ACTION] = listOf(receiver)
        val named = actionPath("named").copy(component = RECEIVER)
        val first = actionPath("first")
        val second = actionPath("second").copy(action = OTHER_ACTION)
        assertThat(repository.block(listOf(first, second, named).map { it to COMPONENT }).done).hasSize(3)

        assertThat(repository.unblock(listOf("named")).done).containsExactly("named")
        assertThat(components.requests).hasSize(3)
        assertThat(repository.unblock(listOf("first")).done).containsExactly("first")
        assertThat(components.requests.last().components).containsExactly(other)
        assertThat(repository.unblock(listOf("second")).done).containsExactly("second")
        assertThat(components.requests.last().components).containsExactly(receiver)
    }

    @Test fun givenUpdatedReceiverMatches_whenUnblocking_thenTheActionIsResolvedAgain() = runTest {
        val repository = repository()
        val receiver = declaration(RECEIVER, ComponentType.RECEIVER)
        val other = declaration(OTHER_RECEIVER, ComponentType.RECEIVER)
        receivers.matches[TARGET to ACTION] = listOf(receiver, other)
        val path = actionPath("broadcast")
        assertThat(repository.block(listOf(path to COMPONENT)).done).containsExactly(path.pathKey)

        receivers.matches[TARGET to ACTION] = listOf(other)
        assertThat(repository.unblock(listOf(path.pathKey)).done).containsExactly(path.pathKey)
        assertThat(components.requests.last().components).containsExactly(other)
        assertThat(dao.all()).isEmpty()
    }

    @Test fun givenOneFailedReceiverDisable_whenUnblockingTheAction_thenTheAppliedChangeCanBeUndone() = runTest {
        val repository = repository()
        val receiver = declaration(RECEIVER, ComponentType.RECEIVER)
        val other = declaration(OTHER_RECEIVER, ComponentType.RECEIVER)
        receivers.matches[TARGET to ACTION] = listOf(receiver, other)
        components.controlEvents = {
            val applied = ComponentOutcome(ComponentKey(receiver.id, it.user), ControlState(PmSetting.DISABLED, IfwBlock.NOT_BLOCKED), ControlResult.ACHIEVED, emptyMap())
            val refused = ComponentOutcome(
                ComponentKey(other.id, it.user),
                ControlState(PmSetting.DEFAULT, IfwBlock.NOT_BLOCKED),
                ControlResult.FAILED,
                mapOf(ControlLayer.PACKAGE_MANAGER to ControlFailure.NotPermitted("denied", PrivilegedIdentity.ROOT)),
            )
            components.sendComponents(it.user, listOf(ComponentStatus(receiver, applied.state), ComponentStatus(other, refused.state)))
            listOf(ControlEvent.PackageDone(TARGET, listOf(applied, refused), setOf(receiver.id)))
        }

        val outcome = repository.block(listOf(actionPath("broadcast") to COMPONENT))
        assertThat(outcome.done).isEmpty()
        assertThat(outcome.failure).isEqualTo(WakeBlockFailure.SystemError("denied"))
        assertThat(dao.all().single().pathKey).isEqualTo("broadcast")

        components.controlEvents = null
        assertThat(repository.unblock(listOf("broadcast")).done).containsExactly("broadcast")
        assertThat(components.component(ComponentKey(receiver.id, UserId.SYSTEM)).first()?.state?.pm).isEqualTo(PmSetting.DEFAULT)
        assertThat(dao.all()).isEmpty()
    }

    @Test fun givenTwoFirewallBlocks_whenUnblocking_thenFileIsRebuiltAndRemovedAfterLastPath() = runTest {
        val repository = repository()
        repository.block(listOf(path("first") to FIREWALL, path("second", component = OTHER_SERVICE) to FIREWALL))
        assertThat(repository.unblock(listOf("first", "absent", "first")).done).containsExactly("first")
        assertThat(dao.all().single().pathKey).isEqualTo("second")
        assertThat(writer.firewallRules()).hasSize(1)
        assertThat(repository.unblock(listOf("second")).failure).isNull()
        assertThat(dao.all()).isEmpty()
        assertThat(writer.userWrites.last()).isNull()
    }

    @Test fun givenComponentListBlockWithoutWakeRow_whenAllowingPath_thenItsDisabledLayersAreCleared() = runTest {
        val repository = repository()
        val id = ComponentId(TARGET, RECEIVER)
        val app = AppKey(TARGET, UserId.SYSTEM)
        components.sendComponents(
            UserId.SYSTEM,
            listOf(
                ComponentStatus(declaration(RECEIVER, ComponentType.RECEIVER), ControlState(PmSetting.DISABLED, IfwBlock.BLOCKED)),
            ),
        )
        val path = path("list-block", trigger = ReviveTrigger.PEER_BROADCAST, component = id.className, method = COMPONENT)

        assertThat(repository.unblockPaths(listOf(path))).isEqualTo(WakeBlockOutcome(setOf(path.pathKey), null))

        assertThat(components.requests.single().goal).isEqualTo(ControlGoal.ENABLE)
        assertThat(components.components(app).first().single().isSwitchOn).isTrue()
        assertThat(dao.all()).isEmpty()
    }

    @Test fun givenStaticAndUnrelatedBlocks_whenAllowingAnObservedPath_thenOnlyItsStaticAliasIsRemoved() = runTest {
        val repository = repository()
        val observed = path("observed", trigger = ReviveTrigger.SYSTEM_BROADCAST, component = RECEIVER, method = COMPONENT)
        val static = observed.copy(pathKey = "STATIC|BOOT_AUTOSTART|$TARGET|$RECEIVER")
        val receiver = declaration(RECEIVER, ComponentType.RECEIVER)
        assertThat(repository.block(listOf(static to COMPONENT)).done).containsExactly(static.pathKey)
        assertThat(pm(receiver)).isEqualTo(PmSetting.DISABLED)
        val unrelated = dao.all().single().copy(pathKey = "unrelated-component-path")
        dao.upsert(listOf(unrelated))

        assertThat(repository.unblockPaths(listOf(observed))).isEqualTo(WakeBlockOutcome(setOf(observed.pathKey), null))
        assertThat(dao.all()).containsExactly(unrelated)
        assertThat(pm(receiver)).isEqualTo(PmSetting.DISABLED)

        assertThat(repository.unblock(listOf(unrelated.pathKey)).done).containsExactly(unrelated.pathKey)
        assertThat(dao.all()).isEmpty()
        assertThat(pm(receiver)).isEqualTo(PmSetting.DEFAULT)
    }

    @Test fun givenAStaticNamedReceiver_whenBlockingAndRestoringTheApp_thenOnlyThatReceiverIsChanged() = runTest {
        val repository = repository()
        val bootAction = "android.intent.action.BOOT_COMPLETED"
        val receiver = declaration(RECEIVER, ComponentType.RECEIVER)
        val otherReceiver = declaration(OTHER_RECEIVER, ComponentType.RECEIVER)
        receivers.matches[TARGET to bootAction] = listOf(receiver, otherReceiver)
        val unrelated = firewallRow("unrelated", PEER)
        dao.upsert(listOf(unrelated))
        val static = path(
            "STATIC|BOOT_AUTOSTART|$TARGET|$RECEIVER",
            actor = null,
            trigger = ReviveTrigger.SYSTEM_BROADCAST,
            component = RECEIVER,
            hint = ComponentType.RECEIVER,
            action = bootAction,
            method = COMPONENT,
        ).copy(count = 0L)
        val expected = WakeBlockOutcome(setOf(static.pathKey), null)

        assertThat(repository.block(listOf(static to COMPONENT))).isEqualTo(expected)
        assertThat(dao.all().single { it.pathKey == static.pathKey }.component).isEqualTo(RECEIVER)
        assertThat(pm(receiver)).isEqualTo(PmSetting.DISABLED)
        assertThat(pm(otherReceiver)).isEqualTo(PmSetting.DEFAULT)

        val (outcome, removed) = repository.removeAppBlocks(TARGET, outboundPathKeys = emptySet())

        assertThat(outcome).isEqualTo(expected)
        assertThat(removed.single().pathKey).isEqualTo(static.pathKey)
        assertThat(pm(receiver)).isEqualTo(PmSetting.DEFAULT)
        assertThat(pm(otherReceiver)).isEqualTo(PmSetting.DEFAULT)
        assertThat(dao.all()).containsExactly(unrelated)
        assertThat(components.requests.map { it.components }).containsExactly(listOf(receiver), listOf(receiver)).inOrder()
        assertThat(writer.userWrites).isEmpty()
    }

    @Test fun givenAppAndCurrentOutboundRows_whenRemovingAndRestoring_thenExactStoredRowsReturn() = runTest {
        val repository = repository()
        val inbound = firewallRow("old-inbound", TARGET)
        val toPeer = firewallRow("current-outbound", PEER)
        val unrelated = firewallRow("old-outbound", PEER)
        val component = WakeBlockEntity("component", TARGET, COMPONENT.name, null, null, RECEIVER, null, 17)
        dao.upsert(listOf(inbound, toPeer, unrelated, component))
        components.sendComponents(
            UserId.SYSTEM,
            listOf(
                ComponentStatus(declaration(SERVICE, ComponentType.SERVICE), ControlState(PmSetting.DEFAULT, IfwBlock.NOT_BLOCKED)),
                ComponentStatus(ComponentDeclaration(ComponentId(PEER, "$PEER.Service"), ComponentType.SERVICE, true, true), ControlState(PmSetting.DEFAULT, IfwBlock.NOT_BLOCKED)),
                ComponentStatus(declaration(RECEIVER, ComponentType.RECEIVER), ControlState(PmSetting.DISABLED, IfwBlock.NOT_BLOCKED)),
            ),
        )

        val (outcome, undo) = repository.removeAppBlocks(TARGET, setOf("current-outbound"))

        assertThat(outcome).isEqualTo(WakeBlockOutcome(setOf(inbound.pathKey, toPeer.pathKey, component.pathKey), null))
        assertThat(dao.all()).containsExactly(unrelated)
        assertThat(repository.restoreBlocks(undo)).isEqualTo(outcome)
        assertThat(dao.all()).containsExactly(inbound, toPeer, unrelated, component)
    }

    @Test fun givenAnOldOutboundBlock_whenRestoringTheSender_thenItIsRemovedWithoutAnObservedPath() = runTest {
        val repository = repository()
        val old = firewallRow("old-outbound", PEER).copy(sender = TARGET)
        val unrelated = firewallRow("other-sender", PEER)
        dao.upsert(listOf(old, unrelated))

        val (outcome, undo) = repository.removeAppBlocks(TARGET, emptySet())

        assertThat(outcome).isEqualTo(WakeBlockOutcome(setOf(old.pathKey), null))
        assertThat(dao.all()).containsExactly(unrelated)
        assertThat(undo.map { it.pathKey }).containsExactly(old.pathKey)
    }

    @Test fun givenResetScope_whenResettingProtection_thenOnlyMatchingFirewallRowsAreRemoved() = runTest {
        val history = mock<WakeRepository>()
        whenever(history.isOwnerProfile).thenReturn(true)
        val protection = LocalWakeProtectionRepository(history, mock(), mock(), rules, mock(), channel, mock(), firewall, lock, components)
        val kept = firewallRow("kept", PEER)
        val dropped = firewallRow("dropped", TARGET)
        val pm = dropped.copy(pathKey = "pm", method = COMPONENT.name, componentType = null)
        dao.upsert(listOf(kept, dropped, pm))

        assertThat(protection.resetFirewall(TARGET)).isTrue()
        assertThat(dao.all()).containsExactly(kept, pm)
        assertThat(protection.resetFirewall(null)).isTrue()
        assertThat(dao.all()).containsExactly(pm)
        assertThat(writer.resets).containsExactly(TARGET, null).inOrder()
    }

    @Test fun givenRemovedAndUnreadablePackages_whenSyncing_thenOnlyConfirmedRemovalsArePrunedAndEmptyFileIsDeleted() = runTest {
        val kept = firewallRow("kept", TARGET)
        val removed = firewallRow("gone", PEER)
        val pm = removed.copy(pathKey = "pm", method = COMPONENT.name, componentType = null)
        dao.upsert(listOf(kept, removed, pm))
        whenever(packages.isInstalled(AppKey(TARGET, UserId.SYSTEM))).thenReturn(ReadResult.Failure(ReadFailure.ChannelUnavailable))
        whenever(packages.isInstalled(AppKey(PEER, UserId.SYSTEM))).thenReturn(ReadResult.Success(false))
        lock.withLock { firewall.sync() }
        assertThat(dao.all()).containsExactly(kept)
        assertThat(writer.firewallRules()).hasSize(1)

        whenever(packages.isInstalled(AppKey(TARGET, UserId.SYSTEM))).thenReturn(ReadResult.Success(false))
        lock.withLock { firewall.sync() }
        assertThat(dao.all()).isEmpty()
        assertThat(writer.userWrites.last()).isNull()
    }

    @Test fun givenNamedAndActionOnlyBroadcastRows_whenSyncing_thenLegacyBroadcastRulesAreRetired() = runTest {
        val service = firewallRow("service", TARGET)
        val action = service.copy(pathKey = "action", componentType = ComponentType.RECEIVER.name, component = null, action = ACTION)
        val receiver = action.copy(pathKey = "receiver", component = RECEIVER)
        dao.upsert(listOf(service, action, receiver))
        whenever(packages.isInstalled(AppKey(TARGET, UserId.SYSTEM))).thenReturn(ReadResult.Success(true))

        lock.withLock { firewall.sync() }

        assertThat(dao.all()).containsExactly(service)
        assertThat(writer.firewallRules().map { it.componentType }).containsExactly(IfwComponentType.SERVICE)
    }

    @Test fun givenObsoleteFirewallRows_whenBlockingOrUnblocking_thenRowsArePrunedWithEachWrite() = runTest {
        val repository = repository()
        val outdated = firewallRow("outdated", TARGET).copy(componentType = ComponentType.RECEIVER.name, component = null, action = ACTION)
        dao.upsert(listOf(outdated))

        assertThat(repository.block(listOf(path("service") to FIREWALL)).done).containsExactly("service")
        assertThat(dao.all().map { it.pathKey }).containsExactly("service")
        assertThat(writer.firewallRules()).hasSize(1)

        dao.upsert(listOf(outdated))
        assertThat(repository.unblock(listOf("service")).done).containsExactly("service")
        assertThat(dao.all()).isEmpty()
        assertThat(writer.userWrites.last()).isNull()
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun givenFirewallWriteInFlight_whenAnotherBlockArrives_thenBatchUsesCommittedRows() = runTest {
        val repository = repository()
        val releaseWrite = CompletableDeferred<Unit>()
        writer.beforeWrite = { releaseWrite.await() }
        val first = async { repository.block(listOf(path("first") to FIREWALL)) }
        runCurrent()
        val second = async { repository.block(listOf(path("second", component = OTHER_SERVICE) to FIREWALL)) }
        runCurrent()
        assertThat(writer.userWrites).isEmpty()
        assertThat(dao.all()).isEmpty()
        releaseWrite.complete(Unit)
        assertThat(first.await().done).containsExactly("first")
        assertThat(second.await().done).containsExactly("second")
        assertThat(writer.firewallRules()).hasSize(2)
    }

    @Test fun givenAccessPrompt_whenBlocking_thenTheLockIsAvailableAndLostAccessPreventsTheWrite() = runTest {
        assertPromptPrecedesLock { repository -> repository.block(listOf(path("new") to FIREWALL)) }
    }

    @Test fun givenAccessPrompt_whenUnblocking_thenTheLockIsAvailableAndLostAccessPreventsTheWrite() = runTest {
        assertPromptPrecedesLock { repository -> repository.unblock(listOf("existing")) }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private suspend fun TestScope.assertPromptPrecedesLock(change: suspend (WakeBlockRepository) -> WakeBlockOutcome) {
        val promptOpened = CompletableDeferred<Unit>()
        val grantAccess = CompletableDeferred<Unit>()
        val promptingChannel = object : PrivilegedChannel by channel {
            override suspend fun connect(): ChannelState {
                promptOpened.complete(Unit)
                grantAccess.await()
                return channel.connect()
            }
        }
        val repository = repository(privilegedChannel = promptingChannel)
        val existing = firewallRow("existing", TARGET)
        dao.upsert(listOf(existing))
        val pending = async { change(repository) }
        promptOpened.await()

        val acquired = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val competingChange = async {
            lock.withLock {
                acquired.complete(Unit)
                release.await()
            }
        }
        runCurrent()
        assertThat(acquired.isCompleted).isTrue()

        grantAccess.complete(Unit)
        runCurrent()
        assertThat(channel.connectCount).isEqualTo(1)
        channel.setState(ChannelState.Unavailable(UnavailableReason.DENIED))
        release.complete(Unit)
        competingChange.await()

        assertThat(pending.await()).isEqualTo(WakeBlockOutcome(emptySet(), WakeBlockFailure.NoAccess))
        assertThat(writer.userWrites).isEmpty()
        assertThat(dao.all()).containsExactly(existing)
    }
}
