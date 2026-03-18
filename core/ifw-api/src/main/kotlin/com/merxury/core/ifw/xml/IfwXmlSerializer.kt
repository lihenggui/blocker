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

import android.util.Xml
import com.merxury.core.ifw.model.IfwComponentType
import com.merxury.core.ifw.model.IfwFilter
import com.merxury.core.ifw.model.IfwRule
import com.merxury.core.ifw.model.IfwRules
import com.merxury.core.ifw.model.StringMatcher
import com.merxury.core.ifw.xml.IfwXmlConstants.ATTR_BLOCK
import com.merxury.core.ifw.xml.IfwXmlConstants.ATTR_CONTAINS
import com.merxury.core.ifw.xml.IfwXmlConstants.ATTR_EQUALS
import com.merxury.core.ifw.xml.IfwXmlConstants.ATTR_IS_NULL
import com.merxury.core.ifw.xml.IfwXmlConstants.ATTR_LOG
import com.merxury.core.ifw.xml.IfwXmlConstants.ATTR_MAX
import com.merxury.core.ifw.xml.IfwXmlConstants.ATTR_MIN
import com.merxury.core.ifw.xml.IfwXmlConstants.ATTR_NAME
import com.merxury.core.ifw.xml.IfwXmlConstants.ATTR_PATTERN
import com.merxury.core.ifw.xml.IfwXmlConstants.ATTR_REGEX
import com.merxury.core.ifw.xml.IfwXmlConstants.ATTR_STARTS_WITH
import com.merxury.core.ifw.xml.IfwXmlConstants.ATTR_TYPE
import com.merxury.core.ifw.xml.IfwXmlConstants.TAG_AND
import com.merxury.core.ifw.xml.IfwXmlConstants.TAG_CATEGORY
import com.merxury.core.ifw.xml.IfwXmlConstants.TAG_COMPONENT_FILTER
import com.merxury.core.ifw.xml.IfwXmlConstants.TAG_NOT
import com.merxury.core.ifw.xml.IfwXmlConstants.TAG_OR
import com.merxury.core.ifw.xml.IfwXmlConstants.TAG_PORT
import com.merxury.core.ifw.xml.IfwXmlConstants.TAG_RULES
import com.merxury.core.ifw.xml.IfwXmlConstants.TAG_SENDER
import com.merxury.core.ifw.xml.IfwXmlConstants.TAG_SENDER_PACKAGE
import com.merxury.core.ifw.xml.IfwXmlConstants.TAG_SENDER_PERMISSION
import java.io.StringWriter
import org.xmlpull.v1.XmlSerializer as XmlPullSerializer

/**
 * Serializes the [IfwRules] domain model to Android Intent Firewall XML.
 *
 * Produces XML compatible with AOSP's IntentFirewall parser, including
 * support for composite filters, all string matching modes, and special filters.
 *
 * Thread-safe: each call to [serialize] creates its own writer instance.
 *
 * @see IfwXmlDeserializer for the reverse operation
 */
class IfwXmlSerializer {

    /**
     * Serializes an [IfwRules] to an XML string.
     *
     * @param rules the domain model to serialize
     * @return the XML string representation
     */
    fun serialize(rules: IfwRules): String {
        val writer = StringWriter()
        val serializer = Xml.newSerializer()
        serializer.setOutput(writer)
        serializer.startDocument("utf-8", null)
        serializer.text("\n")
        serializer.startTag(null, TAG_RULES)

        for (componentType in IfwComponentType.entries) {
            val rulesOfType = rules.rulesFor(componentType)
            for (rule in rulesOfType) {
                serializer.text("\n  ")
                serializeRule(serializer, rule)
            }
        }

        serializer.text("\n")
        serializer.endTag(null, TAG_RULES)
        serializer.endDocument()
        return writer.toString()
    }

    private fun serializeRule(
        serializer: XmlPullSerializer,
        rule: IfwRule,
    ) {
        serializer.startTag(null, rule.componentType.xmlTag)
        serializer.attribute(null, ATTR_BLOCK, rule.block.toString())
        serializer.attribute(null, ATTR_LOG, rule.log.toString())

        for (intentFilter in rule.intentFilters) {
            serializer.text("\n    ")
            serializeIntentFilter(serializer, intentFilter)
        }

        for (filter in rule.filters) {
            serializer.text("\n    ")
            serializeFilter(serializer, filter, indent = 4)
        }

        serializer.text("\n  ")
        serializer.endTag(null, rule.componentType.xmlTag)
    }

