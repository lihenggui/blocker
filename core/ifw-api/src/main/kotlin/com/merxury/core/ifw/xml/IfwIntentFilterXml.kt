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

import android.os.PersistableBundle
import com.merxury.core.ifw.model.IfwAuthorityEntry
import com.merxury.core.ifw.model.IfwIntentFilter
import com.merxury.core.ifw.model.IfwMimeTypeEntry
import com.merxury.core.ifw.model.IfwMimeTypeKind
import com.merxury.core.ifw.model.IfwPatternMatcher
import com.merxury.core.ifw.model.IfwPatternMatcherType
import com.merxury.core.ifw.xml.IfwXmlConstants.ATTR_AGLOB
import com.merxury.core.ifw.xml.IfwXmlConstants.ATTR_HOST
import com.merxury.core.ifw.xml.IfwXmlConstants.ATTR_LITERAL
import com.merxury.core.ifw.xml.IfwXmlConstants.ATTR_NAME
import com.merxury.core.ifw.xml.IfwXmlConstants.ATTR_PORT
import com.merxury.core.ifw.xml.IfwXmlConstants.ATTR_PREFIX
import com.merxury.core.ifw.xml.IfwXmlConstants.ATTR_SGLOB
import com.merxury.core.ifw.xml.IfwXmlConstants.ATTR_SUFFIX
import com.merxury.core.ifw.xml.IfwXmlConstants.TAG_ACTION
import com.merxury.core.ifw.xml.IfwXmlConstants.TAG_INTENT_AUTH
import com.merxury.core.ifw.xml.IfwXmlConstants.TAG_INTENT_CATEGORY
import com.merxury.core.ifw.xml.IfwXmlConstants.TAG_INTENT_EXTRAS
import com.merxury.core.ifw.xml.IfwXmlConstants.TAG_INTENT_FILTER
import com.merxury.core.ifw.xml.IfwXmlConstants.TAG_INTENT_GROUP
import com.merxury.core.ifw.xml.IfwXmlConstants.TAG_INTENT_SSP
import com.merxury.core.ifw.xml.IfwXmlConstants.TAG_INTENT_STATIC_TYPE
import com.merxury.core.ifw.xml.IfwXmlConstants.TAG_INTENT_TYPE
import com.merxury.core.ifw.xml.IfwXmlConstants.TAG_PATH
import com.merxury.core.ifw.xml.IfwXmlConstants.TAG_SCHEME
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlSerializer

internal fun parseIntentFilter(parser: XmlPullParser): IfwIntentFilter {
    val actions = mutableListOf<String>()
    val categories = mutableListOf<String>()
    val dataTypes = mutableListOf<IfwMimeTypeEntry>()
    val mimeGroups = mutableListOf<String>()
    val schemes = mutableListOf<String>()
    val schemeSpecificParts = mutableListOf<IfwPatternMatcher>()
    val authorities = mutableListOf<IfwAuthorityEntry>()
    val paths = mutableListOf<IfwPatternMatcher>()
    var extras: PersistableBundle? = null

    val outerDepth = parser.depth
    while (true) {
        when (parser.next()) {
            XmlPullParser.START_TAG -> {
                when (parser.name) {
                    TAG_ACTION -> parser.getAttributeValue(null, ATTR_NAME)?.let(actions::add)
                    TAG_INTENT_CATEGORY -> parser.getAttributeValue(null, ATTR_NAME)?.let(categories::add)
                    TAG_INTENT_STATIC_TYPE -> parser.getAttributeValue(null, ATTR_NAME)?.let { value ->
                        dataTypes += IfwMimeTypeEntry(value = value, kind = IfwMimeTypeKind.STATIC)
                    }
                    TAG_INTENT_TYPE -> parser.getAttributeValue(null, ATTR_NAME)?.let { value ->
                        dataTypes += IfwMimeTypeEntry(value = value, kind = IfwMimeTypeKind.DYNAMIC)
                    }
                    TAG_INTENT_GROUP -> parser.getAttributeValue(null, ATTR_NAME)?.let(mimeGroups::add)
                    TAG_SCHEME -> parser.getAttributeValue(null, ATTR_NAME)?.let(schemes::add)
                    TAG_INTENT_SSP -> parsePatternMatcher(parser)?.let(schemeSpecificParts::add)
                    TAG_INTENT_AUTH -> {
                        val host = parser.getAttributeValue(null, ATTR_HOST)
                        if (host != null) {
                            authorities += IfwAuthorityEntry(
                                host = host,
                                port = parser.getAttributeValue(null, ATTR_PORT),
                            )
                        }
                    }
                    TAG_PATH -> parsePatternMatcher(parser)?.let(paths::add)
                    TAG_INTENT_EXTRAS -> {
                        extras = restorePersistableBundle(parser)
                        continue
                    }
                }
                skipCurrentTag(parser)
            }

            XmlPullParser.END_TAG -> if (parser.depth == outerDepth && parser.name == TAG_INTENT_FILTER) {
                break
            }
        }
    }

    return IfwIntentFilter(
        actions = actions,
        categories = categories,
        dataTypes = dataTypes,
        mimeGroups = mimeGroups,
        schemes = schemes,
        schemeSpecificParts = schemeSpecificParts,
        authorities = authorities,
        paths = paths,
        extras = extras,
    )
}

