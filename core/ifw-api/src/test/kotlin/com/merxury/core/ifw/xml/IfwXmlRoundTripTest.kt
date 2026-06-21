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
import com.merxury.core.ifw.model.IfwIntentFilter
import com.merxury.core.ifw.model.IfwMimeTypeEntry
import com.merxury.core.ifw.model.IfwMimeTypeKind
import com.merxury.core.ifw.model.IfwPatternMatcher
import com.merxury.core.ifw.model.IfwPatternMatcherType
import com.merxury.core.ifw.model.IfwRule
import com.merxury.core.ifw.model.IfwRules
import com.merxury.core.ifw.model.SenderType
import com.merxury.core.ifw.model.StringMatcher
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertEquals

/**
 * Verifies that serialize → deserialize produces the original domain model.
 */
@RunWith(RobolectricTestRunner::class)
class IfwXmlRoundTripTest {

    private lateinit var serializer: IfwXmlSerializer
    private lateinit var deserializer: IfwXmlDeserializer

    @Before
    fun setUp() {
        serializer = IfwXmlSerializer()
        deserializer = IfwXmlDeserializer()
    }

    private fun roundTrip(rules: IfwRules): IfwRules {
        val xml = serializer.serialize(rules)
        return deserializer.deserialize(xml)
    }

    @Test
    fun givenEmptyRules_whenRoundTripping_thenProducesEqualResult() {
        val original = IfwRules.empty()
        val result = roundTrip(original)
        assertEquals(original, result)
    }

    @Test
    fun givenComponentFilters_whenRoundTripping_thenProducesEqualResult() {
        val original = IfwRules(
            listOf(
                IfwRule(
                    componentType = IfwComponentType.ACTIVITY,
                    filters = listOf(IfwFilter.ComponentFilter("com.example/com.example.Main")),
                ),
                IfwRule(
                    componentType = IfwComponentType.BROADCAST,
                    filters = listOf(
                        IfwFilter.ComponentFilter("com.example/com.example.R1"),
                        IfwFilter.ComponentFilter("com.example/com.example.R2"),
                    ),
                ),
                IfwRule(
                    componentType = IfwComponentType.SERVICE,
                    filters = listOf(IfwFilter.ComponentFilter("com.example/com.example.S1")),
                ),
            ),
        )
        assertEquals(original, roundTrip(original))
    }

    @Test
    fun givenAllStringMatcherModes_whenRoundTripping_thenProducesEqualResult() {
        val original = IfwRules(
            listOf(
                IfwRule(
                    componentType = IfwComponentType.BROADCAST,
                    filters = listOf(
                        IfwFilter.Action(StringMatcher.Equals("a")),
                        IfwFilter.Action(StringMatcher.StartsWith("b")),
                        IfwFilter.Action(StringMatcher.Contains("c")),
                        IfwFilter.Action(StringMatcher.Pattern("d")),
                        IfwFilter.Action(StringMatcher.Regex("e")),
                        IfwFilter.Data(StringMatcher.IsNull(true)),
                    ),
                ),
            ),
        )
        assertEquals(original, roundTrip(original))
    }

    @Test
    fun givenAllStringFilterTypes_whenRoundTripping_thenProducesEqualResult() {
        val eq = StringMatcher.Equals("test")
        val original = IfwRules(
            listOf(
                IfwRule(
                    componentType = IfwComponentType.BROADCAST,
                    filters = listOf(
                        IfwFilter.Action(eq),
                        IfwFilter.Component(eq),
                        IfwFilter.ComponentName(eq),
                        IfwFilter.ComponentPackage(eq),
                        IfwFilter.Data(eq),
                        IfwFilter.Host(eq),
                        IfwFilter.MimeType(eq),
                        IfwFilter.Scheme(eq),
                        IfwFilter.SchemeSpecificPart(eq),
                        IfwFilter.Path(eq),
                    ),
                ),
            ),
        )
        assertEquals(original, roundTrip(original))
    }

