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
import com.merxury.blocker.core.model.component.ComponentDeclaration
import com.merxury.blocker.core.model.component.ComponentKey
import com.merxury.blocker.core.model.component.ControlState
import com.merxury.blocker.core.model.component.IfwBlock
import com.merxury.blocker.core.model.component.PmSetting
import com.merxury.blocker.core.model.control.ComponentOutcome
import com.merxury.blocker.core.model.control.ControlResult
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
        val events = fixture.run(block(IFW_ONLY, MAIN, SYNC, BOOT))

        assertThat(events.outcomes()).containsExactly(
            achieved(MAIN, PmSetting.DEFAULT, IfwBlock.BLOCKED),
            achieved(SYNC, PmSetting.DEFAULT, IfwBlock.BLOCKED),
            achieved(BOOT, PmSetting.DEFAULT, IfwBlock.BLOCKED),
        ).inOrder()
        assertThat(fixture.executor.calls).isEmpty()
        assertThat(fixture.ifwWrites).containsExactly("write $PKG")
    }

    @Test
    fun givenRunnableComponent_whenBlockWithBoth_thenBothLayersBlocked() = runTest {
        val events = fixture.run(block(BOTH, MAIN))

        assertThat(events.outcomeOf(MAIN)).isEqualTo(achieved(MAIN, PmSetting.DISABLED, IfwBlock.BLOCKED))
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
    fun givenBlockedInBothLayers_whenUnblockWithPmLayers_thenEveryLayerCleared() = runTest {
        fixture.system.put(OWNER, SYNC.id, PmSetting.DISABLED)
        fixture.blockInIfw(SYNC)

        val events = fixture.run(unblock(SYNC, layers = PM_ONLY))

        assertThat(events.outcomeOf(SYNC)).isEqualTo(achieved(SYNC, PmSetting.DEFAULT, IfwBlock.NOT_BLOCKED))
    }

    @Test
    fun givenPmDisabledOnly_whenUnblockWithIfwLayers_thenPmClearedToo() = runTest {
        fixture.system.put(OWNER, BOOT.id, PmSetting.DISABLED)

        val events = fixture.run(unblock(BOOT, layers = IFW_ONLY))

        assertThat(events.outcomeOf(BOOT)).isEqualTo(achieved(BOOT, PmSetting.DEFAULT, IfwBlock.NOT_BLOCKED))
        assertThat(fixture.ifwWrites).isEmpty()
    }

    @Test
    fun givenDisabledProvider_whenUnblock_thenDefault() = runTest {
        fixture.system.put(OWNER, FILES.id, PmSetting.DISABLED)

        val events = fixture.run(unblock(FILES))

        assertThat(events.outcomeOf(FILES)).isEqualTo(achieved(FILES, PmSetting.DEFAULT, IfwBlock.NOT_BLOCKED))
    }

    @Test
    fun givenManifestDisabledAtDefault_whenUnblock_thenEnabled() = runTest {
        val events = fixture.run(unblock(HIDDEN))

        assertThat(events.outcomeOf(HIDDEN)).isEqualTo(achieved(HIDDEN, PmSetting.ENABLED, IfwBlock.NOT_BLOCKED))
    }

    @Test
    fun givenManifestDisabledAndDisabled_whenUnblock_thenEnabled() = runTest {
        fixture.system.put(OWNER, HIDDEN.id, PmSetting.DISABLED)

        val events = fixture.run(unblock(HIDDEN))

        assertThat(events.outcomeOf(HIDDEN)).isEqualTo(achieved(HIDDEN, PmSetting.ENABLED, IfwBlock.NOT_BLOCKED))
    }

    @Test
    fun givenManifestEnabledAndDisabledComponents_whenUnblock_thenOneCallPerTargetSetting() = runTest {
        fixture.system.put(OWNER, MAIN.id, PmSetting.DISABLED)
        fixture.system.put(OWNER, SYNC.id, PmSetting.DISABLED)
        fixture.system.put(OWNER, HIDDEN.id, PmSetting.DISABLED)

        fixture.run(unblock(MAIN, SYNC, HIDDEN))

        assertThat(fixture.system.log).containsExactly("read $PKG", "set $PKG 2 DEFAULT", "set $PKG 1 ENABLED", "read $PKG").inOrder()
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
