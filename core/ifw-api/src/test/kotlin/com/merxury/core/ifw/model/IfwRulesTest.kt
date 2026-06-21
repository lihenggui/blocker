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

package com.merxury.core.ifw.model

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class IfwRulesTest {

    @Test
    fun givenEmptyRules_whenCheckingIsEmpty_thenReturnsTrue() {
        val rules = IfwRules.empty()
        assertTrue(rules.isEmpty())
        assertTrue(rules.rules.isEmpty())
    }

    @Test
    fun givenRulesWithMultipleComponentTypes_whenFilteringByType_thenReturnsCorrectRules() {
        val activityRule = IfwRule(
            componentType = IfwComponentType.ACTIVITY,
            filters = listOf(IfwFilter.ComponentFilter("com.example/com.example.Main")),
        )
        val broadcastRule = IfwRule(
            componentType = IfwComponentType.BROADCAST,
            filters = listOf(IfwFilter.Action(StringMatcher.Equals("BOOT"))),
        )
        val rules = IfwRules(listOf(activityRule, broadcastRule))

        assertEquals(1, rules.rulesFor(IfwComponentType.ACTIVITY).size)
        assertEquals(1, rules.rulesFor(IfwComponentType.BROADCAST).size)
        assertEquals(0, rules.rulesFor(IfwComponentType.SERVICE).size)
    }

    @Test
    fun givenRulesWithComponentFilters_whenExtractingComponentFilters_thenReturnsCorrectNames() {
        val rule = IfwRule(
            componentType = IfwComponentType.BROADCAST,
            filters = listOf(
                IfwFilter.ComponentFilter("com.example/com.example.Receiver1"),
                IfwFilter.ComponentFilter("com.example/com.example.Receiver2"),
                IfwFilter.Action(StringMatcher.Equals("BOOT")),
            ),
        )
        val rules = IfwRules(listOf(rule))

        val names = rules.componentFiltersFor(IfwComponentType.BROADCAST)
        assertEquals(
            setOf("com.example/com.example.Receiver1", "com.example/com.example.Receiver2"),
            names,
        )
    }

    @Test
    fun givenRulesWithFilters_whenCheckingIsEmpty_thenReturnsFalse() {
        val rule = IfwRule(
            componentType = IfwComponentType.SERVICE,
            filters = listOf(IfwFilter.ComponentFilter("a/b")),
        )
        assertFalse(IfwRules(listOf(rule)).isEmpty())
    }

    @Test
    fun givenRulesWithEmptyFilters_whenCheckingIsEmpty_thenReturnsTrue() {
        val rule = IfwRule(componentType = IfwComponentType.ACTIVITY, filters = emptyList())
        assertTrue(IfwRules(listOf(rule)).isEmpty())
    }
}
