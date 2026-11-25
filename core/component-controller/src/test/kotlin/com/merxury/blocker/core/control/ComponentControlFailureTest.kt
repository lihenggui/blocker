/*
 * Copyright 2026 Blocker
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.merxury.blocker.core.control

import com.google.common.truth.Truth.assertThat
import com.merxury.blocker.core.model.ComponentType
import com.merxury.blocker.core.model.component.AppKey
import com.merxury.blocker.core.model.component.ComponentDeclaration
import com.merxury.blocker.core.model.component.ComponentId
import com.merxury.blocker.core.model.component.ControlLayer
import com.merxury.blocker.core.model.component.ControlState
import com.merxury.blocker.core.model.component.IfwBlock
import com.merxury.blocker.core.model.component.PmSetting
import com.merxury.blocker.core.model.control.ControlEvent
import com.merxury.blocker.core.model.control.ControlFailure
import com.merxury.blocker.core.model.control.ControlResult
import com.merxury.blocker.core.model.ifw.IfwResult
import com.merxury.blocker.core.model.privileged.CallFailure
import com.merxury.blocker.core.model.privileged.ChannelState
import com.merxury.blocker.core.model.privileged.PrivilegedIdentity
import com.merxury.blocker.core.model.privileged.UnavailableReason
import com.merxury.blocker.core.model.system.ReadFailure
import com.merxury.core.ifw.IfwStore
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.IOException

private val MAIN = declaration("Main")
private val SYNC = declaration("Sync", ComponentType.SERVICE)
private val BOOT = declaration("Boot", ComponentType.RECEIVER)
private val FILES = declaration("Files", ComponentType.PROVIDER)
private val OTHER_MAIN = declaration("Main", packageName = OTHER_PKG)

private val NOT_CHECKED = ChannelState.Unavailable(UnavailableReason.NOT_CHECKED)
private val SHELL = ChannelState.Ready(PrivilegedIdentity.SHELL)

/** What a request reports when the system, the channel or the rule files fail. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ComponentControlFailureTest {

    @Test
    fun givenProtectedComponentInBatch_whenBlockWithPm_thenOneByOneAndOnlyItFails() = runTest {
        val fixture = ControlFixture()
        fixture.system.protected += SYNC.id

        val events = fixture.run(block(PM_ONLY, MAIN, SYNC, BOOT))

        assertThat(fixture.executor.calls.map { it.components.size }).containsExactly(3, 1, 1, 1).inOrder()
        assertThat(events.outcomeOf(MAIN).result).isEqualTo(ControlResult.ACHIEVED)
        assertThat(events.outcomeOf(BOOT).result).isEqualTo(ControlResult.ACHIEVED)
        val refused = events.outcomeOf(SYNC)
        assertThat(refused.result).isEqualTo(ControlResult.FAILED)
        assertThat(refused.state.pm).isEqualTo(PmSetting.DEFAULT)
        assertThat(refused.failures).containsExactly(ControlLayer.PACKAGE_MANAGER, ControlFailure.NotPermitted("Protected component", PrivilegedIdentity.ROOT))
    }

    @Test
    fun givenShellIdentityAndOrdinaryApp_whenBlockWithPm_thenOneRefusedCallForThePackage() = runTest {
        val fixture = ControlFixture(SHELL)

        val events = fixture.run(block(PM_ONLY, MAIN, SYNC, BOOT))

        events.outcomes().forEach { outcome ->
            assertThat(outcome.result).isEqualTo(ControlResult.FAILED)
            assertThat(outcome.state.pm).isEqualTo(PmSetting.DEFAULT)
            assertThat(outcome.failures.getValue(ControlLayer.PACKAGE_MANAGER)).isInstanceOf(ControlFailure.NotPermitted::class.java)
        }
        // Android decides; its refusal of the batch stands for every component without a retry each.
        assertThat(fixture.executor.calls.map { it.components.size }).containsExactly(3)
        assertThat(fixture.channel.connectCount).isEqualTo(0)
    }

    @Test
    @Config(sdk = [32])
    fun givenShellIdentityWithoutBatchCall_whenBlockWithPm_thenTheFirstRefusalStandsForThePackage() = runTest {
        val fixture = ControlFixture(SHELL)

        val events = fixture.run(block(PM_ONLY, MAIN, SYNC))

        assertThat(events.outcomes().map { it.result }).containsExactly(ControlResult.FAILED, ControlResult.FAILED)
        assertThat(fixture.executor.calls).hasSize(1)
    }

    @Test
    fun givenShellIdentityAndTestOnlyApp_whenBlockWithPm_thenChanged() = runTest {
        val fixture = ControlFixture(SHELL)
        fixture.system.testOnly += PKG

        val events = fixture.run(block(PM_ONLY, MAIN, SYNC))

        assertThat(events.outcomes().map { it.result }).containsExactly(ControlResult.ACHIEVED, ControlResult.ACHIEVED)
        assertThat(fixture.pm(MAIN)).isEqualTo(PmSetting.DISABLED)
    }

    @Test
    fun givenShellIdentity_whenBlockWithIfw_thenNotPermittedWithoutCallingTheStore() = runTest {
        val fixture = ControlFixture(SHELL)
        // What the store sees through a channel that runs as the shell.
        fixture.files.noAccess = true

        val events = fixture.run(block(IFW_ONLY, MAIN))

        val outcome = events.outcomeOf(MAIN)
        assertThat(outcome.result).isEqualTo(ControlResult.FAILED)
        assertThat(outcome.state).isEqualTo(ControlState(PmSetting.DEFAULT, IfwBlock.UNKNOWN))
        assertThat(outcome.failures.getValue(ControlLayer.INTENT_FIREWALL)).isInstanceOf(ControlFailure.NotPermitted::class.java)
        // Only the peeks of the reads; no read-modify-write was attempted.
        assertThat(fixture.files.operations.filterNot { it.startsWith("peek") }).isEmpty()
    }

    @Test
    fun givenRulesUnreachableAfterConnect_whenBlockWithIfw_thenChannelUnavailable() = runTest {
        val fixture = ControlFixture()
        fixture.files.noAccess = true

        val events = fixture.run(block(IFW_ONLY, MAIN))

        assertThat(events.outcomeOf(MAIN).failures).containsExactly(ControlLayer.INTENT_FIREWALL, ControlFailure.ChannelUnavailable)
    }

    @Test
    fun givenChannelNotConnected_whenApplyOverSeveralPackages_thenConnectsOnce() = runTest {
        val fixture = ControlFixture(NOT_CHECKED)

        val events = fixture.run(block(BOTH, MAIN, OTHER_MAIN))

        assertThat(fixture.channel.connectCount).isEqualTo(1)
        assertThat(events.outcomes().map { it.result }).containsExactly(ControlResult.ACHIEVED, ControlResult.ACHIEVED)
    }

    @Test
    fun givenChannelDenied_whenBlockWithBoth_thenChannelUnavailableAndNothingSent() = runTest {
        val fixture = ControlFixture(NOT_CHECKED)
        fixture.channel.connectResult = ChannelState.Unavailable(UnavailableReason.DENIED)
        fixture.files.connected = false

        val events = fixture.run(block(BOTH, MAIN, OTHER_MAIN))

        assertThat(fixture.channel.connectCount).isEqualTo(1)
        assertThat(fixture.executor.calls).isEmpty()
        assertThat(fixture.files.operations.filterNot { it.startsWith("peek") }).isEmpty()
        events.outcomes().forEach { outcome ->
            assertThat(outcome.result).isEqualTo(ControlResult.FAILED)
            assertThat(outcome.failures).containsExactly(
                ControlLayer.PACKAGE_MANAGER,
                ControlFailure.ChannelUnavailable,
                ControlLayer.INTENT_FIREWALL,
                ControlFailure.ChannelUnavailable,
            )
        }
    }

    @Test
    fun givenRootDeniedAfterTheChannelDied_whenEnabledTwice_thenBothAttemptsFailAlike() = runTest {
        // The channel still reads as ready after its process died: the first read through it fails
        // and marks it lost, and a later connect is refused.
        val fixture = ControlFixture()
        fixture.blockInIfw(BOOT)
        fixture.files.connected = false
        fixture.channel.connectResult = ChannelState.Unavailable(UnavailableReason.DENIED)
        var reads = 0
        fixture.system.beforeRead = {
            reads++
            fixture.system.readFailure = if (reads == 1) ReadFailure.ChannelUnavailable else null
            if (reads == 1) fixture.channel.mutableState.value = ChannelState.Unavailable(UnavailableReason.FAILED)
        }

        val first = fixture.run(enable(BOOT)).outcomeOf(BOOT)
        val second = fixture.run(enable(BOOT)).outcomeOf(BOOT)

        assertThat(fixture.executor.calls).isEmpty()
        listOf(first, second).forEach { outcome ->
            assertThat(outcome.result).isEqualTo(ControlResult.FAILED)
            assertThat(outcome.state).isEqualTo(ControlState(PmSetting.DEFAULT, IfwBlock.BLOCKED))
            assertThat(outcome.failures).containsExactly(ControlLayer.INTENT_FIREWALL, ControlFailure.ChannelUnavailable)
        }
    }

    @Test
    fun givenSettingUnreadableBefore_whenUnblockClearsItButTheRuleStays_thenFailedNotPartial() = runTest {
        val fixture = ControlFixture()
        fixture.blockInIfw(MAIN)
        fixture.files.writeFailure = IOException("Disk full")
        var reads = 0
        fixture.system.beforeRead = {
            reads++
            fixture.system.readFailure = if (reads == 1) ReadFailure.ChannelUnavailable else null
        }

        val outcome = fixture.run(unblock(MAIN)).outcomeOf(MAIN)

        // The setting was cleared as it might have been a block, but it may have been clear already.
        assertThat(fixture.executor.calls).hasSize(1)
        assertThat(outcome.result).isEqualTo(ControlResult.FAILED)
        assertThat(outcome.failures).containsExactly(ControlLayer.INTENT_FIREWALL, ControlFailure.WriteFailed("Disk full"))
    }

    @Test
    fun givenNoChannel_whenEnableNeedsBothLayers_thenEachListsChannelUnavailableAndFailed() = runTest {
        val fixture = ControlFixture(NOT_CHECKED)
        val shippedOff = declaration("Off", ComponentType.RECEIVER, enabledInManifest = false)
        fixture.blockInIfw(shippedOff)
        fixture.files.connected = false
        fixture.channel.connectResult = ChannelState.Unavailable(UnavailableReason.DENIED)

        repeat(2) {
            val outcome = fixture.run(enable(shippedOff)).outcomeOf(shippedOff)

            assertThat(outcome.result).isEqualTo(ControlResult.FAILED)
            assertThat(outcome.failures).containsExactly(
                ControlLayer.PACKAGE_MANAGER,
                ControlFailure.ChannelUnavailable,
                ControlLayer.INTENT_FIREWALL,
                ControlFailure.ChannelUnavailable,
            )
        }
    }

    @Test
    fun givenPmClearedThenTheRuleStoreThrows_whenUnblock_thenPartialWithOnlyTheFirewallFailed() = runTest {
        val fixture = ControlFixture { store ->
            object : IfwStore by store {
                override suspend fun setBlocked(
                    packageName: String,
                    changes: Map<ComponentDeclaration, Boolean>,
                    onWrite: ((Collection<ComponentId>) -> Unit)?,
                ): IfwResult<Map<ComponentId, IfwBlock>> = throw IllegalStateException("Store broke")
            }
        }
        fixture.system.put(OWNER, MAIN.id, PmSetting.DISABLED)
        fixture.blockInIfw(MAIN)

        val outcome = fixture.run(unblock(MAIN)).outcomeOf(MAIN)

        assertThat(fixture.pm(MAIN)).isEqualTo(PmSetting.DEFAULT)
        assertThat(outcome.result).isEqualTo(ControlResult.PARTIAL)
        assertThat(outcome.state).isEqualTo(ControlState(PmSetting.DEFAULT, IfwBlock.BLOCKED))
        assertThat(outcome.failures).containsExactly(ControlLayer.INTENT_FIREWALL, ControlFailure.SystemError("java.lang.IllegalStateException: Store broke"))
    }

    @Test
    fun givenPmCallThrows_whenBlockWithBoth_thenOnlyThePmLayerFailsAndTheRuleIsWritten() = runTest {
        val fixture = ControlFixture()
        fixture.system.beforeSet = { throw IllegalStateException("Binder broke") }

        val outcome = fixture.run(block(BOTH, MAIN)).outcomeOf(MAIN)

        assertThat(outcome.result).isEqualTo(ControlResult.PARTIAL)
        assertThat(outcome.state).isEqualTo(ControlState(PmSetting.DEFAULT, IfwBlock.BLOCKED))
        assertThat(outcome.failures).containsExactly(ControlLayer.PACKAGE_MANAGER, ControlFailure.SystemError("java.lang.IllegalStateException: Binder broke"))
    }

    @Test
    fun givenShellAndAnOrdinaryApp_whenEnabledThroughEveryLayer_thenOnlyThePmLayerIsJudged() = runTest {
        val fixture = ControlFixture(SHELL)
        fixture.files.noAccess = true
        fixture.system.put(OWNER, MAIN.id, PmSetting.DISABLED)

        val events = fixture.run(enable(MAIN, SYNC))

        val blocked = events.outcomeOf(MAIN)
        assertThat(blocked.result).isEqualTo(ControlResult.FAILED)
        assertThat(blocked.failures.keys).containsExactly(ControlLayer.PACKAGE_MANAGER)
        assertThat(blocked.failures.getValue(ControlLayer.PACKAGE_MANAGER)).isInstanceOf(ControlFailure.NotPermitted::class.java)
        // Nothing to do for a component the PackageManager lets run; its firewall state stays unread.
        val clear = events.outcomeOf(SYNC)
        assertThat(clear.result).isEqualTo(ControlResult.ACHIEVED)
        assertThat(clear.state).isEqualTo(ControlState(PmSetting.DEFAULT, IfwBlock.UNKNOWN))
        assertThat(fixture.files.operations.filterNot { it.startsWith("peek") }).isEmpty()
    }

    @Test
    fun givenShell_whenBlockWithBoth_thenTheFirewallLayerIsStillNotPermitted() = runTest {
        val fixture = ControlFixture(SHELL)
        fixture.files.noAccess = true
        fixture.system.testOnly += PKG

        val outcome = fixture.run(block(BOTH, MAIN)).outcomeOf(MAIN)

        assertThat(outcome.result).isEqualTo(ControlResult.PARTIAL)
        assertThat(outcome.failures.keys).containsExactly(ControlLayer.INTENT_FIREWALL)
        assertThat(outcome.failures.getValue(ControlLayer.INTENT_FIREWALL)).isInstanceOf(ControlFailure.NotPermitted::class.java)
    }

    @Test
    fun givenShell_whenUnblockOnlyTheFirewall_thenNotPermitted() = runTest {
        val fixture = ControlFixture(SHELL)
        fixture.files.noAccess = true

        val outcome = fixture.run(unblock(MAIN, layers = IFW_ONLY)).outcomeOf(MAIN)

        assertThat(outcome.result).isEqualTo(ControlResult.FAILED)
        assertThat(outcome.failures.getValue(ControlLayer.INTENT_FIREWALL)).isInstanceOf(ControlFailure.NotPermitted::class.java)
    }

    @Test
    fun givenChannelLostInFirstPackage_whenNextPackage_thenNotConnectedAgainAndNothingSent() = runTest {
        val fixture = ControlFixture()
        fixture.system.beforeSet = {
            fixture.system.callFailure = CallFailure.ChannelUnavailable
            fixture.channel.mutableState.value = ChannelState.Unavailable(UnavailableReason.FAILED)
        }

        val events = fixture.run(block(PM_ONLY, MAIN, SYNC, OTHER_MAIN))

        assertThat(fixture.executor.calls.map { it.components.size }).containsExactly(2)
        assertThat(fixture.channel.connectCount).isEqualTo(0)
        assertThat(events.outcomes().map { it.failures[ControlLayer.PACKAGE_MANAGER] }).containsExactly(
            ControlFailure.ChannelUnavailable,
            ControlFailure.ChannelUnavailable,
            ControlFailure.ChannelUnavailable,
        )
    }

    @Test
    fun givenRulesUnreadable_whenBlockWithBoth_thenPmKeptAndPartial() = runTest {
        val fixture = ControlFixture()
        fixture.files.unreadablePackages += PKG

        val events = fixture.run(block(BOTH, MAIN))

        val outcome = events.outcomeOf(MAIN)
        assertThat(outcome.result).isEqualTo(ControlResult.PARTIAL)
        assertThat(outcome.state).isEqualTo(ControlState(PmSetting.DISABLED, IfwBlock.UNKNOWN))
        assertThat(outcome.failures).containsExactly(ControlLayer.INTENT_FIREWALL, ControlFailure.RulesUnreadable)
        assertThat(fixture.pm(MAIN)).isEqualTo(PmSetting.DISABLED)
    }

    @Test
    fun givenRulesUnreadableAndPmClear_whenUnblock_thenFailedAsNothingMoved() = runTest {
        val fixture = ControlFixture()
        fixture.files.unreadablePackages += PKG

        val events = fixture.run(unblock(MAIN))

        val outcome = events.outcomeOf(MAIN)
        assertThat(outcome.result).isEqualTo(ControlResult.FAILED)
        assertThat(outcome.state).isEqualTo(ControlState(PmSetting.DEFAULT, IfwBlock.UNKNOWN))
        assertThat(outcome.failures).containsExactly(ControlLayer.INTENT_FIREWALL, ControlFailure.RulesUnreadable)
    }

    @Test
    fun givenRuleWriteFails_whenUnblockBothLayers_thenPmClearedAndPartial() = runTest {
        val fixture = ControlFixture()
        fixture.system.put(OWNER, MAIN.id, PmSetting.DISABLED)
        fixture.blockInIfw(MAIN)
        fixture.files.writeFailure = IOException("Disk full")

        val events = fixture.run(unblock(MAIN))

        val outcome = events.outcomeOf(MAIN)
        assertThat(outcome.result).isEqualTo(ControlResult.PARTIAL)
        assertThat(outcome.state).isEqualTo(ControlState(PmSetting.DEFAULT, IfwBlock.BLOCKED))
        assertThat(outcome.failures).containsExactly(ControlLayer.INTENT_FIREWALL, ControlFailure.WriteFailed("Disk full"))
    }

    @Test
    fun givenPmDisabledAndRulesUnreadable_whenUnblock_thenPmClearedAndPartial() = runTest {
        val fixture = ControlFixture()
        fixture.system.put(OWNER, MAIN.id, PmSetting.DISABLED)
        fixture.files.unreadablePackages += PKG

        val events = fixture.run(unblock(MAIN))

        val outcome = events.outcomeOf(MAIN)
        assertThat(outcome.result).isEqualTo(ControlResult.PARTIAL)
        assertThat(outcome.state).isEqualTo(ControlState(PmSetting.DEFAULT, IfwBlock.UNKNOWN))
        assertThat(outcome.failures).containsExactly(ControlLayer.INTENT_FIREWALL, ControlFailure.RulesUnreadable)
    }

    @Test
    fun givenRuleWriteFailsAndPmNotBlocked_whenUnblock_thenFailed() = runTest {
        val fixture = ControlFixture()
        fixture.blockInIfw(MAIN)
        fixture.files.writeFailure = IOException("Disk full")

        val events = fixture.run(unblock(MAIN))

        assertThat(events.outcomeOf(MAIN).result).isEqualTo(ControlResult.FAILED)
    }

    @Test
    fun givenCallSucceedsButNothingChanges_whenBlockWithPm_thenStateNotConfirmed() = runTest {
        val fixture = ControlFixture()
        fixture.system.ignoreWrites = true

        val events = fixture.run(block(PM_ONLY, MAIN, FILES))

        events.outcomes().forEach { outcome ->
            assertThat(outcome.result).isEqualTo(ControlResult.FAILED)
            assertThat(outcome.state.pm).isEqualTo(PmSetting.DEFAULT)
            assertThat(outcome.failures).containsExactly(ControlLayer.PACKAGE_MANAGER, ControlFailure.StateNotConfirmed)
        }
    }

    @Test
    fun givenCallSucceedsButNothingChanges_whenUnblock_thenStateNotConfirmed() = runTest {
        val fixture = ControlFixture()
        fixture.system.put(OWNER, MAIN.id, PmSetting.DISABLED)
        fixture.system.ignoreWrites = true

        val events = fixture.run(unblock(MAIN))

        val outcome = events.outcomeOf(MAIN)
        assertThat(outcome.result).isEqualTo(ControlResult.FAILED)
        assertThat(outcome.failures).containsExactly(ControlLayer.PACKAGE_MANAGER, ControlFailure.StateNotConfirmed)
    }

    @Test
    fun givenPackageNotInstalled_whenBlockWithPm_thenTargetNotFoundAndOtherPackagesDone() = runTest {
        val fixture = ControlFixture()
        fixture.system.missing += PKG

        val events = fixture.run(block(PM_ONLY, MAIN, OTHER_MAIN))

        val missing = events.outcomeOf(MAIN)
        assertThat(missing.result).isEqualTo(ControlResult.FAILED)
        assertThat(missing.state.pm).isEqualTo(PmSetting.UNKNOWN)
        assertThat(missing.failures.getValue(ControlLayer.PACKAGE_MANAGER)).isInstanceOf(ControlFailure.TargetNotFound::class.java)
        assertThat(events.outcomeOf(OTHER_MAIN).result).isEqualTo(ControlResult.ACHIEVED)
    }

    @Test
    fun givenMixedResults_whenApply_thenFinishedCountsEach() = runTest {
        val fixture = ControlFixture()
        fixture.files.unreadablePackages += PKG
        fixture.system.protected += FILES.id

        val events = fixture.run(block(BOTH, MAIN, FILES, OTHER_MAIN))

        assertThat(events.outcomeOf(MAIN).result).isEqualTo(ControlResult.PARTIAL)
        assertThat(events.outcomeOf(FILES).result).isEqualTo(ControlResult.FAILED)
        assertThat(events.outcomeOf(OTHER_MAIN).result).isEqualTo(ControlResult.ACHIEVED)
        assertThat(events.last()).isEqualTo(ControlEvent.Finished(achieved = 1, partial = 1, failed = 1))
    }

    @Test
    fun givenNotConnected_whenState_thenPmReadAndIfwUnknownWithoutConnecting() = runTest {
        val fixture = ControlFixture(NOT_CHECKED)
        fixture.files.connected = false
        fixture.system.put(OWNER, MAIN.id, PmSetting.DISABLED)

        val state = fixture.control.state(AppKey(PKG, OWNER), listOf(MAIN, FILES, OTHER_MAIN))

        assertThat(state).containsExactly(
            MAIN.id,
            ControlState(PmSetting.DISABLED, IfwBlock.UNKNOWN),
            FILES.id,
            ControlState(PmSetting.DEFAULT, IfwBlock.NOT_BLOCKED),
            OTHER_MAIN.id,
            ControlState(PmSetting.UNKNOWN, IfwBlock.UNKNOWN),
        )
        assertThat(fixture.channel.connectCount).isEqualTo(0)
    }

    @Test
    fun givenSettingsUnreadable_whenState_thenUnknownNotDefault() = runTest {
        val fixture = ControlFixture()
        fixture.system.readFailure = ReadFailure.ChannelUnavailable
        fixture.blockInIfw(SYNC)

        val state = fixture.control.state(AppKey(PKG, OWNER), listOf(MAIN, SYNC))

        assertThat(state).containsExactly(
            MAIN.id,
            ControlState(PmSetting.UNKNOWN, IfwBlock.NOT_BLOCKED),
            SYNC.id,
            ControlState(PmSetting.UNKNOWN, IfwBlock.BLOCKED),
        )
    }
}
