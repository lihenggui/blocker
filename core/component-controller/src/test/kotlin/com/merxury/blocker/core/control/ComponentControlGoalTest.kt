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

import android.content.pm.PackageManager
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import com.merxury.blocker.core.model.ComponentType
import com.merxury.blocker.core.model.component.ComponentDeclaration
import com.merxury.blocker.core.model.component.ComponentKey
import com.merxury.blocker.core.model.component.ControlLayer
import com.merxury.blocker.core.model.component.ControlState
import com.merxury.blocker.core.model.component.IfwBlock
import com.merxury.blocker.core.model.component.PmSetting
import com.merxury.blocker.core.model.control.ComponentOutcome
import com.merxury.blocker.core.model.control.ControlGoal
import com.merxury.blocker.core.model.control.ControlRequest
import com.merxury.blocker.core.model.control.ControlResult
import com.merxury.blocker.core.model.privileged.ChannelState
import com.merxury.blocker.core.model.privileged.PrivilegedIdentity
import com.merxury.blocker.core.testing.controller.FakeIfwFileSystem
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

private val MAIN = declaration("Main")
private val SYNC = declaration("Sync", ComponentType.SERVICE)
private val BOOT = declaration("Boot", ComponentType.RECEIVER)
private val FILES = declaration("Files", ComponentType.PROVIDER)
private val HIDDEN = declaration("Hidden", enabledInManifest = false)

