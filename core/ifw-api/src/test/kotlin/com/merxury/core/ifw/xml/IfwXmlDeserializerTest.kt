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

package com.merxury.core.ifw.xml

import com.merxury.core.ifw.model.IfwComponentType
import com.merxury.core.ifw.model.IfwFilter
import com.merxury.core.ifw.model.IfwMimeTypeKind
import com.merxury.core.ifw.model.IfwPatternMatcherType
import com.merxury.core.ifw.model.SenderType
import com.merxury.core.ifw.model.StringMatcher
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
class IfwXmlDeserializerTest {

    private lateinit var deserializer: IfwXmlDeserializer

    @Before
    fun setUp() {
        deserializer = IfwXmlDeserializer()
    }

    // ── Basic structure ──

    @Test
    fun givenEmptyRulesXml_whenDeserializing_thenReturnsEmptyModel() {
        val xml = "<rules></rules>"
        val result = deserializer.deserialize(xml)
        assertTrue(result.isEmpty())
    }

    @Test
    fun givenComponentFilterXml_whenDeserializing_thenParsesCorrectly() {
        val xml = """
            <rules>
              <activity block="true" log="false">
                <component-filter name="com.example/com.example.Main" />
              </activity>
            </rules>
        """.trimIndent()
        val result = deserializer.deserialize(xml)
        assertEquals(1, result.rules.size)
        val rule = result.rules[0]
        assertEquals(IfwComponentType.ACTIVITY, rule.componentType)
        assertTrue(rule.block)
        assertEquals(false, rule.log)
        val filter = rule.filters[0]
        assertIs<IfwFilter.ComponentFilter>(filter)
        assertEquals("com.example/com.example.Main", filter.name)
    }

    @Test
    fun givenMultipleComponentTypes_whenDeserializing_thenParsesAllTypes() {
        val xml = """
            <rules>
              <activity block="true" log="false">
                <component-filter name="a/b" />
              </activity>
              <broadcast block="true" log="true">
                <component-filter name="c/d" />
              </broadcast>
              <service block="false" log="false">
                <component-filter name="e/f" />
              </service>
            </rules>
        """.trimIndent()
        val result = deserializer.deserialize(xml)
        assertEquals(3, result.rules.size)
        assertEquals(IfwComponentType.ACTIVITY, result.rules[0].componentType)
        assertEquals(IfwComponentType.BROADCAST, result.rules[1].componentType)
        assertTrue(result.rules[1].log)
        assertEquals(IfwComponentType.SERVICE, result.rules[2].componentType)
        assertEquals(false, result.rules[2].block)
    }

    @Test
    fun givenMissingBlockAndLogAttributes_whenDeserializing_thenUsesDefaultValues() {
        val xml = """
            <rules>
              <broadcast>
                <component-filter name="a/b" />
              </broadcast>
            </rules>
        """.trimIndent()
        val result = deserializer.deserialize(xml)
        assertEquals(false, result.rules[0].block)
        assertEquals(false, result.rules[0].log)
    }

    @Test
    fun givenAospIntentFilterXml_whenDeserializing_thenParsesSelectorSeparately() {
        val xml = """
            <rules>
              <broadcast block="true" log="false">
                <intent-filter>
                  <action name="android.intent.action.VIEW" />
                  <cat name="android.intent.category.DEFAULT" />
                  <staticType name="image/*" />
                  <type name="text/plain" />
                  <scheme name="content" />
                  <ssp prefix="id:" />
                  <auth host="example.com" port="443" />
                  <path literal="/items" />
                </intent-filter>
                <sender-package name="com.example.sender" />
              </broadcast>
            </rules>
        """.trimIndent()

        val rule = deserializer.deserialize(xml).rules.single()

        assertEquals(1, rule.intentFilters.size)
        val intentFilter = rule.intentFilters.single()
        assertEquals(listOf("android.intent.action.VIEW"), intentFilter.actions)
        assertEquals(listOf("android.intent.category.DEFAULT"), intentFilter.categories)
        assertEquals(2, intentFilter.dataTypes.size)
        assertEquals(IfwMimeTypeKind.STATIC, intentFilter.dataTypes[0].kind)
        assertEquals("image/*", intentFilter.dataTypes[0].value)
        assertEquals(IfwMimeTypeKind.DYNAMIC, intentFilter.dataTypes[1].kind)
        assertEquals("text/plain", intentFilter.dataTypes[1].value)
        assertEquals(listOf("content"), intentFilter.schemes)
        assertEquals(IfwPatternMatcherType.PREFIX, intentFilter.schemeSpecificParts.single().type)
        assertEquals("id:", intentFilter.schemeSpecificParts.single().value)
        assertEquals("example.com", intentFilter.authorities.single().host)
        assertEquals("443", intentFilter.authorities.single().port)
        assertEquals(IfwPatternMatcherType.LITERAL, intentFilter.paths.single().type)
        assertEquals("/items", intentFilter.paths.single().value)
        assertEquals(1, rule.filters.size)
        assertIs<IfwFilter.SenderPackage>(rule.filters.single())
    }