internal fun serializeIntentFilter(
    serializer: XmlSerializer,
    intentFilter: IfwIntentFilter,
) {
    serializer.startTag(null, TAG_INTENT_FILTER)

    intentFilter.actions.forEach { action ->
        serializer.startTag(null, TAG_ACTION)
        serializer.attribute(null, ATTR_NAME, action)
        serializer.endTag(null, TAG_ACTION)
    }

    intentFilter.categories.forEach { category ->
        serializer.startTag(null, TAG_INTENT_CATEGORY)
        serializer.attribute(null, ATTR_NAME, category)
        serializer.endTag(null, TAG_INTENT_CATEGORY)
    }

    intentFilter.dataTypes.forEach { entry ->
        serializer.startTag(null, entry.kind.xmlTag)
        serializer.attribute(null, ATTR_NAME, normalizeMimeTypeForXml(entry.value))
        serializer.endTag(null, entry.kind.xmlTag)
    }

    intentFilter.mimeGroups.forEach { group ->
        serializer.startTag(null, TAG_INTENT_GROUP)
        serializer.attribute(null, ATTR_NAME, group)
        serializer.endTag(null, TAG_INTENT_GROUP)
    }

    intentFilter.schemes.forEach { scheme ->
        serializer.startTag(null, TAG_SCHEME)
        serializer.attribute(null, ATTR_NAME, scheme)
        serializer.endTag(null, TAG_SCHEME)
    }

    intentFilter.schemeSpecificParts.forEach { matcher ->
        serializer.startTag(null, TAG_INTENT_SSP)
        serializer.attribute(null, matcher.type.xmlAttribute, matcher.value)
        serializer.endTag(null, TAG_INTENT_SSP)
    }

    intentFilter.authorities.forEach { authority ->
        serializer.startTag(null, TAG_INTENT_AUTH)
        serializer.attribute(null, ATTR_HOST, authority.host)
        authority.port?.let { port -> serializer.attribute(null, ATTR_PORT, port) }
        serializer.endTag(null, TAG_INTENT_AUTH)
    }

    intentFilter.paths.forEach { matcher ->
        serializer.startTag(null, TAG_PATH)
        serializer.attribute(null, matcher.type.xmlAttribute, matcher.value)
        serializer.endTag(null, TAG_PATH)
    }

    intentFilter.extras?.let { extras ->
        serializer.startTag(null, TAG_INTENT_EXTRAS)
        savePersistableBundle(extras, serializer)
        serializer.endTag(null, TAG_INTENT_EXTRAS)
    }

    serializer.endTag(null, TAG_INTENT_FILTER)
}

private fun parsePatternMatcher(parser: XmlPullParser): IfwPatternMatcher? = when {
    parser.getAttributeValue(null, ATTR_LITERAL) != null -> IfwPatternMatcher(
        value = requireNotNull(parser.getAttributeValue(null, ATTR_LITERAL)),
        type = IfwPatternMatcherType.LITERAL,
    )
    parser.getAttributeValue(null, ATTR_PREFIX) != null -> IfwPatternMatcher(
        value = requireNotNull(parser.getAttributeValue(null, ATTR_PREFIX)),
        type = IfwPatternMatcherType.PREFIX,
    )
    parser.getAttributeValue(null, ATTR_SGLOB) != null -> IfwPatternMatcher(
        value = requireNotNull(parser.getAttributeValue(null, ATTR_SGLOB)),
        type = IfwPatternMatcherType.SIMPLE_GLOB,
    )
    parser.getAttributeValue(null, ATTR_AGLOB) != null -> IfwPatternMatcher(
        value = requireNotNull(parser.getAttributeValue(null, ATTR_AGLOB)),
        type = IfwPatternMatcherType.ADVANCED_GLOB,
    )
    parser.getAttributeValue(null, ATTR_SUFFIX) != null -> IfwPatternMatcher(
        value = requireNotNull(parser.getAttributeValue(null, ATTR_SUFFIX)),
        type = IfwPatternMatcherType.SUFFIX,
    )
    else -> null
}

private fun normalizeMimeTypeForXml(type: String): String = if ('/' in type) type else "$type/*"

private fun restorePersistableBundle(parser: XmlPullParser): PersistableBundle {
    val method = PersistableBundle::class.java.getMethod("restoreFromXml", XmlPullParser::class.java)
    return method.invoke(null, parser) as PersistableBundle
}

private fun savePersistableBundle(
    extras: PersistableBundle,
    serializer: XmlSerializer,
) {
    val method = PersistableBundle::class.java.getMethod("saveToXml", XmlSerializer::class.java)
    method.invoke(extras, serializer)
}

internal fun skipCurrentTag(parser: XmlPullParser) {
    var depth = 1
    while (depth > 0) {
        when (parser.next()) {
            XmlPullParser.START_TAG -> depth++
            XmlPullParser.END_TAG -> depth--
        }
    }
}
