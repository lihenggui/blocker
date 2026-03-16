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
                    hostMatchMode = MatchMode.CONTAINS,
                    path = "/api/track",
                    pathMatchMode = MatchMode.STARTS_WITH,
                    scheme = "https",
                    schemeMatchMode = MatchMode.REGEX,
                    schemeSpecificPart = "//tracking.example.com/api",
                    schemeSpecificPartMatchMode = MatchMode.PATTERN,
                ),
            ),
        )
        val filter = state.toIfwFilter()
        assertTrue(filter is IfwFilter.And)
        val children = (filter as IfwFilter.And).filters
        assertEquals(5, children.size)
        assertTrue(children.contains(IfwFilter.Host(StringMatcher.Contains("tracking.example.com"))))
        assertTrue(children.contains(IfwFilter.Path(StringMatcher.StartsWith("/api/track"))))
        assertTrue(children.contains(IfwFilter.Scheme(StringMatcher.Regex("https"))))
        assertTrue(children.contains(IfwFilter.SchemeSpecificPart(StringMatcher.Pattern("//tracking.example.com/api"))))
    }

    @Test
    fun givenIsNotNullMatchMode_whenToIfwFilter_thenUsesFalseIsNullMatcher() {
        val state = baseState().copy(
            blockMode = BlockMode.CONDITIONAL,
            conditions = listOf(
                ConditionUiState.DataFilter(
                    id = "1",
                    matchMode = MatchMode.IS_NOT_NULL,
                ),
            ),
        )
        val filter = state.toIfwFilter()
        assertTrue(filter is IfwFilter.And)
        assertTrue((filter as IfwFilter.And).filters.contains(IfwFilter.Data(StringMatcher.IsNull(false))))
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
    fun givenSenderUserId_whenFromIfwRules_thenReturnsSourceControlBlockUserId() {
        val rules = IfwRules(
            listOf(
                IfwRule(
                    componentType = componentType,
                    filters = listOf(
                        IfwFilter.And(
                            listOf(
                                IfwFilter.ComponentFilter(filterName),
                                IfwFilter.Sender(SenderType.USER_ID),
                            ),
                        ),
                    ),
                ),
            ),
        )
        val state = rules.toEditorState(componentType, pkg, comp)
        val source = state.conditions[0] as ConditionUiState.SourceControl
        assertEquals(SourceOption.BLOCK_USER_ID, source.option)
    }

    @Test
    fun givenSchemeSpecificPartMatcher_whenFromIfwRules_thenRestoresLinkCondition() {
        val rules = IfwRules(
            listOf(
                IfwRule(
                    componentType = componentType,
                    filters = listOf(
                        IfwFilter.And(
                            listOf(
                                IfwFilter.ComponentFilter(filterName),
                                IfwFilter.Scheme(StringMatcher.StartsWith("https")),
                                IfwFilter.Host(StringMatcher.Contains("example.com")),
                                IfwFilter.SchemeSpecificPart(StringMatcher.Regex("//example.com/.*")),
                            ),
                        ),
                    ),
                ),
            ),
        )
        val state = rules.toEditorState(componentType, pkg, comp)
        val link = state.conditions[0] as ConditionUiState.LinkFilter
        assertEquals("https", link.scheme)
        assertEquals(MatchMode.STARTS_WITH, link.schemeMatchMode)
        assertEquals("example.com", link.host)
        assertEquals(MatchMode.CONTAINS, link.hostMatchMode)
        assertEquals("//example.com/.*", link.schemeSpecificPart)
        assertEquals(MatchMode.REGEX, link.schemeSpecificPartMatchMode)
    }

    @Test
    fun givenFalseIsNullMatcher_whenFromIfwRules_thenRestoresIsNotNullMatchMode() {
        val rules = IfwRules(
            listOf(
                IfwRule(
                    componentType = componentType,
                    filters = listOf(
                        IfwFilter.And(
                            listOf(
                                IfwFilter.ComponentFilter(filterName),
                                IfwFilter.Data(StringMatcher.IsNull(false)),
                            ),
                        ),
                    ),
                ),
            ),
        )
        val state = rules.toEditorState(componentType, pkg, comp)
        val dataFilter = state.conditions[0] as ConditionUiState.DataFilter
        assertEquals(MatchMode.IS_NOT_NULL, dataFilter.matchMode)
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

    @Test
    fun givenLinkFilterAndUnsupportedCondition_whenFromIfwRules_thenReturnsAdvancedRule() {
        val rules = IfwRules(
            listOf(
                IfwRule(
                    componentType = componentType,
                    filters = listOf(
                        IfwFilter.And(
                            listOf(
                                IfwFilter.ComponentFilter(filterName),
                                IfwFilter.Host(StringMatcher.Equals("tracking.example.com")),
                                IfwFilter.Not(IfwFilter.Action(StringMatcher.Equals("A"))),
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