    // ── String matcher modes ──

    @Test
    fun givenEqualsAttribute_whenDeserializing_thenCreatesEqualsMatcher() {
        val xml = """
            <rules>
              <broadcast block="true" log="false">
                <action equals="android.intent.action.VIEW" />
              </broadcast>
            </rules>
        """.trimIndent()
        val filter = deserializer.deserialize(xml).rules[0].filters[0]
        assertIs<IfwFilter.Action>(filter)
        assertIs<StringMatcher.Equals>(filter.matcher)
        assertEquals("android.intent.action.VIEW", filter.matcher.value)
    }

    @Test
    fun givenStartsWithAttribute_whenDeserializing_thenCreatesStartsWithMatcher() {
        val xml = """
            <rules>
              <broadcast block="true" log="false">
                <component-package startsWith="com.example" />
              </broadcast>
            </rules>
        """.trimIndent()
        val filter = deserializer.deserialize(xml).rules[0].filters[0]
        assertIs<IfwFilter.ComponentPackage>(filter)
        assertIs<StringMatcher.StartsWith>(filter.matcher)
    }

    @Test
    fun givenContainsAttribute_whenDeserializing_thenCreatesContainsMatcher() {
        val xml = """
            <rules>
              <broadcast block="true" log="false">
                <action contains="BOOT" />
              </broadcast>
            </rules>
        """.trimIndent()
        val filter = deserializer.deserialize(xml).rules[0].filters[0]
        assertIs<IfwFilter.Action>(filter)
        assertIs<StringMatcher.Contains>(filter.matcher)
    }

    @Test
    fun givenPatternAttribute_whenDeserializing_thenCreatesPatternMatcher() {
        val xml = """
            <rules>
              <activity block="true" log="false">
                <path pattern="/api/*" />
              </activity>
            </rules>
        """.trimIndent()
        val filter = deserializer.deserialize(xml).rules[0].filters[0]
        assertIs<IfwFilter.Path>(filter)
        assertIs<StringMatcher.Pattern>(filter.matcher)
    }

    @Test
    fun givenRegexAttribute_whenDeserializing_thenCreatesRegexMatcher() {
        val xml = """
            <rules>
              <service block="true" log="false">
                <mime-type regex="image/.*" />
              </service>
            </rules>
        """.trimIndent()
        val filter = deserializer.deserialize(xml).rules[0].filters[0]
        assertIs<IfwFilter.MimeType>(filter)
        assertIs<StringMatcher.Regex>(filter.matcher)
    }

    @Test
    fun givenIsNullAttribute_whenDeserializing_thenCreatesIsNullMatcher() {
        val xml = """
            <rules>
              <broadcast block="true" log="false">
                <data isNull="true" />
              </broadcast>
            </rules>
        """.trimIndent()
        val filter = deserializer.deserialize(xml).rules[0].filters[0]
        assertIs<IfwFilter.Data>(filter)
        val matcher = filter.matcher
        assertIs<StringMatcher.IsNull>(matcher)
        assertTrue(matcher.isNull)
    }

    // ── All string-based filter tags ──

