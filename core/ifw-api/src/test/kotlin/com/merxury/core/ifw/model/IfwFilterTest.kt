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
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals

class IfwFilterTest {

    // ── Composite filters ──

    @Test
    fun givenChildFilters_whenCreatingAndFilter_thenHoldsAllChildren() {
        val children = listOf(
            IfwFilter.Action(StringMatcher.Equals("a")),
            IfwFilter.Action(StringMatcher.Equals("b")),
        )
        val filter = IfwFilter.And(children)
        assertEquals(2, filter.filters.size)
    }

    @Test
    fun givenChildFilters_whenCreatingOrFilter_thenHoldsAllChildren() {
        val children = listOf(
            IfwFilter.Scheme(StringMatcher.Equals("http")),
            IfwFilter.Scheme(StringMatcher.Equals("https")),
        )
        val filter = IfwFilter.Or(children)
        assertEquals(2, filter.filters.size)
    }

    @Test
    fun givenChildFilter_whenCreatingNotFilter_thenHoldsChild() {
        val child = IfwFilter.Sender(SenderType.SYSTEM)
        val filter = IfwFilter.Not(child)
        assertEquals(child, filter.filter)
    }

    @Test
    fun givenNestedComposites_whenCreatingAndWithOrAndNot_thenStructureIsCorrect() {
        val filter = IfwFilter.And(
            listOf(
                IfwFilter.Or(
                    listOf(
                        IfwFilter.Action(StringMatcher.Equals("a")),
                        IfwFilter.Action(StringMatcher.Equals("b")),
                    ),
                ),
                IfwFilter.Not(IfwFilter.Sender(SenderType.SYSTEM)),
            ),
        )
        assertEquals(2, filter.filters.size)
    }

    // ── String-based filters ──

    @Test
    fun givenActionFilter_whenAccessingMatcher_thenReturnsCorrectStringMatcher() {
        val filter = IfwFilter.Action(StringMatcher.Equals("android.intent.action.VIEW"))
        assertEquals("android.intent.action.VIEW", filter.matcher.value)
    }

    @Test
    fun givenComponentFilter_whenAccessingMatcher_thenReturnsCorrectStringMatcher() {
        val filter = IfwFilter.Component(StringMatcher.StartsWith("com.example"))
        assertEquals("com.example", filter.matcher.value)
    }

    @Test
    fun givenComponentNameFilter_whenAccessingMatcher_thenReturnsCorrectStringMatcher() {
        val filter = IfwFilter.ComponentName(StringMatcher.Contains("Activity"))
        assertEquals("Activity", filter.matcher.value)
    }

    @Test
    fun givenComponentPackageFilter_whenAccessingMatcher_thenReturnsCorrectStringMatcher() {
        val filter = IfwFilter.ComponentPackage(StringMatcher.Regex("com\\.example\\..*"))
        assertEquals("com\\.example\\..*", filter.matcher.value)
    }

    @Test
    fun givenDataFilter_whenAccessingMatcher_thenReturnsCorrectStringMatcher() {
        val filter = IfwFilter.Data(StringMatcher.IsNull(isNull = true))
        assertEquals("true", filter.matcher.value)
    }

    @Test
    fun givenHostFilter_whenAccessingMatcher_thenReturnsCorrectStringMatcher() {
        val filter = IfwFilter.Host(StringMatcher.Equals("example.com"))
        assertEquals("example.com", filter.matcher.value)
    }

    @Test
    fun givenMimeTypeFilter_whenAccessingMatcher_thenReturnsCorrectStringMatcher() {
        val filter = IfwFilter.MimeType(StringMatcher.Pattern("image/*"))
        assertEquals("image/*", filter.matcher.value)
    }

    @Test
    fun givenSchemeFilter_whenAccessingMatcher_thenReturnsCorrectStringMatcher() {
        val filter = IfwFilter.Scheme(StringMatcher.Equals("content"))
        assertEquals("content", filter.matcher.value)
    }

