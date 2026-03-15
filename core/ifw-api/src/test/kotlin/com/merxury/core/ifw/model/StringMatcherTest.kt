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
import kotlin.test.assertNotEquals

class StringMatcherTest {

    @Test
    fun givenEqualsMatcherWithValue_whenAccessingValue_thenReturnsCorrectValue() {
        val matcher = StringMatcher.Equals("android.intent.action.VIEW")
        assertEquals("android.intent.action.VIEW", matcher.value)
    }

    @Test
    fun givenStartsWithMatcherWithValue_whenAccessingValue_thenReturnsCorrectValue() {
        val matcher = StringMatcher.StartsWith("com.example")
        assertEquals("com.example", matcher.value)
    }

    @Test
    fun givenContainsMatcherWithValue_whenAccessingValue_thenReturnsCorrectValue() {
        val matcher = StringMatcher.Contains("BOOT")
        assertEquals("BOOT", matcher.value)
    }

    @Test
    fun givenPatternMatcherWithValue_whenAccessingValue_thenReturnsCorrectValue() {
        val matcher = StringMatcher.Pattern("/api/*")
        assertEquals("/api/*", matcher.value)
    }

    @Test
    fun givenRegexMatcherWithValue_whenAccessingValue_thenReturnsCorrectValue() {
        val matcher = StringMatcher.Regex("android\\.intent\\.action\\..*")
        assertEquals("android\\.intent\\.action\\..*", matcher.value)
    }

    @Test
    fun givenIsNullMatcherWithTrue_whenAccessingValue_thenReturnsTrueString() {
        val matcher = StringMatcher.IsNull(isNull = true)
        assertEquals("true", matcher.value)
    }

    @Test
    fun givenIsNullMatcherWithFalse_whenAccessingValue_thenReturnsFalseString() {
        val matcher = StringMatcher.IsNull(isNull = false)
        assertEquals("false", matcher.value)
    }

    @Test
    fun givenDifferentMatcherTypes_whenComparing_thenTheyAreNotEqual() {
        val equals = StringMatcher.Equals("test")
        val contains = StringMatcher.Contains("test")
        assertNotEquals<StringMatcher>(equals, contains)
    }

    @Test
    fun givenSameMatcherTypeAndValue_whenComparing_thenTheyAreEqual() {
        val a = StringMatcher.Equals("test")
        val b = StringMatcher.Equals("test")
        assertEquals(a, b)
    }
}