/** Each goal with each layer, for each kind of component. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ComponentControlGoalTest {

    private val fixture = ControlFixture()

    private fun achieved(component: ComponentDeclaration, pm: PmSetting, ifw: IfwBlock) = ComponentOutcome(ComponentKey(component.id, OWNER), ControlState(pm, ifw), ControlResult.ACHIEVED, emptyMap())

    @Test
    fun givenRunnableComponents_whenBlockWithPm_thenDisabledInOneCallAndRulesUntouched() = runTest {
        val events = fixture.run(block(PM_ONLY, MAIN, SYNC, BOOT))

        assertThat(events.outcomes()).containsExactly(
            achieved(MAIN, PmSetting.DISABLED, IfwBlock.NOT_BLOCKED),
            achieved(SYNC, PmSetting.DISABLED, IfwBlock.NOT_BLOCKED),
            achieved(BOOT, PmSetting.DISABLED, IfwBlock.NOT_BLOCKED),
        ).inOrder()
        assertThat(fixture.system.log).containsExactly("read $PKG", "set $PKG 3 DISABLED", "read $PKG").inOrder()
        assertThat(fixture.ifwWrites).isEmpty()
    }

    @Test
    fun givenRunnableComponents_whenBlockWithIfw_thenRuleWrittenOnceAndPmUntouched() = runTest {
        val events = fixture.run(block(IFW_ONLY, MAIN, SYNC))

        assertThat(events.outcomes()).containsExactly(
            achieved(MAIN, PmSetting.DEFAULT, IfwBlock.BLOCKED),
            achieved(SYNC, PmSetting.DEFAULT, IfwBlock.BLOCKED),
        ).inOrder()
        assertThat(fixture.executor.calls).isEmpty()
        assertThat(fixture.ifwWrites).containsExactly("write $PKG")
    }

    @Test
    fun givenRunnableReceiver_whenBlockWithIfw_thenPmDisabledAndNoRuleWritten() = runTest {
        val events = fixture.run(block(IFW_ONLY, BOOT))

        assertThat(events.outcomeOf(BOOT)).isEqualTo(achieved(BOOT, PmSetting.DISABLED, IfwBlock.NOT_BLOCKED))
        val disable = fixture.executor.calls.single()
        assertThat(disable.components.single().className).isEqualTo(BOOT.id.className)
        assertThat(disable.newState).isEqualTo(PackageManager.COMPONENT_ENABLED_STATE_DISABLED)
        assertThat(fixture.ifwWrites).isEmpty()
    }

    @Test
    fun givenReceiverBlockedByAnEarlierFirewallRule_whenBlockedWithIfw_thenTheRuleStays() = runTest {
        fixture.blockInIfw(BOOT)

        val events = fixture.run(block(IFW_ONLY, BOOT))

        assertThat(events.outcomeOf(BOOT)).isEqualTo(achieved(BOOT, PmSetting.DISABLED, IfwBlock.BLOCKED))
        assertThat(fixture.ifwWrites).isEmpty()
    }

    @Test
    fun givenRunnableReceiver_whenBlockWithBoth_thenBothLayersBlocked() = runTest {
        val events = fixture.run(block(BOTH, BOOT))

        assertThat(events.outcomeOf(BOOT)).isEqualTo(achieved(BOOT, PmSetting.DISABLED, IfwBlock.BLOCKED))
    }

    @Test
    fun givenProvider_whenBlockWithIfw_thenPmDisabledInsteadAndNoRule() = runTest {
        val events = fixture.run(block(IFW_ONLY, FILES))

        assertThat(events.outcomeOf(FILES)).isEqualTo(achieved(FILES, PmSetting.DISABLED, IfwBlock.NOT_BLOCKED))
        assertThat(fixture.ifwWrites).isEmpty()
    }

    @Test
    fun givenProvider_whenBlockWithBoth_thenOnlyPmDisabled() = runTest {
        val events = fixture.run(block(BOTH, FILES, MAIN))

        assertThat(events.outcomeOf(FILES)).isEqualTo(achieved(FILES, PmSetting.DISABLED, IfwBlock.NOT_BLOCKED))
        assertThat(events.outcomeOf(MAIN)).isEqualTo(achieved(MAIN, PmSetting.DISABLED, IfwBlock.BLOCKED))
        assertThat(fixture.files.rulesText()).doesNotContain("Files")
    }

    @Test
    fun givenManifestDisabledComponent_whenBlockWithPm_thenDisabledSetting() = runTest {
        val events = fixture.run(block(PM_ONLY, HIDDEN))

        assertThat(events.outcomeOf(HIDDEN)).isEqualTo(achieved(HIDDEN, PmSetting.DISABLED, IfwBlock.NOT_BLOCKED))
    }

    @Test
    fun givenConditionalRule_whenBlockWithIfw_thenUnconditionalRuleAddedAndConditionalKept() = runTest {
        fixture.files.putFile(PKG, conditionalRule(MAIN))

        val events = fixture.run(block(IFW_ONLY, MAIN))

        assertThat(events.outcomeOf(MAIN)).isEqualTo(achieved(MAIN, PmSetting.DEFAULT, IfwBlock.BLOCKED))
        assertThat(fixture.files.rulesText()).contains("com.example.nobody")
    }

    @Test
    fun givenBlockedInBothLayers_whenUnblock_thenNoOverrideLeftAndRuleFileRemoved() = runTest {
        fixture.system.put(OWNER, MAIN.id, PmSetting.DISABLED)
        fixture.blockInIfw(MAIN)

        val events = fixture.run(unblock(MAIN))

        assertThat(events.outcomeOf(MAIN)).isEqualTo(achieved(MAIN, PmSetting.DEFAULT, IfwBlock.NOT_BLOCKED))
        assertThat(fixture.system.settings).isEmpty()
        assertThat(fixture.ifwWrites).containsExactly("delete $PKG")
        assertThat(fixture.files.fileContent(PKG)).isNull()
    }

    @Test
    fun givenBlockedInBothLayers_whenUnblockWithPm_thenOnlyPmClearedAndAchievedWithRuleShown() = runTest {
        fixture.system.put(OWNER, SYNC.id, PmSetting.DISABLED)
        fixture.blockInIfw(SYNC)

        val events = fixture.run(unblock(SYNC, layers = PM_ONLY))

        assertThat(events.outcomeOf(SYNC)).isEqualTo(achieved(SYNC, PmSetting.DEFAULT, IfwBlock.BLOCKED))
        assertThat(fixture.ifwWrites).isEmpty()
        assertThat(fixture.files.rulesText()).contains(SYNC.id.flatten())
    }

    @Test
    fun givenReceiverBlockedInBothLayers_whenUnblockWithIfw_thenBothLayersCleared() = runTest {
        fixture.system.put(OWNER, BOOT.id, PmSetting.DISABLED)
        fixture.blockInIfw(BOOT)

        val events = fixture.run(unblock(BOOT, layers = IFW_ONLY))

        assertThat(events.outcomeOf(BOOT)).isEqualTo(achieved(BOOT, PmSetting.DEFAULT, IfwBlock.NOT_BLOCKED))
        assertThat(fixture.system.settings).isEmpty()
        assertThat(fixture.ifwWrites).containsExactly("delete $PKG")
    }

    @Test
    fun givenReceiverDisabledInPm_whenUnblockWithIfwAsShell_thenOnlyPmClearedAndJudged() = runTest {
        val fixture = ControlFixture(ChannelState.Ready(PrivilegedIdentity.SHELL))
        fixture.files.noAccess = true
        fixture.system.testOnly += PKG
        fixture.system.put(OWNER, BOOT.id, PmSetting.DISABLED)

        val events = fixture.run(unblock(BOOT, layers = IFW_ONLY))

        assertThat(events.outcomeOf(BOOT)).isEqualTo(achieved(BOOT, PmSetting.DEFAULT, IfwBlock.UNKNOWN))
        assertThat(fixture.system.settings).isEmpty()
        assertThat(fixture.ifwWrites).isEmpty()
    }

    @Test
    fun givenDisabledProvider_whenUnblockWithIfw_thenPmClearedInstead() = runTest {
        fixture.system.put(OWNER, FILES.id, PmSetting.DISABLED)

        val events = fixture.run(unblock(FILES, layers = IFW_ONLY))

        assertThat(events.outcomeOf(FILES)).isEqualTo(achieved(FILES, PmSetting.DEFAULT, IfwBlock.NOT_BLOCKED))
    }

    @Test
    fun givenPmRefused_whenUnblockWithPmOfComponentAlsoBlockedByRule_thenFailedNotPartial() = runTest {
        fixture.system.put(OWNER, MAIN.id, PmSetting.DISABLED)
        fixture.blockInIfw(MAIN)
        fixture.system.protected += MAIN.id

        val events = fixture.run(unblock(MAIN, layers = PM_ONLY))

        val outcome = events.outcomeOf(MAIN)
        assertThat(outcome.result).isEqualTo(ControlResult.FAILED)
        assertThat(outcome.state).isEqualTo(ControlState(PmSetting.DISABLED, IfwBlock.BLOCKED))
        assertThat(outcome.failures.keys).containsExactly(ControlLayer.PACKAGE_MANAGER)
    }

    @Test
    fun givenDisabledProvider_whenUnblock_thenDefault() = runTest {
        fixture.system.put(OWNER, FILES.id, PmSetting.DISABLED)

        val events = fixture.run(unblock(FILES))

        assertThat(events.outcomeOf(FILES)).isEqualTo(achieved(FILES, PmSetting.DEFAULT, IfwBlock.NOT_BLOCKED))
    }

    @Test
    fun givenManifestDisabledAtDefault_whenUnblock_thenLeftAtDefaultWithoutACall() = runTest {
        val events = fixture.run(unblock(HIDDEN))

        assertThat(events.outcomeOf(HIDDEN)).isEqualTo(achieved(HIDDEN, PmSetting.DEFAULT, IfwBlock.NOT_BLOCKED))
        assertThat(fixture.executor.calls).isEmpty()
    }

    @Test
    fun givenManifestDisabledAtDefault_whenEnable_thenEnabled() = runTest {
        val events = fixture.run(enable(HIDDEN))

        assertThat(events.outcomeOf(HIDDEN)).isEqualTo(achieved(HIDDEN, PmSetting.ENABLED, IfwBlock.NOT_BLOCKED))
    }

    @Test
    fun givenManifestDisabledAndDisabled_whenUnblock_thenBackToTheManifestDefault() = runTest {
        fixture.system.put(OWNER, HIDDEN.id, PmSetting.DISABLED)

        val events = fixture.run(unblock(HIDDEN))

        assertThat(events.outcomeOf(HIDDEN)).isEqualTo(achieved(HIDDEN, PmSetting.DEFAULT, IfwBlock.NOT_BLOCKED))
    }

    @Test
    fun givenManifestDisabledAndDisabled_whenEnable_thenEnabled() = runTest {
        fixture.system.put(OWNER, HIDDEN.id, PmSetting.DISABLED)

        val events = fixture.run(enable(HIDDEN))

        assertThat(events.outcomeOf(HIDDEN)).isEqualTo(achieved(HIDDEN, PmSetting.ENABLED, IfwBlock.NOT_BLOCKED))
    }

    @Test
    fun givenManifestEnabledAndDisabledComponents_whenEnable_thenOneCallPerTargetSetting() = runTest {
        fixture.system.put(OWNER, MAIN.id, PmSetting.DISABLED)
        fixture.system.put(OWNER, SYNC.id, PmSetting.DISABLED)
        fixture.system.put(OWNER, HIDDEN.id, PmSetting.DISABLED)

        fixture.run(enable(MAIN, SYNC, HIDDEN))

        assertThat(fixture.system.log).containsExactly("read $PKG", "set $PKG 2 DEFAULT", "set $PKG 1 ENABLED", "read $PKG").inOrder()
    }

    @Test
    fun givenManifestEnabledAndDisabledComponents_whenUnblock_thenAllInOneCallToDefault() = runTest {
        fixture.system.put(OWNER, MAIN.id, PmSetting.DISABLED)
        fixture.system.put(OWNER, SYNC.id, PmSetting.DISABLED)
        fixture.system.put(OWNER, HIDDEN.id, PmSetting.DISABLED)

        fixture.run(unblock(MAIN, SYNC, HIDDEN))

        assertThat(fixture.system.log).containsExactly("read $PKG", "set $PKG 3 DEFAULT", "read $PKG").inOrder()
    }

    @Test
    fun givenEveryGoalLayerManifestAndSetting_whenControlled_thenTheTruthTableHolds() = runTest {
        // From a DISABLED setting or an unconditional rule, or neither: what each goal leaves.
        for (goal in ControlGoal.entries) {
            for (layers in listOf(PM_ONLY, IFW_ONLY, BOTH)) {
                for (enabledInManifest in listOf(true, false)) {
                    for (setting in listOf(PmSetting.DEFAULT, PmSetting.ENABLED, PmSetting.DISABLED)) {
                        for (ruled in listOf(false, true)) {
                            val component = declaration("Case", ComponentType.SERVICE, enabledInManifest = enabledInManifest)
                            val case = ControlFixture()
                            case.system.put(OWNER, component.id, setting)
                            if (ruled) case.blockInIfw(component)

                            val outcome = case.run(ControlRequest(OWNER, listOf(component), goal, layers)).outcomeOf(component)

                            val expectedPm = if (ControlLayer.PACKAGE_MANAGER in layers) expectedSetting(goal, enabledInManifest, setting) else setting
                            val expectedIfw = when {
                                ControlLayer.INTENT_FIREWALL !in layers -> if (ruled) IfwBlock.BLOCKED else IfwBlock.NOT_BLOCKED
                                goal == ControlGoal.BLOCK -> IfwBlock.BLOCKED
                                else -> IfwBlock.NOT_BLOCKED
                            }
                            val label = "$goal $layers manifest=$enabledInManifest $setting ruled=$ruled"
                            assertWithMessage(label).that(outcome.state).isEqualTo(ControlState(expectedPm, expectedIfw))
                            assertWithMessage(label).that(outcome.result).isEqualTo(ControlResult.ACHIEVED)
                        }
                    }
                }
            }
        }
    }

    @Test
    fun givenShellAndATestOnlyApp_whenUnblockedOrEnabledThroughEveryLayer_thenJudgedOnThePackageManagerAlone() = runTest {
        // The shell can change a test-only app's settings but can neither read nor write the rules.
        for (goal in listOf(ControlGoal.UNBLOCK, ControlGoal.ENABLE)) {
            for (enabledInManifest in listOf(true, false)) {
                for (setting in listOf(PmSetting.DEFAULT, PmSetting.ENABLED, PmSetting.DISABLED)) {
                    val component = declaration("Case", ComponentType.SERVICE, enabledInManifest = enabledInManifest)
                    val case = ControlFixture(ChannelState.Ready(PrivilegedIdentity.SHELL))
                    case.files.noAccess = true
                    case.system.testOnly += PKG
                    case.system.put(OWNER, component.id, setting)

                    val outcome = case.run(ControlRequest(OWNER, listOf(component), goal, BOTH)).outcomeOf(component)

                    val label = "$goal manifest=$enabledInManifest $setting"
                    assertWithMessage(label).that(outcome.state).isEqualTo(ControlState(expectedSetting(goal, enabledInManifest, setting), IfwBlock.UNKNOWN))
                    assertWithMessage(label).that(outcome.result).isEqualTo(ControlResult.ACHIEVED)
                    assertWithMessage(label).that(case.files.operations.filterNot { it.startsWith("peek") }).isEmpty()
                }
            }
        }
    }

    private fun expectedSetting(goal: ControlGoal, enabledInManifest: Boolean, setting: PmSetting): PmSetting = when (goal) {
        ControlGoal.BLOCK -> PmSetting.DISABLED

        ControlGoal.UNBLOCK -> if (setting == PmSetting.DISABLED) PmSetting.DEFAULT else setting

        ControlGoal.ENABLE -> when {
            setting == PmSetting.ENABLED || (setting == PmSetting.DEFAULT && enabledInManifest) -> setting
            enabledInManifest -> PmSetting.DEFAULT
            else -> PmSetting.ENABLED
        }
    }

    @Test
    fun givenPmRefused_whenEnableShippedDisabledComponent_thenFailed() = runTest {
        fixture.system.protected += HIDDEN.id

        val outcome = fixture.run(enable(HIDDEN, layers = PM_ONLY)).outcomeOf(HIDDEN)

        assertThat(outcome.result).isEqualTo(ControlResult.FAILED)
        assertThat(outcome.state.pm).isEqualTo(PmSetting.DEFAULT)
    }

    @Test
    fun givenOnlyConditionalRule_whenUnblock_thenAchievedConditionalAndRuleKept() = runTest {
        val rule = conditionalRule(MAIN)
        fixture.files.putFile(PKG, rule)

        val events = fixture.run(unblock(MAIN))

        assertThat(events.outcomeOf(MAIN)).isEqualTo(achieved(MAIN, PmSetting.DEFAULT, IfwBlock.CONDITIONAL))
        assertThat(fixture.files.fileContent(PKG)).isEqualTo(rule)
        assertThat(fixture.ifwWrites).isEmpty()
    }

    @Test
    fun givenEnabledOverride_whenUnblock_thenLeftAsIs() = runTest {
        fixture.system.put(OWNER, MAIN.id, PmSetting.ENABLED)

        val events = fixture.run(unblock(MAIN))

        assertThat(events.outcomeOf(MAIN)).isEqualTo(achieved(MAIN, PmSetting.ENABLED, IfwBlock.NOT_BLOCKED))
        assertThat(fixture.executor.calls).isEmpty()
    }

    @Test
    fun givenAlreadyBlocked_whenBlockWithBoth_thenNoPmCallAndStoreWritesNothing() = runTest {
        fixture.system.put(OWNER, MAIN.id, PmSetting.DISABLED)
        fixture.blockInIfw(MAIN)

        val events = fixture.run(block(BOTH, MAIN))

        assertThat(events.outcomeOf(MAIN)).isEqualTo(achieved(MAIN, PmSetting.DISABLED, IfwBlock.BLOCKED))
        assertThat(fixture.system.log).containsExactly("read $PKG", "read $PKG").inOrder()
        assertThat(fixture.files.operations).contains("read $PKG")
        assertThat(fixture.ifwWrites).isEmpty()
    }

    @Test
    fun givenAlreadyBlockedInPm_whenBlockWithPm_thenNothingSentAndNoReadBack() = runTest {
        fixture.system.put(OWNER, MAIN.id, PmSetting.DISABLED)

        val events = fixture.run(block(PM_ONLY, MAIN))

        assertThat(events.outcomeOf(MAIN)).isEqualTo(achieved(MAIN, PmSetting.DISABLED, IfwBlock.NOT_BLOCKED))
        assertThat(fixture.system.log).containsExactly("read $PKG")
    }

    @Test
    fun givenCachedRuleRemovedByAnotherWriter_whenBlockWithIfw_thenStoreRereadsAndWrites() = runTest {
        fixture.blockInIfw(MAIN)
        // Another tool empties the file; the store still remembers the rule.
        fixture.files.putFile(PKG, "<rules />")

        val events = fixture.run(block(IFW_ONLY, MAIN))

        assertThat(events.outcomeOf(MAIN)).isEqualTo(achieved(MAIN, PmSetting.DEFAULT, IfwBlock.BLOCKED))
        assertThat(fixture.ifwWrites).containsExactly("write $PKG")
        assertThat(fixture.files.rulesText()).contains(MAIN.id.flatten())
    }

    @Test
    fun givenSomeAlreadyBlocked_whenBlockWithPm_thenOnlyTheOthersSent() = runTest {
        fixture.system.put(OWNER, MAIN.id, PmSetting.DISABLED)

        val events = fixture.run(block(PM_ONLY, MAIN, SYNC))

        assertThat(events.outcomes().map { it.result }).containsExactly(ControlResult.ACHIEVED, ControlResult.ACHIEVED)
        assertThat(fixture.executor.calls.single().components.map { it.className }).containsExactly(SYNC.id.className)
    }

    @Test
    fun givenAlreadyRunnable_whenUnblock_thenNoPmCallAndNoRuleWrite() = runTest {
        val events = fixture.run(unblock(MAIN, FILES))

        assertThat(events.outcomes().map { it.result }).containsExactly(ControlResult.ACHIEVED, ControlResult.ACHIEVED)
        assertThat(fixture.executor.calls).isEmpty()
        assertThat(fixture.ifwWrites).isEmpty()
    }

    @Test
    fun givenWorkProfile_whenBlockWithPm_thenOnlyThatUserChanges() = runTest {
        val events = fixture.run(block(PM_ONLY, MAIN, user = WORK))

        assertThat(events.outcomeOf(MAIN, WORK).state.pm).isEqualTo(PmSetting.DISABLED)
        assertThat(fixture.pm(MAIN, WORK)).isEqualTo(PmSetting.DISABLED)
        assertThat(fixture.pm(MAIN, OWNER)).isEqualTo(PmSetting.DEFAULT)
        assertThat(fixture.executor.calls.single().user).isEqualTo(WORK)
    }

    @Test
    @Config(sdk = [32])
    fun givenPlatformWithoutBatchCall_whenBlockWithPm_thenOneCallPerComponent() = runTest {
        val events = fixture.run(block(PM_ONLY, MAIN, SYNC, BOOT))

        assertThat(events.outcomes().map { it.result }).containsExactly(ControlResult.ACHIEVED, ControlResult.ACHIEVED, ControlResult.ACHIEVED)
        assertThat(fixture.executor.calls.map { it.components.size }).containsExactly(1, 1, 1)
    }
}

private fun conditionalRule(component: ComponentDeclaration) = """
    <rules>
      <activity block="true" log="false">
        <component-filter name="${component.id.flatten()}" />
        <sender-package name="com.example.nobody" />
      </activity>
    </rules>
""".trimIndent()

private fun FakeIfwFileSystem.rulesText(): String = fileContent(PKG).orEmpty()
