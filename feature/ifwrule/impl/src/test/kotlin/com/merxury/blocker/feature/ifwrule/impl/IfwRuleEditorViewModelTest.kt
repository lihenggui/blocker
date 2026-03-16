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

import com.merxury.blocker.core.testing.controller.FakeIntentFirewall
import com.merxury.blocker.core.testing.util.MainDispatcherRule
import com.merxury.blocker.feature.ifwrule.impl.model.BlockMode
import com.merxury.blocker.feature.ifwrule.impl.model.ConditionUiState
import com.merxury.blocker.feature.ifwrule.impl.model.MatchMode
import com.merxury.blocker.feature.ifwrule.impl.model.RuleEditorScreenUiState
import com.merxury.core.ifw.model.IfwComponentType
import com.merxury.core.ifw.model.IfwFilter
import com.merxury.core.ifw.model.IfwRule
import com.merxury.core.ifw.model.IfwRules
import com.merxury.core.ifw.model.StringMatcher
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class IfwRuleEditorViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val pkg = "com.example"
    private val comp = "com.example.BootReceiver"

    @Test
    fun givenNoExistingRules_whenLoaded_thenShowsDefaultState() = runTest {
        val viewModel = createViewModel()
        val state = viewModel.uiState.value
        assertIs<RuleEditorScreenUiState.Success>(state)
        assertEquals(BlockMode.ALL, state.editor.blockMode)
        assertTrue(state.editor.conditions.isEmpty())
    }

    @Test
    fun givenExistingComponentFilter_whenLoaded_thenShowsBlockAll() = runTest {
        val ifw = FakeIntentFirewall()
        ifw.addComponentFilter(pkg, comp)
        val viewModel = createViewModel(ifw)
        val state = viewModel.uiState.value
        assertIs<RuleEditorScreenUiState.Success>(state)
        assertEquals(BlockMode.ALL, state.editor.blockMode)
    }

    @Test
    fun givenExistingConditionalRule_whenLoaded_thenShowsConditions() = runTest {
        val ifw = FakeIntentFirewall()
        val filter = IfwFilter.And(
            listOf(
                IfwFilter.ComponentFilter("$pkg/$comp"),
                IfwFilter.Action(StringMatcher.Equals("android.intent.action.BOOT_COMPLETED")),
            ),
        )
        ifw.rules[pkg] = IfwRules(
            listOf(
                IfwRule(
                    componentType = IfwComponentType.BROADCAST,
                    filters = listOf(filter),
                ),
            ),
        )
        val viewModel = createViewModel(ifw)
        val state = viewModel.uiState.value
        assertIs<RuleEditorScreenUiState.Success>(state)
        assertEquals(BlockMode.CONDITIONAL, state.editor.blockMode)
        assertEquals(1, state.editor.conditions.size)
    }

    @Test
    fun givenUserSavesBlockAll_whenSave_thenRulesWritten() = runTest {
        val ifw = FakeIntentFirewall()
        val viewModel = createViewModel(ifw)
        assertIs<RuleEditorScreenUiState.Success>(viewModel.uiState.value)
        viewModel.save()
        val rules = ifw.getRules(pkg)
        val filters = rules.rulesFor(IfwComponentType.BROADCAST).flatMap { it.filters }
        assertTrue(filters.any { it is IfwFilter.ComponentFilter })
    }

    @Test
    fun givenUserAddsCondition_whenUpdateConditions_thenStateUpdated() = runTest {
        val viewModel = createViewModel()
        assertIs<RuleEditorScreenUiState.Success>(viewModel.uiState.value)
        val condition = ConditionUiState.ActionFilter(
            id = "1",
            matchMode = MatchMode.EXACT,
            value = "BOOT_COMPLETED",
        )
        viewModel.updateBlockMode(BlockMode.CONDITIONAL)
        viewModel.addCondition(condition)
        val state = viewModel.uiState.value
        assertIs<RuleEditorScreenUiState.Success>(state)
        assertEquals(1, state.editor.conditions.size)
    }

    private fun createViewModel(
        ifw: FakeIntentFirewall = FakeIntentFirewall(),
    ) = IfwRuleEditorViewModel(
        intentFirewall = ifw,
        packageName = pkg,
        componentName = comp,
        componentTypeTag = "broadcast",
    )
}