    @Test
    fun givenSchemeSpecificPartFilter_whenAccessingMatcher_thenReturnsCorrectStringMatcher() {
        val filter = IfwFilter.SchemeSpecificPart(StringMatcher.StartsWith("//example"))
        assertEquals("//example", filter.matcher.value)
    }

    @Test
    fun givenPathFilter_whenAccessingMatcher_thenReturnsCorrectStringMatcher() {
        val filter = IfwFilter.Path(StringMatcher.Regex("/api/v[0-9]+/.*"))
        assertEquals("/api/v[0-9]+/.*", filter.matcher.value)
    }

    // ── Non-string filters ──

    @Test
    fun givenCategoryFilter_whenAccessingName_thenReturnsCorrectName() {
        val filter = IfwFilter.Category("android.intent.category.LAUNCHER")
        assertEquals("android.intent.category.LAUNCHER", filter.name)
    }

    @Test
    fun givenPortWithExactValue_whenAccessingEquals_thenReturnsCorrectPort() {
        val filter = IfwFilter.Port(equals = 8080)
        assertEquals(8080, filter.equals)
    }

    @Test
    fun givenPortWithRange_whenAccessingMinMax_thenReturnsCorrectRange() {
        val filter = IfwFilter.Port(min = 1, max = 1024)
        assertEquals(1, filter.min)
        assertEquals(1024, filter.max)
    }

    @Test
    fun givenPortWithEqualsAndMinMax_whenCreating_thenThrowsIllegalArgumentException() {
        assertFailsWith<IllegalArgumentException> {
            IfwFilter.Port(equals = 80, min = 1, max = 100)
        }
    }

    @Test
    fun givenSenderFilter_whenAccessingType_thenReturnsCorrectSenderType() {
        val filter = IfwFilter.Sender(SenderType.SYSTEM)
        assertEquals(SenderType.SYSTEM, filter.type)
    }

    @Test
    fun givenSenderPackageFilter_whenAccessingName_thenReturnsCorrectName() {
        val filter = IfwFilter.SenderPackage("com.android.systemui")
        assertEquals("com.android.systemui", filter.name)
    }

    @Test
    fun givenSenderPermissionFilter_whenAccessingName_thenReturnsCorrectName() {
        val filter = IfwFilter.SenderPermission("android.permission.INTERNET")
        assertEquals("android.permission.INTERNET", filter.name)
    }

    @Test
    fun givenComponentFilterWithName_whenAccessingName_thenReturnsCorrectName() {
        val filter = IfwFilter.ComponentFilter("com.example/com.example.MainActivity")
        assertEquals("com.example/com.example.MainActivity", filter.name)
    }

    // ── SenderType ──

    @Test
    fun givenValidXmlValues_whenParsingSenderType_thenReturnsCorrectTypes() {
        assertEquals(SenderType.SIGNATURE, SenderType.fromXmlValue("signature"))
        assertEquals(SenderType.SYSTEM, SenderType.fromXmlValue("system"))
        assertEquals(SenderType.SYSTEM_OR_SIGNATURE, SenderType.fromXmlValue("system|signature"))
        assertEquals(SenderType.USER_ID, SenderType.fromXmlValue("userId"))
    }

    @Test
    fun givenUnknownXmlValue_whenParsingSenderType_thenThrowsIllegalArgumentException() {
        assertFailsWith<IllegalArgumentException> {
            SenderType.fromXmlValue("unknown")
        }
    }

    // ── Equality ──

    @Test
    fun givenDifferentFilterTypes_whenComparing_thenTheyAreNotEqual() {
        val action = IfwFilter.Action(StringMatcher.Equals("test"))
        val scheme = IfwFilter.Scheme(StringMatcher.Equals("test"))
        assertNotEquals<IfwFilter>(action, scheme)
    }
}
