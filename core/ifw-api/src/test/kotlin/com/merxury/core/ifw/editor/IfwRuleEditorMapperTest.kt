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

package com.merxury.core.ifw.editor

import com.merxury.core.ifw.model.IfwFilter
import com.merxury.core.ifw.model.StringMatcher
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class IfwRuleEditorMapperTest {

    @Test
    fun givenRootAllGroup_whenConvertingToTopLevelFilters_thenKeepsDirectChildrenFlat() {
        val rootGroup = IfwEditorNode.Group(
            mode = IfwEditorGroupMode.ALL,
            children = listOf(
                IfwEditorNode.Condition(
                    kind = IfwEditorConditionKind.COMPONENT_FILTER,
                    value = "com.example/.Receiver",
                ),
                IfwEditorNode.Condition(
                    kind = IfwEditorConditionKind.ACTION,
                    matcherMode = IfwEditorStringMatcherMode.EXACT,
                    value = "android.intent.action.BOOT_COMPLETED",
                ),
            ),
        )

        assertEquals(
            listOf(
                IfwFilter.ComponentFilter("com.example/.Receiver"),
                IfwFilter.Action(StringMatcher.Equals("android.intent.action.BOOT_COMPLETED")),
            ),
            rootGroup.toTopLevelFilters(),
        )
        assertTrue(rootGroup.hasTopLevelComponentFilter())
    }

    @Test
    fun givenRootAnyGroup_whenCheckingTopLevelSelector_thenNestedComponentFilterDoesNotCount() {
        val rootGroup = IfwEditorNode.Group(
            mode = IfwEditorGroupMode.ANY,
            children = listOf(
                IfwEditorNode.Condition(
                    kind = IfwEditorConditionKind.COMPONENT_FILTER,
                    value = "com.example/.Receiver",
                ),
                IfwEditorNode.Condition(
                    kind = IfwEditorConditionKind.COMPONENT_PACKAGE,
                    matcherMode = IfwEditorStringMatcherMode.PATTERN,
                    value = "com.example.*",
                ),
            ),
        )

        assertEquals(
            listOf(
                IfwFilter.Or(
                    listOf(
                        IfwFilter.ComponentFilter("com.example/.Receiver"),
                        IfwFilter.ComponentPackage(StringMatcher.Pattern("com.example.*")),
                    ),
                ),
            ),
            rootGroup.toTopLevelFilters(),
        )
        assertFalse(rootGroup.hasTopLevelComponentFilter())
    }
}