    private fun serializeFilter(
        serializer: XmlPullSerializer,
        filter: IfwFilter,
        indent: Int,
    ) {
        when (filter) {
            is IfwFilter.And -> serializeComposite(serializer, TAG_AND, filter.filters, indent)
            is IfwFilter.Or -> serializeComposite(serializer, TAG_OR, filter.filters, indent)
            is IfwFilter.Not -> serializeComposite(serializer, TAG_NOT, listOf(filter.filter), indent)

            is IfwFilter.Action -> serializeStringFilter(serializer, "action", filter.matcher)
            is IfwFilter.Component -> serializeStringFilter(serializer, "component", filter.matcher)
            is IfwFilter.ComponentName -> serializeStringFilter(serializer, "component-name", filter.matcher)
            is IfwFilter.ComponentPackage -> serializeStringFilter(serializer, "component-package", filter.matcher)
            is IfwFilter.Data -> serializeStringFilter(serializer, "data", filter.matcher)
            is IfwFilter.Host -> serializeStringFilter(serializer, "host", filter.matcher)
            is IfwFilter.MimeType -> serializeStringFilter(serializer, "mime-type", filter.matcher)
            is IfwFilter.Scheme -> serializeStringFilter(serializer, "scheme", filter.matcher)
            is IfwFilter.SchemeSpecificPart -> serializeStringFilter(serializer, "scheme-specific-part", filter.matcher)
            is IfwFilter.Path -> serializeStringFilter(serializer, "path", filter.matcher)

            is IfwFilter.Category -> {
                serializer.startTag(null, TAG_CATEGORY)
                serializer.attribute(null, ATTR_NAME, filter.name)
                serializer.endTag(null, TAG_CATEGORY)
            }

            is IfwFilter.Port -> {
                serializer.startTag(null, TAG_PORT)
                filter.equals?.let { serializer.attribute(null, ATTR_EQUALS, it.toString()) }
                filter.min?.let { serializer.attribute(null, ATTR_MIN, it.toString()) }
                filter.max?.let { serializer.attribute(null, ATTR_MAX, it.toString()) }
                serializer.endTag(null, TAG_PORT)
            }

            is IfwFilter.Sender -> {
                serializer.startTag(null, TAG_SENDER)
                serializer.attribute(null, ATTR_TYPE, filter.type.xmlValue)
                serializer.endTag(null, TAG_SENDER)
            }

            is IfwFilter.SenderPackage -> {
                serializer.startTag(null, TAG_SENDER_PACKAGE)
                serializer.attribute(null, ATTR_NAME, filter.name)
                serializer.endTag(null, TAG_SENDER_PACKAGE)
            }

            is IfwFilter.SenderPermission -> {
                serializer.startTag(null, TAG_SENDER_PERMISSION)
                serializer.attribute(null, ATTR_NAME, filter.name)
                serializer.endTag(null, TAG_SENDER_PERMISSION)
            }

            is IfwFilter.ComponentFilter -> {
                serializer.startTag(null, TAG_COMPONENT_FILTER)
                serializer.attribute(null, ATTR_NAME, filter.name)
                serializer.endTag(null, TAG_COMPONENT_FILTER)
            }
        }
    }

    private fun serializeComposite(
        serializer: XmlPullSerializer,
        tag: String,
        children: List<IfwFilter>,
        indent: Int,
    ) {
        serializer.startTag(null, tag)
        val childIndent = indent + 2
        val childPrefix = "\n" + " ".repeat(childIndent)
        for (child in children) {
            serializer.text(childPrefix)
            serializeFilter(serializer, child, childIndent)
        }
        serializer.text("\n" + " ".repeat(indent))
        serializer.endTag(null, tag)
    }

    private fun serializeStringFilter(
        serializer: XmlPullSerializer,
        tag: String,
        matcher: StringMatcher,
    ) {
        serializer.startTag(null, tag)
        when (matcher) {
            is StringMatcher.Equals -> serializer.attribute(null, ATTR_EQUALS, matcher.value)
            is StringMatcher.StartsWith -> serializer.attribute(null, ATTR_STARTS_WITH, matcher.value)
            is StringMatcher.Contains -> serializer.attribute(null, ATTR_CONTAINS, matcher.value)
            is StringMatcher.Pattern -> serializer.attribute(null, ATTR_PATTERN, matcher.value)
            is StringMatcher.Regex -> serializer.attribute(null, ATTR_REGEX, matcher.value)
            is StringMatcher.IsNull -> serializer.attribute(null, ATTR_IS_NULL, matcher.isNull.toString())
        }
        serializer.endTag(null, tag)
    }
}
