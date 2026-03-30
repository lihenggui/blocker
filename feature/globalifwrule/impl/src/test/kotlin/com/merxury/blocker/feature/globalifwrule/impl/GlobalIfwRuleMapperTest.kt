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

package com.merxury.blocker.feature.globalifwrule.impl

import com.merxury.blocker.core.model.data.AdvancedGlobalIfwRuleDraft
import com.merxury.blocker.core.model.data.GlobalIfwRuleEditMode
import com.merxury.blocker.core.model.data.SimpleGlobalIfwRuleDraft
import com.merxury.blocker.core.model.data.SimpleTargetMode
import com.merxury.core.ifw.editor.IfwEditorConditionKind
import com.merxury.core.ifw.editor.IfwEditorNode
import com.merxury.core.ifw.editor.IfwEditorStringMatcherMode
import com.merxury.core.ifw.model.IfwComponentType
import com.merxury.core.ifw.model.IfwFilter
import com.merxury.core.ifw.model.IfwIntentFilter
import com.merxury.core.ifw.model.IfwRule
import com.merxury.core.ifw.model.StringMatcher
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GlobalIfwRuleMapperTest {

    @Test
    fun givenSimpleDraft_whenMappingToRule_thenCreatesStableSimpleFilters() {
        val draft = SimpleGlobalIfwRuleDraft(
            selectedPackageName = "com.example",
            componentType = IfwComponentType.BROADCAST,
            targetMode = SimpleTargetMode.MULTIPLE,
            targets = listOf(
                "com.example/com.example.BootReceiver",
                "com.example/com.example.AlarmReceiver",
            ),
            action = "android.intent.action.BOOT_COMPLETED",
            category = "android.intent.category.DEFAULT",
            callerPackage = "android",
        )

        val rule = draft.toIfwRule()

        assertEquals(5, rule.filters.size)
        assertEquals(
            IfwFilter.ComponentFilter("com.example/com.example.AlarmReceiver"),
            rule.filters[0],
        )
        assertEquals(
            IfwFilter.ComponentFilter("com.example/com.example.BootReceiver"),
            rule.filters[1],
        )
        assertEquals(
            IfwFilter.Action(StringMatcher.Equals("android.intent.action.BOOT_COMPLETED")),
            rule.filters[2],
        )
        assertEquals(
            IfwFilter.Category("android.intent.category.DEFAULT"),
            rule.filters[3],
        )
        assertEquals(
            IfwFilter.SenderPackage("android"),
            rule.filters[4],
        )
    }

    @Test
    fun givenSimpleRule_whenParsing_thenReturnsEditableSimpleDraft() {
        val rule = IfwRule(
            componentType = IfwComponentType.SERVICE,
            block = true,
            log = false,
            filters = listOf(
                IfwFilter.ComponentFilter("com.example/com.example.SyncService"),
                IfwFilter.Action(StringMatcher.Equals("sync")),
                IfwFilter.SenderPackage("android"),
            ),
        )

        val draft = rule.toSimpleDraftOrNull(
            storagePackageName = "com.example",
            editingRuleIndex = 2,
        )

        assertNotNull(draft)
        assertEquals("com.example", draft.selectedPackageName)
        assertEquals(SimpleTargetMode.SINGLE, draft.targetMode)
        assertEquals(listOf("com.example/com.example.SyncService"), draft.targets)
        assertEquals("sync", draft.action)
        assertEquals("android", draft.callerPackage)
        assertEquals(2, draft.editingRuleIndex)
    }

    @Test
    fun givenCompositeRule_whenParsingSimpleDraft_thenReturnsNull() {
        val rule = IfwRule(
            componentType = IfwComponentType.BROADCAST,
            filters = listOf(
                IfwFilter.And(
                    listOf(
                        IfwFilter.ComponentFilter("com.example/com.example.BootReceiver"),
                        IfwFilter.Action(StringMatcher.Equals("android.intent.action.BOOT_COMPLETED")),
                    ),
                ),
            ),
        )

        val draft = rule.toSimpleDraftOrNull(storagePackageName = "com.example")

        assertNull(draft)
    }

    @Test
    fun givenIntentFilterAdvancedDraft_whenSaving_thenPreservesIntentFiltersWithoutTargetComponent() {
        val draft = AdvancedGlobalIfwRuleDraft(
            storagePackageName = "com.example",
            componentType = IfwComponentType.ACTIVITY,
            intentFilters = listOf(
                IfwIntentFilter(actions = listOf("android.intent.action.VIEW")),
            ),
            rootGroup = IfwEditorNode.Group(
                children = listOf(
                    IfwEditorNode.Condition(
                        kind = IfwEditorConditionKind.ACTION,
                        matcherMode = IfwEditorStringMatcherMode.EXACT,
                        value = "android.intent.action.SEND",
                    ),
                ),
            ),
        )

        val rule = draft.toIfwRuleOrNull()

        assertNotNull(rule)
        assertEquals(1, rule.intentFilters.size)
        assertEquals(1, rule.filters.size)
        assertIs<IfwFilter.Action>(rule.filters.single())
    }

    @Test
    fun givenUnsupportedRule_whenBuildingListItem_thenMarksRuleAsAdvanced() {
        val rule = IfwRule(
            componentType = IfwComponentType.BROADCAST,
            filters = listOf(
                IfwFilter.Or(
                    listOf(
                        IfwFilter.ComponentFilter("com.example/com.example.BootReceiver"),
                        IfwFilter.ComponentFilter("com.example/com.example.AlarmReceiver"),
                    ),
                ),
            ),
        )

        val item = rule.toRuleItemUiState(
            index = 0,
            storagePackageName = "com.example",
        )

        assertEquals(GlobalIfwRuleEditMode.ADVANCED, item.editMode)
        assertNull(item.simpleDraft)
        assertTrue(item.filtersSummary.contains("OR"))
    }
}
