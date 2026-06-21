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
import kotlin.test.assertContains
import kotlin.test.assertFalse

@RunWith(RobolectricTestRunner::class)
class IfwXmlSerializerTest {

    private lateinit var serializer: IfwXmlSerializer

    @Before
    fun setUp() {
        serializer = IfwXmlSerializer()
    }

    @Test
    fun givenEmptyRules_whenSerializing_thenProducesRulesElement() {
        val xml = serializer.serialize(IfwRules.empty())
        assertContains(xml, "<rules")
        assertContains(xml, "</rules>")
    }

    @Test
    fun givenComponentFilter_whenSerializing_thenOutputsCorrectXml() {
        val rules = IfwRules(
            listOf(
                IfwRule(
                    componentType = IfwComponentType.ACTIVITY,
                    filters = listOf(IfwFilter.ComponentFilter("com.example/com.example.Main")),
                ),
            ),
        )
        val xml = serializer.serialize(rules)
        assertContains(xml, "<activity")
        assertContains(xml, "block=\"true\"")
        assertContains(xml, "log=\"true\"")
        assertContains(xml, "<component-filter")
        assertContains(xml, "name=\"com.example/com.example.Main\"")
    }

    @Test
    fun givenIntentFilterSelector_whenSerializing_thenOutputsAospIntentFilterXml() {
        val rules = IfwRules(
            listOf(
                IfwRule(
                    componentType = IfwComponentType.BROADCAST,
                    intentFilters = listOf(
                        IfwIntentFilter(
                            actions = listOf("android.intent.action.VIEW"),
                            categories = listOf("android.intent.category.DEFAULT"),
                            dataTypes = listOf(
                                IfwMimeTypeEntry("image", IfwMimeTypeKind.STATIC),
                                IfwMimeTypeEntry("text/plain", IfwMimeTypeKind.DYNAMIC),
                            ),
                            schemes = listOf("content"),
                            schemeSpecificParts = listOf(
                                IfwPatternMatcher("id:", IfwPatternMatcherType.PREFIX),
                            ),
                            paths = listOf(
                                IfwPatternMatcher("/items", IfwPatternMatcherType.LITERAL),
                            ),
                        ),
                    ),
                    filters = listOf(IfwFilter.SenderPackage("com.example.sender")),
                ),
            ),
        )

        val xml = serializer.serialize(rules)

        assertContains(xml, "<intent-filter>")
        assertContains(xml, "<action name=\"android.intent.action.VIEW\"")
        assertContains(xml, "<cat name=\"android.intent.category.DEFAULT\"")
        assertContains(xml, "<staticType name=\"image/*\"")
        assertContains(xml, "<type name=\"text/plain\"")
        assertContains(xml, "<scheme name=\"content\"")
        assertContains(xml, "<ssp prefix=\"id:\"")
        assertContains(xml, "<path literal=\"/items\"")
    }

