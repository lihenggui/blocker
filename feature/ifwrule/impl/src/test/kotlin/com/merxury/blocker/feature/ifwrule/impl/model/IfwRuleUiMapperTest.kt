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

import com.merxury.core.ifw.editor.IfwEditorConditionKind
import com.merxury.core.ifw.editor.IfwEditorGroupMode
import com.merxury.core.ifw.editor.IfwEditorNode
import com.merxury.core.ifw.editor.IfwEditorStringMatcherMode
import com.merxury.core.ifw.model.IfwComponentType
import com.merxury.core.ifw.model.IfwFilter
import com.merxury.core.ifw.model.IfwRule
import com.merxury.core.ifw.model.IfwRules
import com.merxury.core.ifw.model.SenderType
import com.merxury.core.ifw.model.StringMatcher
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class IfwRuleUiMapperTest {

    private val packageName = "com.example"
    private val componentName = "com.example.BootReceiver"
    private val componentType = IfwComponentType.BROADCAST
    private val filterName = "$packageName/$componentName"

    @Test
    fun givenBlockAll_whenToIfwFilter_thenReturnsDirectComponentFilter() {
        val filter = baseState().copy(blockMode = BlockMode.ALL).toIfwFilter()

        assertEquals(IfwFilter.ComponentFilter(filterName), filter)
    }

    @Test
    fun givenNestedConditions_whenToIfwFilter_thenPreservesTreeStructure() {
        val state = baseState().copy(
            blockMode = BlockMode.CONDITIONAL,
            rootGroup = IfwEditorNode.Group(
                mode = IfwEditorGroupMode.ALL,
                children = listOf(
                    IfwEditorNode.Condition(
                        kind = IfwEditorConditionKind.ACTION,
                        matcherMode = IfwEditorStringMatcherMode.EXACT,
                        value = "android.intent.action.BOOT_COMPLETED",
                    ),
                    IfwEditorNode.Group(
                        mode = IfwEditorGroupMode.ANY,
                        children = listOf(
                            IfwEditorNode.Condition(
                                kind = IfwEditorConditionKind.CALLER_TYPE,
                                senderType = SenderType.SYSTEM,
                                excluded = true,
                            ),
                            IfwEditorNode.Condition(
                                kind = IfwEditorConditionKind.HOST,
                                matcherMode = IfwEditorStringMatcherMode.CONTAINS,
                                value = "example.com",
                            ),
                        ),
                    ),
                ),
            ),
        )

        val filter = state.toIfwFilter()

        assertEquals(
            IfwFilter.And(
                listOf(
                    IfwFilter.ComponentFilter(filterName),
                    IfwFilter.And(
                        listOf(
                            IfwFilter.Action(StringMatcher.Equals("android.intent.action.BOOT_COMPLETED")),
                            IfwFilter.Or(
                                listOf(
                                    IfwFilter.Not(IfwFilter.Sender(SenderType.SYSTEM)),
                                    IfwFilter.Host(StringMatcher.Contains("example.com")),
                                ),
                            ),
                        ),
                    ),
                ),
            ),
            filter,
        )
    }

    @Test
    fun givenConditionalRule_whenFromIfwRules_thenRestoresNestedRootGroup() {
        val rules = IfwRules(
            listOf(
                IfwRule(
                    componentType = componentType,
                    filters = listOf(
                        IfwFilter.And(
                            listOf(
                                IfwFilter.ComponentFilter(filterName),
                                IfwFilter.Action(StringMatcher.Equals("android.intent.action.BOOT_COMPLETED")),
                                IfwFilter.Or(
                                    listOf(
                                        IfwFilter.Not(IfwFilter.Sender(SenderType.SYSTEM)),
                                        IfwFilter.Path(StringMatcher.StartsWith("/safe")),
                                    ),
                                ),
                            ),
                        ),
                    ),
                ),
            ),
        )

        val state = rules.toEditorState(componentType, packageName, componentName)

        assertEquals(BlockMode.CONDITIONAL, state.blockMode)
        assertEquals(IfwEditorGroupMode.ALL, state.rootGroup.mode)
        assertEquals(2, state.rootGroup.children.size)
        assertIs<IfwEditorNode.Condition>(state.rootGroup.children.first())
        val nestedGroup = assertIs<IfwEditorNode.Group>(state.rootGroup.children.last())
        assertEquals(IfwEditorGroupMode.ANY, nestedGroup.mode)
        assertEquals(2, nestedGroup.children.size)
        val negatedCaller = assertIs<IfwEditorNode.Condition>(nestedGroup.children.first())
        assertEquals(IfwEditorConditionKind.CALLER_TYPE, negatedCaller.kind)
        assertTrue(negatedCaller.excluded)
    }

    @Test
    fun givenTopLevelComponentAndFilters_whenFromIfwRules_thenRestoresConditionalState() {
        val rules = IfwRules(
            listOf(
                IfwRule(
                    componentType = componentType,
                    filters = listOf(
                        IfwFilter.ComponentFilter(filterName),
                        IfwFilter.Scheme(StringMatcher.Equals("https")),
                        IfwFilter.Not(IfwFilter.MimeType(StringMatcher.Equals("text/plain"))),
                    ),
                ),
            ),
        )

        val state = rules.toEditorState(componentType, packageName, componentName)

        assertEquals(BlockMode.CONDITIONAL, state.blockMode)
        assertEquals(IfwEditorGroupMode.ANY, state.rootGroup.mode)
        assertEquals(2, state.rootGroup.children.size)
        val scheme = assertIs<IfwEditorNode.Condition>(state.rootGroup.children.first())
        assertEquals(IfwEditorConditionKind.SCHEME, scheme.kind)
        val mime = assertIs<IfwEditorNode.Condition>(state.rootGroup.children.last())
        assertEquals(IfwEditorConditionKind.MIME_TYPE, mime.kind)
        assertTrue(mime.excluded)
    }

    @Test
    fun givenUnsupportedPlacementOfComponentFilter_whenFromIfwRules_thenMarksAdvancedRule() {
        val rules = IfwRules(
            listOf(
                IfwRule(
                    componentType = componentType,
                    filters = listOf(
                        IfwFilter.Or(
                            listOf(
                                IfwFilter.ComponentFilter(filterName),
                                IfwFilter.Action(StringMatcher.Equals("ACTION_A")),
                            ),
                        ),
                    ),
                ),
            ),
        )

        val state = rules.toEditorState(componentType, packageName, componentName)

        assertTrue(state.isAdvancedRule)
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
        val merged = IfwRules.empty().mergeComponentRule(
            componentType = componentType,
            componentName = filterName,
            newFilter = IfwFilter.ComponentFilter(filterName),
            block = false,
            log = true,
        )

        val rule = merged.rules.first()
        assertFalse(rule.block)
        assertTrue(rule.log)
    }

    private fun baseState() = RuleEditorUiState(
        packageName = packageName,
        componentName = componentName,
        componentType = componentType,
    )
}
