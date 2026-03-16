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

package com.merxury.blocker.feature.ifwrule.impl.model

import com.merxury.core.ifw.model.IfwComponentType
import com.merxury.core.ifw.model.IfwFilter
import com.merxury.core.ifw.model.IfwRule
import com.merxury.core.ifw.model.IfwRules
import com.merxury.core.ifw.model.SenderType
import com.merxury.core.ifw.model.StringMatcher
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class IfwRuleUiMapperTest {

    private val pkg = "com.example"
    private val comp = "com.example.BootReceiver"
    private val componentType = IfwComponentType.BROADCAST
    private val filterName = "$pkg/$comp"

    // ── UI State → IFW ─────────────────────────────────────

    @Test
    fun givenBlockAllMode_whenToIfwFilter_thenReturnsComponentFilter() {
        val state = baseState().copy(blockMode = BlockMode.ALL)
        val filter = state.toIfwFilter()
        assertEquals(IfwFilter.ComponentFilter(filterName), filter)
    }

    @Test
    fun givenSingleCondition_whenToIfwFilter_thenReturnsAndWithComponentFilter() {
        val state = baseState().copy(
            blockMode = BlockMode.CONDITIONAL,
            conditions = listOf(
                ConditionUiState.ActionFilter(
                    id = "1",
                    matchMode = MatchMode.EXACT,
                    value = "android.intent.action.BOOT_COMPLETED",
                ),
            ),
        )
        val filter = state.toIfwFilter()
        val expected = IfwFilter.And(
            listOf(
                IfwFilter.ComponentFilter(filterName),
                IfwFilter.Action(StringMatcher.Equals("android.intent.action.BOOT_COMPLETED")),
            ),
        )
        assertEquals(expected, filter)
    }

    @Test
    fun givenMultipleConditionsAllMatch_whenToIfwFilter_thenReturnsAndWithAll() {
        val state = baseState().copy(
            blockMode = BlockMode.CONDITIONAL,
            combineMode = CombineMode.ALL_MATCH,
            conditions = listOf(
                ConditionUiState.ActionFilter(
                    id = "1",
                    matchMode = MatchMode.EXACT,
                    value = "android.intent.action.BOOT_COMPLETED",
                ),
                ConditionUiState.SourceControl(id = "2", option = SourceOption.ALLOW_SYSTEM_ONLY),
            ),
        )
        val filter = state.toIfwFilter()
        val expected = IfwFilter.And(
            listOf(
                IfwFilter.ComponentFilter(filterName),
                IfwFilter.Action(StringMatcher.Equals("android.intent.action.BOOT_COMPLETED")),
                IfwFilter.Not(IfwFilter.Sender(SenderType.SYSTEM)),
            ),
        )
        assertEquals(expected, filter)
    }

    @Test
    fun givenMultipleConditionsAnyMatch_whenToIfwFilter_thenReturnsAndWithOr() {
        val state = baseState().copy(
            blockMode = BlockMode.CONDITIONAL,
            combineMode = CombineMode.ANY_MATCH,
            conditions = listOf(
                ConditionUiState.ActionFilter(id = "1", value = "ACTION_A"),
                ConditionUiState.ActionFilter(id = "2", value = "ACTION_B"),
            ),
        )
        val filter = state.toIfwFilter()
        val expected = IfwFilter.And(
            listOf(
                IfwFilter.ComponentFilter(filterName),
                IfwFilter.Or(
                    listOf(
                        IfwFilter.Action(StringMatcher.Equals("ACTION_A")),
                        IfwFilter.Action(StringMatcher.Equals("ACTION_B")),
                    ),
                ),
            ),
        )
        assertEquals(expected, filter)
    }

    @Test
    fun givenSourceControlAllowSystem_whenToIfwFilter_thenReturnsNotSenderSystem() {
        val state = baseState().copy(
            blockMode = BlockMode.CONDITIONAL,
            conditions = listOf(
                ConditionUiState.SourceControl(id = "1", option = SourceOption.ALLOW_SYSTEM_ONLY),
            ),
        )
        val filter = state.toIfwFilter()
        assertTrue(filter is IfwFilter.And)
        val notSender = (filter as IfwFilter.And).filters[1]
        assertEquals(IfwFilter.Not(IfwFilter.Sender(SenderType.SYSTEM)), notSender)
    }

    @Test
    fun givenSourceControlBlockSystem_whenToIfwFilter_thenReturnsSenderSystemWithoutNot() {
        val state = baseState().copy(
            blockMode = BlockMode.CONDITIONAL,
            conditions = listOf(
                ConditionUiState.SourceControl(id = "1", option = SourceOption.BLOCK_SYSTEM),
            ),
        )
        val filter = state.toIfwFilter()
        assertTrue(filter is IfwFilter.And)
        val sender = (filter as IfwFilter.And).filters[1]
        assertEquals(IfwFilter.Sender(SenderType.SYSTEM), sender)
    }

    @Test
    fun givenCallerPermissionRequire_whenToIfwFilter_thenReturnsNotSenderPermission() {
        val state = baseState().copy(
            blockMode = BlockMode.CONDITIONAL,
            conditions = listOf(
                ConditionUiState.CallerPermission(
                    id = "1",
                    permission = "android.permission.INTERNET",
                    mode = PermissionMode.REQUIRE,
                ),
            ),
        )
        val filter = state.toIfwFilter()
        assertTrue(filter is IfwFilter.And)
        val perm = (filter as IfwFilter.And).filters[1]
        assertEquals(
            IfwFilter.Not(IfwFilter.SenderPermission("android.permission.INTERNET")),
            perm,
        )
    }

    @Test
    fun givenLinkFilter_whenToIfwFilter_thenReturnsMultipleFiltersAnded() {
        val state = baseState().copy(
            blockMode = BlockMode.CONDITIONAL,
            conditions = listOf(
                ConditionUiState.LinkFilter(
                    id = "1",
                    host = "tracking.example.com",
                    path = "/api/track",
                    pathMatchMode = MatchMode.STARTS_WITH,
                    scheme = "https",
                ),
            ),
        )
        val filter = state.toIfwFilter()
        assertTrue(filter is IfwFilter.And)
        val children = (filter as IfwFilter.And).filters
        // component-filter + host + path + scheme = 4
        assertEquals(4, children.size)
        assertTrue(children.any { it is IfwFilter.Host })
        assertTrue(children.any { it is IfwFilter.Path })
        assertTrue(children.any { it is IfwFilter.Scheme })
    }

    // ── IFW → UI State ─────────────────────────────────────

    @Test
    fun givenDirectComponentFilter_whenFromIfwRules_thenReturnsBlockAllMode() {
        val rules = IfwRules(
            listOf(
                IfwRule(
                    componentType = componentType,
                    filters = listOf(IfwFilter.ComponentFilter(filterName)),
                ),
            ),
        )
        val state = rules.toEditorState(componentType, pkg, comp)
        assertEquals(BlockMode.ALL, state.blockMode)
        assertTrue(state.conditions.isEmpty())
    }

    @Test
    fun givenAndWithConditions_whenFromIfwRules_thenReturnsConditionalAllMatch() {
        val rules = IfwRules(
            listOf(
                IfwRule(
                    componentType = componentType,
                    filters = listOf(
                        IfwFilter.And(
                            listOf(
                                IfwFilter.ComponentFilter(filterName),
                                IfwFilter.Action(StringMatcher.Equals("BOOT_COMPLETED")),
                            ),
                        ),
                    ),
                ),
            ),
        )
        val state = rules.toEditorState(componentType, pkg, comp)
        assertEquals(BlockMode.CONDITIONAL, state.blockMode)
        assertEquals(CombineMode.ALL_MATCH, state.combineMode)
        assertEquals(1, state.conditions.size)
        assertTrue(state.conditions[0] is ConditionUiState.ActionFilter)
    }

    @Test
    fun givenAndWithOr_whenFromIfwRules_thenReturnsConditionalAnyMatch() {
        val rules = IfwRules(
            listOf(
                IfwRule(
                    componentType = componentType,
                    filters = listOf(
                        IfwFilter.And(
                            listOf(
                                IfwFilter.ComponentFilter(filterName),
                                IfwFilter.Or(
                                    listOf(
                                        IfwFilter.Action(StringMatcher.Equals("A")),
                                        IfwFilter.Action(StringMatcher.Equals("B")),
                                    ),
                                ),
                            ),
                        ),
                    ),
                ),
            ),
        )
        val state = rules.toEditorState(componentType, pkg, comp)
        assertEquals(BlockMode.CONDITIONAL, state.blockMode)
        assertEquals(CombineMode.ANY_MATCH, state.combineMode)
        assertEquals(2, state.conditions.size)
    }

    @Test
    fun givenNotSenderSystem_whenFromIfwRules_thenReturnsSourceControlAllowSystem() {
        val rules = IfwRules(
            listOf(
                IfwRule(
                    componentType = componentType,
                    filters = listOf(
                        IfwFilter.And(
                            listOf(
                                IfwFilter.ComponentFilter(filterName),
                                IfwFilter.Not(IfwFilter.Sender(SenderType.SYSTEM)),
                            ),
                        ),
                    ),
                ),
            ),
        )
        val state = rules.toEditorState(componentType, pkg, comp)
        val source = state.conditions[0] as ConditionUiState.SourceControl
        assertEquals(SourceOption.ALLOW_SYSTEM_ONLY, source.option)
    }

    @Test
    fun givenNoRulesForComponent_whenFromIfwRules_thenReturnsNull() {
        val rules = IfwRules.empty()
        val state = rules.toEditorStateOrNull(componentType, pkg, comp)
        assertEquals(null, state)
    }

    @Test
    fun givenUnrecognizableStructure_whenFromIfwRules_thenReturnsAdvancedRule() {
        val rules = IfwRules(
            listOf(
                IfwRule(
                    componentType = componentType,
                    filters = listOf(
                        IfwFilter.Or(
                            listOf(
                                IfwFilter.ComponentFilter(filterName),
                                IfwFilter.Action(StringMatcher.Equals("A")),
                            ),
                        ),
                    ),
                ),
            ),
        )
        val state = rules.toEditorState(componentType, pkg, comp)
        assertTrue(state.isAdvancedRule)
    }

    // ── Merge Strategy ──────────────────────────────────────

    @Test
    fun givenExistingRulesForOtherComponents_whenMerge_thenOtherComponentsPreserved() {
        val otherFilter = IfwFilter.ComponentFilter("$pkg/com.example.OtherReceiver")
        val existingRules = IfwRules(
            listOf(
                IfwRule(
                    componentType = componentType,
                    filters = listOf(otherFilter, IfwFilter.ComponentFilter(filterName)),
                ),
            ),
        )
        val newFilter = IfwFilter.And(
            listOf(
                IfwFilter.ComponentFilter(filterName),
                IfwFilter.Action(StringMatcher.Equals("NEW")),
            ),
        )
        val merged = existingRules.mergeComponentRule(
            componentType = componentType,
            componentName = filterName,
            newFilter = newFilter,
            block = true,
            log = true,
        )
        // Other component should still be in the rules
        val allFilters = merged.rules.flatMap { it.filters }
        assertTrue(allFilters.contains(otherFilter))
        assertTrue(allFilters.contains(newFilter))
    }

    @Test
    fun givenNullNewFilter_whenMerge_thenComponentRuleRemoved() {
        val rules = IfwRules(
            listOf(
                IfwRule(
                    componentType = componentType,
                    filters = listOf(IfwFilter.ComponentFilter(filterName)),
                ),
            ),
        )
        val merged = rules.mergeComponentRule(
            componentType = componentType,
            componentName = filterName,
            newFilter = null,
            block = true,
            log = true,
        )
        assertTrue(merged.isEmpty())
    }

    @Test
    fun givenBlockAndLogSettings_whenMerge_thenNewRuleHasCorrectSettings() {
        val newFilter = IfwFilter.ComponentFilter(filterName)
        val merged = IfwRules.empty().mergeComponentRule(
            componentType = componentType,
            componentName = filterName,
            newFilter = newFilter,
            block = false,
            log = true,
        )
        val rule = merged.rules.first()
        assertFalse(rule.block)
        assertTrue(rule.log)
    }

    // ── Round-trip ──────────────────────────────────────────

    @Test
    fun givenEditorState_whenConvertToIfwAndBack_thenStatePreserved() {
        val original = baseState().copy(
            blockMode = BlockMode.CONDITIONAL,
            combineMode = CombineMode.ALL_MATCH,
            conditions = listOf(
                ConditionUiState.ActionFilter(
                    id = "1",
                    matchMode = MatchMode.EXACT,
                    value = "android.intent.action.BOOT_COMPLETED",
                ),
                ConditionUiState.SourceControl(id = "2", option = SourceOption.ALLOW_SYSTEM_ONLY),
            ),
        )
        val filter = original.toIfwFilter()
        val rules = IfwRules(
            listOf(IfwRule(componentType = componentType, filters = listOf(filter))),
        )
        val restored = rules.toEditorState(componentType, pkg, comp)
        assertEquals(original.blockMode, restored.blockMode)
        assertEquals(original.combineMode, restored.combineMode)
        assertEquals(original.conditions.size, restored.conditions.size)
    }

    private fun baseState() = RuleEditorUiState(
        packageName = pkg,
        componentName = comp,
        componentType = componentType,
    )
}