    @Test
    fun givenAllStringMatcherModes_whenSerializing_thenOutputsCorrectAttributes() {
        val rules = IfwRules(
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
        val xml = serializer.serialize(rules)
        assertContains(xml, "equals=\"a\"")
        assertContains(xml, "startsWith=\"b\"")
        assertContains(xml, "contains=\"c\"")
        assertContains(xml, "pattern=\"d\"")
        assertContains(xml, "regex=\"e\"")
        assertContains(xml, "isNull=\"true\"")
    }

    @Test
    fun givenAllStringFilterTags_whenSerializing_thenOutputsAllTags() {
        val eq = StringMatcher.Equals("x")
        val rules = IfwRules(
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
        val xml = serializer.serialize(rules)
        assertContains(xml, "<action")
        assertContains(xml, "<component ")
        assertContains(xml, "<component-name")
        assertContains(xml, "<component-package")
        assertContains(xml, "<data")
        assertContains(xml, "<host")
        assertContains(xml, "<mime-type")
        assertContains(xml, "<scheme ")
        assertContains(xml, "<scheme-specific-part")
        assertContains(xml, "<path")
    }

    @Test
    fun givenCategoryFilter_whenSerializing_thenOutputsCorrectXml() {
        val rules = IfwRules(
            listOf(
                IfwRule(
                    componentType = IfwComponentType.ACTIVITY,
                    filters = listOf(IfwFilter.Category("android.intent.category.LAUNCHER")),
                ),
            ),
        )
        val xml = serializer.serialize(rules)
        assertContains(xml, "<category")
        assertContains(xml, "name=\"android.intent.category.LAUNCHER\"")
    }

    @Test
    fun givenPortWithExactMatch_whenSerializing_thenOutputsEqualsAttribute() {
        val rules = IfwRules(
            listOf(
                IfwRule(
                    componentType = IfwComponentType.BROADCAST,
                    filters = listOf(IfwFilter.Port(equals = 8080)),
                ),
            ),
        )
        val xml = serializer.serialize(rules)
        assertContains(xml, "<port")
        assertContains(xml, "equals=\"8080\"")
    }

    @Test
    fun givenPortWithRange_whenSerializing_thenOutputsMinMaxAttributes() {
        val rules = IfwRules(
            listOf(
                IfwRule(
                    componentType = IfwComponentType.BROADCAST,
                    filters = listOf(IfwFilter.Port(min = 1, max = 1024)),
                ),
            ),
        )
        val xml = serializer.serialize(rules)
        assertContains(xml, "min=\"1\"")
        assertContains(xml, "max=\"1024\"")
        assertFalse(xml.contains("equals="))
    }

    @Test
    fun givenSenderFilter_whenSerializing_thenOutputsCorrectTypeAttribute() {
        val rules = IfwRules(
            listOf(
                IfwRule(
                    componentType = IfwComponentType.BROADCAST,
                    filters = listOf(IfwFilter.Sender(SenderType.SYSTEM_OR_SIGNATURE)),
                ),
            ),
        )
        val xml = serializer.serialize(rules)
        assertContains(xml, "<sender")
        assertContains(xml, "type=\"system|signature\"")
    }

    @Test
    fun givenSenderPackageFilter_whenSerializing_thenOutputsCorrectXml() {
        val rules = IfwRules(
            listOf(
                IfwRule(
                    componentType = IfwComponentType.BROADCAST,
                    filters = listOf(IfwFilter.SenderPackage("com.android.systemui")),
                ),
            ),
        )
        val xml = serializer.serialize(rules)
        assertContains(xml, "<sender-package")
        assertContains(xml, "name=\"com.android.systemui\"")
    }

    @Test
    fun givenSenderPermissionFilter_whenSerializing_thenOutputsCorrectXml() {
        val rules = IfwRules(
            listOf(
                IfwRule(
                    componentType = IfwComponentType.BROADCAST,
                    filters = listOf(IfwFilter.SenderPermission("android.permission.INTERNET")),
                ),
            ),
        )
        val xml = serializer.serialize(rules)
        assertContains(xml, "<sender-permission")
        assertContains(xml, "name=\"android.permission.INTERNET\"")
    }

    @Test
    fun givenAndComposite_whenSerializing_thenOutputsAndElement() {
        val rules = IfwRules(
            listOf(
                IfwRule(
                    componentType = IfwComponentType.BROADCAST,
                    filters = listOf(
                        IfwFilter.And(
                            listOf(
                                IfwFilter.Action(StringMatcher.Equals("a")),
                                IfwFilter.Sender(SenderType.SYSTEM),
                            ),
                        ),
                    ),
                ),
            ),
        )
        val xml = serializer.serialize(rules)
        assertContains(xml, "<and>")
        assertContains(xml, "</and>")
        assertContains(xml, "<action")
        assertContains(xml, "<sender")
    }

    @Test
    fun givenOrComposite_whenSerializing_thenOutputsOrElement() {
        val rules = IfwRules(
            listOf(
                IfwRule(
                    componentType = IfwComponentType.BROADCAST,
                    filters = listOf(
                        IfwFilter.Or(
                            listOf(
                                IfwFilter.Scheme(StringMatcher.Equals("http")),
                                IfwFilter.Scheme(StringMatcher.Equals("https")),
                            ),
                        ),
                    ),
                ),
            ),
        )
        val xml = serializer.serialize(rules)
        assertContains(xml, "<or>")
        assertContains(xml, "</or>")
    }

    @Test
    fun givenNotComposite_whenSerializing_thenOutputsNotElement() {
        val rules = IfwRules(
            listOf(
                IfwRule(
                    componentType = IfwComponentType.BROADCAST,
                    filters = listOf(
                        IfwFilter.Not(IfwFilter.Sender(SenderType.SYSTEM)),
                    ),
                ),
            ),
        )
        val xml = serializer.serialize(rules)
        assertContains(xml, "<not>")
        assertContains(xml, "</not>")
        assertContains(xml, "<sender")
    }

    @Test
    fun givenBlockFalseAndLogTrue_whenSerializing_thenOutputsCorrectAttributes() {
        val rules = IfwRules(
            listOf(
                IfwRule(
                    componentType = IfwComponentType.SERVICE,
                    block = false,
                    log = true,
                    filters = listOf(IfwFilter.ComponentFilter("a/b")),
                ),
            ),
        )
        val xml = serializer.serialize(rules)
        assertContains(xml, "block=\"false\"")
        assertContains(xml, "log=\"true\"")
    }
}
