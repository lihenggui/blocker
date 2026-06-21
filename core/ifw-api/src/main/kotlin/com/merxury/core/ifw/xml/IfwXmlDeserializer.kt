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
import com.merxury.core.ifw.model.IfwRule
import com.merxury.core.ifw.model.IfwRules
import com.merxury.core.ifw.model.SenderType
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
import com.merxury.core.ifw.xml.IfwXmlConstants.TAG_ACTION
import com.merxury.core.ifw.xml.IfwXmlConstants.TAG_AND
import com.merxury.core.ifw.xml.IfwXmlConstants.TAG_CATEGORY
import com.merxury.core.ifw.xml.IfwXmlConstants.TAG_COMPONENT
import com.merxury.core.ifw.xml.IfwXmlConstants.TAG_COMPONENT_FILTER
import com.merxury.core.ifw.xml.IfwXmlConstants.TAG_COMPONENT_NAME
import com.merxury.core.ifw.xml.IfwXmlConstants.TAG_COMPONENT_PACKAGE
import com.merxury.core.ifw.xml.IfwXmlConstants.TAG_DATA
import com.merxury.core.ifw.xml.IfwXmlConstants.TAG_HOST
import com.merxury.core.ifw.xml.IfwXmlConstants.TAG_INTENT_FILTER
import com.merxury.core.ifw.xml.IfwXmlConstants.TAG_MIME_TYPE
import com.merxury.core.ifw.xml.IfwXmlConstants.TAG_NOT
import com.merxury.core.ifw.xml.IfwXmlConstants.TAG_OR
import com.merxury.core.ifw.xml.IfwXmlConstants.TAG_PATH
import com.merxury.core.ifw.xml.IfwXmlConstants.TAG_PORT
import com.merxury.core.ifw.xml.IfwXmlConstants.TAG_RULES
import com.merxury.core.ifw.xml.IfwXmlConstants.TAG_SCHEME
import com.merxury.core.ifw.xml.IfwXmlConstants.TAG_SCHEME_SPECIFIC_PART
import com.merxury.core.ifw.xml.IfwXmlConstants.TAG_SENDER
import com.merxury.core.ifw.xml.IfwXmlConstants.TAG_SENDER_PACKAGE
import com.merxury.core.ifw.xml.IfwXmlConstants.TAG_SENDER_PERMISSION
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.StringReader

/**
 * Deserializes Android Intent Firewall XML into the [IfwRules] domain model.
 *
 * Supports the complete set of AOSP IFW filter types including composite
 * filters (`<and>`, `<or>`, `<not>`), string-based filters with all matching
 * modes, and special filters (`<sender>`, `<port>`, `<category>`, etc.).
 *
 * Thread-safe: each call to [deserialize] creates its own parser instance.
 *
 * @see IfwXmlSerializer for the reverse operation
 */
class IfwXmlDeserializer {

    /**
     * Parses an IFW XML string into an [IfwRules] domain model.
     *
     * @param xml the XML string to parse
     * @return the parsed [IfwRules]
     * @throws IfwXmlParseException if the XML is malformed or contains unrecognized elements
     */
    fun deserialize(xml: String): IfwRules {
        val parser = XmlPullParserFactory.newInstance().newPullParser()
        parser.setInput(StringReader(xml))

        val rules = mutableListOf<IfwRule>()
        var eventType = parser.eventType

        while (eventType != XmlPullParser.END_DOCUMENT) {
            if (eventType == XmlPullParser.START_TAG && parser.name != TAG_RULES) {
                val componentType = IfwComponentType.fromXmlTag(parser.name)
                if (componentType != null) {
                    rules.add(parseRule(parser, componentType))
                }
            }
            eventType = parser.next()
        }
        return IfwRules(rules)
    }

    private fun parseRule(parser: XmlPullParser, componentType: IfwComponentType): IfwRule {
        val block = parser.getAttributeValue(null, ATTR_BLOCK)?.toBoolean() ?: false
        val log = parser.getAttributeValue(null, ATTR_LOG)?.toBoolean() ?: false
        val intentFilters = mutableListOf<IfwIntentFilter>()
        val filters = parseRuleChildren(parser, intentFilters)
        return IfwRule(
            componentType = componentType,
            block = block,
            log = log,
            intentFilters = intentFilters,
            filters = filters,
        )
    }

    private fun parseRuleChildren(
        parser: XmlPullParser,
        intentFilters: MutableList<IfwIntentFilter>,
    ): List<IfwFilter> {
        val filters = mutableListOf<IfwFilter>()
        val outerDepth = parser.depth
        while (true) {
            when (parser.next()) {
                XmlPullParser.START_TAG -> {
                    if (parser.name == TAG_INTENT_FILTER) {
                        intentFilters += parseIntentFilter(parser)
                    } else {
                        filters += parseFilter(parser)
                    }
                }
                XmlPullParser.END_TAG -> {
                    if (parser.depth == outerDepth) {
                        break
                    }
                }
            }
        }
        return filters
    }