    @Test
    fun givenNonStringFilters_whenRoundTripping_thenProducesEqualResult() {
        val original = IfwRules(
            listOf(
                IfwRule(
                    componentType = IfwComponentType.BROADCAST,
                    filters = listOf(
                        IfwFilter.Category("android.intent.category.DEFAULT"),
                        IfwFilter.Port(equals = 443),
                        IfwFilter.Port(min = 1, max = 1024),
                        IfwFilter.Sender(SenderType.SYSTEM),
                        IfwFilter.Sender(SenderType.SIGNATURE),
                        IfwFilter.Sender(SenderType.SYSTEM_OR_SIGNATURE),
                        IfwFilter.Sender(SenderType.USER_ID),
                        IfwFilter.SenderPackage("com.android.systemui"),
                        IfwFilter.SenderPermission("android.permission.INTERNET"),
                    ),
                ),
            ),
        )
        assertEquals(original, roundTrip(original))
    }

    @Test
    fun givenCompositeFilters_whenRoundTripping_thenProducesEqualResult() {
        val original = IfwRules(
            listOf(
                IfwRule(
                    componentType = IfwComponentType.BROADCAST,
                    log = true,
                    filters = listOf(
                        IfwFilter.And(
                            listOf(
                                IfwFilter.Action(StringMatcher.Equals("android.intent.action.BOOT_COMPLETED")),
                                IfwFilter.Or(
                                    listOf(
                                        IfwFilter.Sender(SenderType.SYSTEM),
                                        IfwFilter.Not(
                                            IfwFilter.SenderPackage("com.evil.app"),
                                        ),
                                    ),
                                ),
                            ),
                        ),
                    ),
                ),
            ),
        )
        assertEquals(original, roundTrip(original))
    }

    @Test
    fun givenIntentFilterSelectors_whenRoundTripping_thenProducesEqualResult() {
        val original = IfwRules(
            listOf(
                IfwRule(
                    componentType = IfwComponentType.BROADCAST,
                    intentFilters = listOf(
                        IfwIntentFilter(
                            actions = listOf("android.intent.action.VIEW"),
                            categories = listOf("android.intent.category.DEFAULT"),
                            dataTypes = listOf(
                                IfwMimeTypeEntry("image/*", IfwMimeTypeKind.STATIC),
                                IfwMimeTypeEntry("text/plain", IfwMimeTypeKind.DYNAMIC),
                            ),
                            schemes = listOf("content"),
                            schemeSpecificParts = listOf(
                                IfwPatternMatcher("id:", IfwPatternMatcherType.PREFIX),
                            ),
                            authorities = listOf(),
                            paths = listOf(
                                IfwPatternMatcher("/items", IfwPatternMatcherType.LITERAL),
                            ),
                        ),
                    ),
                    filters = listOf(IfwFilter.SenderPackage("com.example.sender")),
                ),
            ),
        )

        assertEquals(original, roundTrip(original))
    }

    @Test
    fun givenBlockFalseAndLogTrue_whenRoundTripping_thenProducesEqualResult() {
        val original = IfwRules(
            listOf(
                IfwRule(
                    componentType = IfwComponentType.SERVICE,
                    block = false,
                    log = true,
                    filters = listOf(IfwFilter.ComponentFilter("a/b")),
                ),
            ),
        )
        assertEquals(original, roundTrip(original))
    }

    @Test
    fun givenComponentFilterOnlyXml_whenDeserializing_thenParsesAllComponentFiltersCorrectly() {
        val legacyXml = """
            <rules>
               <activity block="true" log="false">
                  <component-filter name="com.example/com.example.MainActivity" />
               </activity>
               <broadcast block="true" log="false">
                  <component-filter name="com.example/com.example.AlarmReceiver" />
                  <component-filter name="com.example/com.example.BootReceiver" />
               </broadcast>
               <service block="true" log="false">
                  <component-filter name="com.example/com.example.DaemonService" />
               </service>
            </rules>
        """.trimIndent()
        val result = deserializer.deserialize(legacyXml)
        assertEquals(3, result.rules.size)
        assertEquals(
            setOf("com.example/com.example.MainActivity"),
            result.componentFiltersFor(IfwComponentType.ACTIVITY),
        )
        assertEquals(
            setOf("com.example/com.example.AlarmReceiver", "com.example/com.example.BootReceiver"),
            result.componentFiltersFor(IfwComponentType.BROADCAST),
        )
        assertEquals(
            setOf("com.example/com.example.DaemonService"),
            result.componentFiltersFor(IfwComponentType.SERVICE),
        )
    }
}