    @Test
    fun givenAllStringFilterTags_whenDeserializing_thenParsesAllFilterTypes() {
        val xml = """
            <rules>
              <broadcast block="true" log="false">
                <action equals="a" />
                <component equals="b" />
                <component-name equals="c" />
                <component-package equals="d" />
                <data equals="e" />
                <host equals="f" />
                <mime-type equals="g" />
                <scheme equals="h" />
                <scheme-specific-part equals="i" />
                <path equals="j" />
              </broadcast>
            </rules>
        """.trimIndent()
        val filters = deserializer.deserialize(xml).rules[0].filters
        assertEquals(10, filters.size)
        assertIs<IfwFilter.Action>(filters[0])
        assertIs<IfwFilter.Component>(filters[1])
        assertIs<IfwFilter.ComponentName>(filters[2])
        assertIs<IfwFilter.ComponentPackage>(filters[3])
        assertIs<IfwFilter.Data>(filters[4])
        assertIs<IfwFilter.Host>(filters[5])
        assertIs<IfwFilter.MimeType>(filters[6])
        assertIs<IfwFilter.Scheme>(filters[7])
        assertIs<IfwFilter.SchemeSpecificPart>(filters[8])
        assertIs<IfwFilter.Path>(filters[9])
    }

    // ── Non-string filters ──

    @Test
    fun givenCategoryXml_whenDeserializing_thenParsesCorrectly() {
        val xml = """
            <rules>
              <activity block="true" log="false">
                <category name="android.intent.category.LAUNCHER" />
              </activity>
            </rules>
        """.trimIndent()
        val filter = deserializer.deserialize(xml).rules[0].filters[0]
        assertIs<IfwFilter.Category>(filter)
        assertEquals("android.intent.category.LAUNCHER", filter.name)
    }

    @Test
    fun givenPortWithExactMatch_whenDeserializing_thenParsesEqualsValue() {
        val xml = """
            <rules>
              <broadcast block="true" log="false">
                <port equals="8080" />
              </broadcast>
            </rules>
        """.trimIndent()
        val filter = deserializer.deserialize(xml).rules[0].filters[0]
        assertIs<IfwFilter.Port>(filter)
        assertEquals(8080, filter.equals)
    }

    @Test
    fun givenPortWithRange_whenDeserializing_thenParsesMinMaxValues() {
        val xml = """
            <rules>
              <broadcast block="true" log="false">
                <port min="1" max="1024" />
              </broadcast>
            </rules>
        """.trimIndent()
        val filter = deserializer.deserialize(xml).rules[0].filters[0]
        assertIs<IfwFilter.Port>(filter)
        assertEquals(1, filter.min)
        assertEquals(1024, filter.max)
    }

    @Test
    fun givenAllSenderTypes_whenDeserializing_thenParsesEachTypeCorrectly() {
        listOf("signature", "system", "system|signature", "userId").forEach { type ->
            val xml = """
                <rules>
                  <broadcast block="true" log="false">
                    <sender type="$type" />
                  </broadcast>
                </rules>
            """.trimIndent()
            val filter = deserializer.deserialize(xml).rules[0].filters[0]
            assertIs<IfwFilter.Sender>(filter)
            assertEquals(SenderType.fromXmlValue(type), filter.type)
        }
    }

    @Test
    fun givenSenderPackageXml_whenDeserializing_thenParsesCorrectly() {
        val xml = """
            <rules>
              <broadcast block="true" log="false">
                <sender-package name="com.android.systemui" />
              </broadcast>
            </rules>
        """.trimIndent()
        val filter = deserializer.deserialize(xml).rules[0].filters[0]
        assertIs<IfwFilter.SenderPackage>(filter)
        assertEquals("com.android.systemui", filter.name)
    }

    @Test
    fun givenSenderPermissionXml_whenDeserializing_thenParsesCorrectly() {
        val xml = """
            <rules>
              <broadcast block="true" log="false">
                <sender-permission name="android.permission.INTERNET" />
              </broadcast>
            </rules>
        """.trimIndent()
        val filter = deserializer.deserialize(xml).rules[0].filters[0]
        assertIs<IfwFilter.SenderPermission>(filter)
        assertEquals("android.permission.INTERNET", filter.name)
    }

    // ── Composite filters ──

