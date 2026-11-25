/*
 * Copyright 2025 Blocker
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

package com.merxury.blocker.feature.ifwrule.impl

import com.merxury.blocker.core.testing.controller.FakeIfwFileSystem
import com.merxury.blocker.core.testing.util.MainDispatcherRule
import com.merxury.core.ifw.IntentFirewallStore
import com.merxury.core.ifw.editor.IfwEditorConditionKind
import com.merxury.core.ifw.editor.IfwEditorNode
import com.merxury.core.ifw.editor.IfwEditorStringMatcherMode
import com.merxury.core.ifw.getOrThrow
import com.merxury.core.ifw.model.IfwComponentType
import com.merxury.core.ifw.model.IfwFilter
import com.merxury.core.ifw.model.IfwRule
import com.merxury.core.ifw.model.IfwRules
import com.merxury.core.ifw.model.StringMatcher
import com.merxury.core.ifw.xml.IfwXmlDeserializer
import com.merxury.core.ifw.xml.IfwXmlSerializer
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], manifest = Config.NONE)
class IfwRuleEditorViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val packageName = "com.example"
    private val componentName = "com.example.BootReceiver"

    @Test
    fun givenNoExistingRules_whenLoaded_thenShowsDefaultState() = runTest {
        val viewModel = createViewModel()

        val state = assertIs<RuleEditorScreenUiState.Success>(viewModel.uiState.value)
        assertEquals(BlockMode.ALL, state.editor.blockMode)
        assertTrue(state.editor.rootGroup.children.isEmpty())
    }

    @Test
    fun givenExistingConditionalRule_whenLoaded_thenShowsNestedRootGroup() = runTest {
        val ifw = store()
        val rules = IfwRules(
            listOf(
                IfwRule(
                    componentType = IfwComponentType.BROADCAST,
                    filters = listOf(
                        IfwFilter.And(
                            listOf(
                                IfwFilter.ComponentFilter("$packageName/$componentName"),
                                IfwFilter.Action(StringMatcher.Equals("android.intent.action.BOOT_COMPLETED")),
                            ),
                        ),
                    ),
                ),
            ),
        )

        ifw.update(packageName) { rules }.getOrThrow()
        val viewModel = createViewModel(ifw)

        val state = assertIs<RuleEditorScreenUiState.Success>(viewModel.uiState.value)
        assertEquals(BlockMode.CONDITIONAL, state.editor.blockMode)
        assertEquals(1, state.editor.rootGroup.children.size)
    }

    @Test
    fun givenUpdatedRootGroup_whenUpdateRootGroup_thenStateChanges() = runTest {
        val viewModel = createViewModel()
        val rootGroup = IfwEditorNode.Group(
            children = listOf(
                IfwEditorNode.Condition(
                    kind = IfwEditorConditionKind.ACTION,
                    matcherMode = IfwEditorStringMatcherMode.EXACT,
                    value = "BOOT_COMPLETED",
                ),
            ),
        )

        viewModel.updateBlockMode(BlockMode.CONDITIONAL)
        viewModel.updateRootGroup(rootGroup)

        val state = assertIs<RuleEditorScreenUiState.Success>(viewModel.uiState.value)
        assertEquals(1, state.editor.rootGroup.children.size)
    }

    @Test
    fun givenUserSavesBlockAll_whenSave_thenRulesWritten() = runTest {
        val ifw = store()
        val viewModel = createViewModel(ifw)

        viewModel.save()

        val filters = ifw.rules(packageName).getOrThrow()
            .rulesFor(IfwComponentType.BROADCAST)
            .flatMap { rule -> rule.filters }
        assertTrue(filters.any { filter -> filter is IfwFilter.ComponentFilter })
    }

    private fun store() = IntentFirewallStore(FakeIfwFileSystem(), IfwXmlSerializer(), IfwXmlDeserializer(), "com.merxury.blocker.test")

    private fun createViewModel(
        ifw: IntentFirewallStore = store(),
    ) = IfwRuleEditorViewModel(
        ifwStore = ifw,
        packageName = packageName,
        componentName = componentName,
        componentTypeTag = "broadcast",
    )
}
