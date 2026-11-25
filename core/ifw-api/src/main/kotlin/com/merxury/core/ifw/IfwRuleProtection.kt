// Copyright 2026 Blocker
// SPDX-License-Identifier: Apache-2.0

package com.merxury.core.ifw

import com.merxury.blocker.core.model.component.ComponentId
import com.merxury.blocker.core.model.ifw.IfwFailure
import com.merxury.blocker.core.model.ifw.IfwResult
import com.merxury.core.ifw.model.IfwComponentType
import com.merxury.core.ifw.model.IfwFilter
import com.merxury.core.ifw.model.IfwRule
import com.merxury.core.ifw.xml.IfwXmlConstants.ATTR_BLOCK
import com.merxury.core.ifw.xml.IfwXmlConstants.ATTR_NAME
import com.merxury.core.ifw.xml.IfwXmlConstants.TAG_COMPONENT_FILTER
import com.merxury.core.ifw.xml.IfwXmlConstants.TAG_RULES
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserException
import org.xmlpull.v1.XmlPullParserFactory
import timber.log.Timber
import java.io.StringReader

/** A firewall file can select components of any package, regardless of its name. */
internal class IfwRuleProtection(private val appPackageName: String) {
    fun allowsBlocking(packageName: String): Boolean = packageName != appPackageName

    fun blocksOwnComponent(rule: IfwRule): Boolean = rule.block &&
        rule.filters.filterIsInstance<IfwFilter.ComponentFilter>().any { isOwnSelector(it.name) }

    /** Checks raw restores while allowing filters outside Blocker's rule model. */
    fun checkXml(xml: String, name: String): IfwResult<Unit> = try {
        checkSelectors(xml)
    } catch (e: XmlPullParserException) {
        Timber.w(e, "Cannot verify IFW rule selectors in $name")
        IfwResult.Failure(IfwFailure.Unreadable(name))
    }

    private fun checkSelectors(xml: String): IfwResult<Unit> {
        val factory = XmlPullParserFactory.newInstance().apply { isNamespaceAware = true }
        val parser = factory.newPullParser().apply { setInput(StringReader(xml)) }
        var rootSeen = false
        var blocking = false
        while (parser.next() != XmlPullParser.END_DOCUMENT) {
            if (parser.eventType != XmlPullParser.START_TAG) continue
            when (parser.depth) {
                1 -> {
                    if (rootSeen || parser.name != TAG_RULES) throw XmlPullParserException("Expected one rules element")
                    rootSeen = true
                }

                2 -> blocking = IfwComponentType.fromXmlTag(parser.name) != null &&
                    parser.getAttributeValue(null, ATTR_BLOCK).toBoolean()

                3 -> if (blocking && parser.name == TAG_COMPONENT_FILTER && isOwnSelector(parser.getAttributeValue(null, ATTR_NAME))) {
                    return IfwResult.Failure(IfwFailure.ProtectedComponent)
                }
            }
        }
        if (!rootSeen || parser.depth != 0) throw XmlPullParserException("Incomplete rules document")
        return IfwResult.Success(Unit)
    }

    private fun isOwnSelector(name: String?): Boolean = name?.let(ComponentId::unflatten)?.packageName == appPackageName
}