    @Test
    fun givenAndCompositeXml_whenDeserializing_thenParsesChildFilters() {
        val xml = """
            <rules>
              <broadcast block="true" log="false">
                <and>
                  <action equals="a" />
                  <action equals="b" />
                </and>
              </broadcast>
            </rules>
        """.trimIndent()
        val filter = deserializer.deserialize(xml).rules[0].filters[0]
        assertIs<IfwFilter.And>(filter)
        assertEquals(2, filter.filters.size)
    }

    @Test
    fun givenOrCompositeXml_whenDeserializing_thenParsesChildFilters() {
        val xml = """
            <rules>
              <broadcast block="true" log="false">
                <or>
                  <scheme equals="http" />
                  <scheme equals="https" />
                </or>
              </broadcast>
            </rules>
        """.trimIndent()
        val filter = deserializer.deserialize(xml).rules[0].filters[0]
        assertIs<IfwFilter.Or>(filter)
        assertEquals(2, filter.filters.size)
    }

    @Test
    fun givenNotCompositeXml_whenDeserializing_thenParsesChildFilter() {
        val xml = """
            <rules>
              <broadcast block="true" log="false">
                <not>
                  <sender type="system" />
                </not>
              </broadcast>
            </rules>
        """.trimIndent()
        val filter = deserializer.deserialize(xml).rules[0].filters[0]
        assertIs<IfwFilter.Not>(filter)
        assertIs<IfwFilter.Sender>(filter.filter)
    }

    @Test
    fun givenDeeplyNestedComposites_whenDeserializing_thenParsesFullHierarchy() {
        val xml = """
            <rules>
              <broadcast block="true" log="true">
                <and>
                  <action equals="android.intent.action.BOOT_COMPLETED" />
                  <or>
                    <sender type="system" />
                    <not>
                      <sender-package name="com.evil.app" />
                    </not>
                  </or>
                </and>
              </broadcast>
            </rules>
        """.trimIndent()
        val filter = deserializer.deserialize(xml).rules[0].filters[0]
        assertIs<IfwFilter.And>(filter)
        assertEquals(2, filter.filters.size)
        assertIs<IfwFilter.Action>(filter.filters[0])
        val orFilter = filter.filters[1]
        assertIs<IfwFilter.Or>(orFilter)
        assertEquals(2, orFilter.filters.size)
        assertIs<IfwFilter.Sender>(orFilter.filters[0])
        val notFilter = orFilter.filters[1]
        assertIs<IfwFilter.Not>(notFilter)
        assertIs<IfwFilter.SenderPackage>(notFilter.filter)
    }

    // ── Legacy format compatibility ──

    @Test
    fun givenComponentFilterOnlyXml_whenDeserializing_thenParsesMultipleComponentFilters() {
        val xml = """
            <rules>
              <activity block="true" log="false">
                <component-filter name="com.example/com.example.MainActivity" />
              </activity>
              <broadcast block="true" log="false">
                <component-filter name="com.example/com.example.BootReceiver" />
                <component-filter name="com.example/com.example.AlarmReceiver" />
              </broadcast>
              <service block="true" log="false">
                <component-filter name="com.example/com.example.DaemonService" />
              </service>
            </rules>
        """.trimIndent()
        val result = deserializer.deserialize(xml)
        assertEquals(3, result.rules.size)
        assertEquals(1, result.rules[0].filters.size)
        assertEquals(2, result.rules[1].filters.size)
        assertEquals(1, result.rules[2].filters.size)
    }

    // ── Error handling ──

    @Test
    fun givenMissingRequiredAttribute_whenDeserializing_thenThrowsParseException() {
        val xml = """
            <rules>
              <broadcast block="true" log="false">
                <category />
              </broadcast>
            </rules>
        """.trimIndent()
        assertFailsWith<IfwXmlParseException> {
            deserializer.deserialize(xml)
        }
    }

    @Test
    fun givenUnknownSenderType_whenDeserializing_thenThrowsIllegalArgumentException() {
        val xml = """
            <rules>
              <broadcast block="true" log="false">
                <sender type="unknown" />
              </broadcast>
            </rules>
        """.trimIndent()
        assertFailsWith<IllegalArgumentException> {
            deserializer.deserialize(xml)
        }
    }
}