    private fun parseFilter(parser: XmlPullParser): IfwFilter = when (parser.name) {
        TAG_AND -> IfwFilter.And(parseFilterChildren(parser))
        TAG_OR -> IfwFilter.Or(parseFilterChildren(parser))
        TAG_NOT -> {
            val children = parseFilterChildren(parser)
            when (children.size) {
                0 -> throw IfwXmlParseException("<not> must contain a child filter at line ${parser.lineNumber}")
                1 -> IfwFilter.Not(children.first())
                else -> throw IfwXmlParseException(
                    "<not> can only contain a single child filter at line ${parser.lineNumber}",
                )
            }
        }

        // Leaf filters — read attributes, then skip to closing tag
        TAG_ACTION -> parseLeaf(parser) { IfwFilter.Action(parseStringMatcher(parser)) }
        TAG_COMPONENT -> parseLeaf(parser) { IfwFilter.Component(parseStringMatcher(parser)) }
        TAG_COMPONENT_NAME -> parseLeaf(parser) { IfwFilter.ComponentName(parseStringMatcher(parser)) }
        TAG_COMPONENT_PACKAGE -> parseLeaf(parser) { IfwFilter.ComponentPackage(parseStringMatcher(parser)) }
        TAG_DATA -> parseLeaf(parser) { IfwFilter.Data(parseStringMatcher(parser)) }
        TAG_HOST -> parseLeaf(parser) { IfwFilter.Host(parseStringMatcher(parser)) }
        TAG_MIME_TYPE -> parseLeaf(parser) { IfwFilter.MimeType(parseStringMatcher(parser)) }
        TAG_SCHEME -> parseLeaf(parser) { IfwFilter.Scheme(parseStringMatcher(parser)) }
        TAG_SCHEME_SPECIFIC_PART -> parseLeaf(parser) { IfwFilter.SchemeSpecificPart(parseStringMatcher(parser)) }
        TAG_PATH -> parseLeaf(parser) { IfwFilter.Path(parseStringMatcher(parser)) }
        TAG_CATEGORY -> parseLeaf(parser) { IfwFilter.Category(name = requireAttr(parser, ATTR_NAME)) }
        TAG_PORT -> parseLeaf(parser) { parsePort(parser) }
        TAG_SENDER -> parseLeaf(parser) {
            IfwFilter.Sender(type = SenderType.fromXmlValue(requireAttr(parser, ATTR_TYPE)))
        }
        TAG_SENDER_PACKAGE -> parseLeaf(parser) { IfwFilter.SenderPackage(name = requireAttr(parser, ATTR_NAME)) }
        TAG_SENDER_PERMISSION -> parseLeaf(parser) {
            IfwFilter.SenderPermission(name = requireAttr(parser, ATTR_NAME))
        }
        TAG_COMPONENT_FILTER -> parseLeaf(parser) {
            IfwFilter.ComponentFilter(name = requireAttr(parser, ATTR_NAME))
        }

        else -> throw IfwXmlParseException(
            "Unknown element in filter list: ${parser.name} at line ${parser.lineNumber}",
        )
    }

    private fun parseFilterChildren(parser: XmlPullParser): List<IfwFilter> {
        val filters = mutableListOf<IfwFilter>()
        val outerDepth = parser.depth
        while (true) {
            when (parser.next()) {
                XmlPullParser.START_TAG -> filters += parseFilter(parser)
                XmlPullParser.END_TAG -> if (parser.depth == outerDepth) break
            }
        }
        return filters
    }

    /**
     * Parses a leaf (non-composite) filter element, then skips to its END_TAG.
     */
    private inline fun parseLeaf(parser: XmlPullParser, parse: () -> IfwFilter): IfwFilter {
        val filter = parse()
        skipCurrentTag(parser)
        return filter
    }

    private fun parseStringMatcher(parser: XmlPullParser): StringMatcher {
        parser.getAttributeValue(null, ATTR_EQUALS)?.let {
            return StringMatcher.Equals(it)
        }
        parser.getAttributeValue(null, ATTR_STARTS_WITH)?.let {
            return StringMatcher.StartsWith(it)
        }
        parser.getAttributeValue(null, ATTR_CONTAINS)?.let {
            return StringMatcher.Contains(it)
        }
        parser.getAttributeValue(null, ATTR_PATTERN)?.let {
            return StringMatcher.Pattern(it)
        }
        parser.getAttributeValue(null, ATTR_REGEX)?.let {
            return StringMatcher.Regex(it)
        }
        parser.getAttributeValue(null, ATTR_IS_NULL)?.let {
            return StringMatcher.IsNull(it.toBoolean())
        }
        // Fallback: check for legacy "name" attribute (used by old Blocker format)
        parser.getAttributeValue(null, ATTR_NAME)?.let {
            return StringMatcher.Equals(it)
        }
        throw IfwXmlParseException(
            "No string matcher attribute found on <${parser.name}> at line ${parser.lineNumber}",
        )
    }

    private fun parsePort(parser: XmlPullParser): IfwFilter.Port {
        val equalsStr = parser.getAttributeValue(null, ATTR_EQUALS)
        val minStr = parser.getAttributeValue(null, ATTR_MIN)
        val maxStr = parser.getAttributeValue(null, ATTR_MAX)
        return IfwFilter.Port(
            equals = equalsStr?.toInt(),
            min = minStr?.toInt(),
            max = maxStr?.toInt(),
        )
    }

    private fun requireAttr(parser: XmlPullParser, attr: String): String = parser.getAttributeValue(null, attr)
        ?: throw IfwXmlParseException(
            "Missing required attribute '$attr' on <${parser.name}> at line ${parser.lineNumber}",
        )
}

/**
 * Thrown when IFW XML content cannot be parsed.
 *
 * @property message a description of what went wrong, including line number when available
 */
class IfwXmlParseException(message: String) : RuntimeException(message)
