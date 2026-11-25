// Copyright 2026 Blocker
// SPDX-License-Identifier: Apache-2.0

package com.merxury.core.ifw

import com.merxury.blocker.core.model.ifw.IfwFailure
import com.merxury.blocker.core.model.ifw.IfwResult
import com.merxury.core.ifw.model.IfwFilter
import com.merxury.core.ifw.model.IfwRule
import com.merxury.core.ifw.model.StringMatcher
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserException
import org.xmlpull.v1.XmlPullParserFactory
import org.xmlpull.v1.XmlSerializer
import timber.log.Timber
import java.io.StringReader
import java.io.StringWriter

private val ruleTags = setOf("activity", "service", "broadcast")

/** A firewall file can select components of any package, regardless of its name. */
internal class IfwRuleProtection(private val appPackageName: String) {
    fun allowsBlocking(packageName: String): Boolean = packageName != appPackageName

    // Android can pass a null component for an implicit broadcast. A blocking intent selector
    // may run only when the firewall can establish it is for a different package.
    private val otherPackage = IfwFilter.And(
        listOf(
            IfwFilter.ComponentPackage(StringMatcher.IsNull(false)),
            IfwFilter.Not(IfwFilter.ComponentPackage(StringMatcher.Equals(appPackageName))),
        ),
    )

    fun protect(rule: IfwRule): IfwRule? = when {
        !rule.block -> rule
        rule.filters.filterIsInstance<IfwFilter.ComponentFilter>().any { isOwnSelector(it.name) } -> null
        rule.intentFilters.isNotEmpty() && otherPackage !in rule.filters -> rule.copy(filters = rule.filters + otherPackage)
        else -> rule
    }

    /**
     * Checks raw restores without interpreting filters Blocker cannot edit. Safe files with only
     * exact component selectors are kept byte for byte.
     */
    fun protectXml(xml: String, name: String): IfwResult<String> = try {
        checkSelectors(xml)
    } catch (e: XmlPullParserException) {
        Timber.w(e, "Cannot verify IFW rule selectors in $name")
        IfwResult.Failure(IfwFailure.Unreadable(name))
    }

    private fun checkSelectors(xml: String): IfwResult<String> {
        val factory = XmlPullParserFactory.newInstance().apply { isNamespaceAware = true }
        val parser = factory.newPullParser().apply { setInput(StringReader(xml)) }
        val output = StringWriter()
        val writer = factory.newSerializer().apply { setOutput(output) }
        var rootSeen = false
        var blocking = false
        var hasIntentSelector = false
        var changed = false
        while (parser.nextToken() != XmlPullParser.END_DOCUMENT) {
            when (parser.eventType) {
                XmlPullParser.START_TAG -> {
                    when (parser.depth) {
                        1 -> {
                            if (rootSeen || parser.name != "rules") throw XmlPullParserException("Expected one rules element")
                            rootSeen = true
                        }

                        2 -> {
                            blocking = parser.name in ruleTags && parser.hasAttribute("block") { it.toBoolean() }
                            hasIntentSelector = false
                        }

                        3 -> if (blocking) {
                            if (parser.name == "component-filter" && parser.hasAttribute("name", ::isOwnSelector)) {
                                return IfwResult.Failure(IfwFailure.ProtectedComponent)
                            }
                            if (parser.name == "intent-filter") hasIntentSelector = true
                        }
                    }
                    for (index in parser.getNamespaceCount(parser.depth - 1) until parser.getNamespaceCount(parser.depth)) {
                        writer.setPrefix(parser.getNamespacePrefix(index).orEmpty(), parser.getNamespaceUri(index))
                    }
                    writer.startTag(parser.namespace, parser.name)
                    for (index in 0 until parser.attributeCount) {
                        writer.attribute(parser.getAttributeNamespace(index), parser.getAttributeName(index), parser.getAttributeValue(index))
                    }
                }

                XmlPullParser.END_TAG -> {
                    if (parser.depth == 2 && hasIntentSelector) {
                        writer.writePackageExclusion()
                        changed = true
                    }
                    writer.endTag(parser.namespace, parser.name)
                }

                XmlPullParser.TEXT, XmlPullParser.CDSECT, XmlPullParser.IGNORABLE_WHITESPACE -> writer.text(parser.text)

                XmlPullParser.COMMENT -> writer.comment(parser.text)

                XmlPullParser.PROCESSING_INSTRUCTION -> writer.processingInstruction(parser.text)

                XmlPullParser.ENTITY_REF -> writer.text(parser.text ?: throw XmlPullParserException("Unresolved XML entity"))

                XmlPullParser.DOCDECL -> throw XmlPullParserException("Rule files cannot declare entities")
            }
        }
        if (!rootSeen || parser.depth != 0) throw XmlPullParserException("Incomplete rules document")
        writer.flush()
        return IfwResult.Success(if (changed) output.toString() else xml)
    }

    private inline fun XmlPullParser.hasAttribute(name: String, matches: (String) -> Boolean): Boolean = (0 until attributeCount).any { getAttributeName(it) == name && matches(getAttributeValue(it)) }

    private fun isOwnSelector(name: String): Boolean = name.substringBefore('/') == appPackageName && '/' in name

    private fun XmlSerializer.writePackageExclusion() {
        startTag(null, "and")
        startTag(null, "component-package").attribute(null, "isNull", "false").endTag(null, "component-package")
        startTag(null, "not")
        startTag(null, "component-package").attribute(null, "equals", appPackageName).endTag(null, "component-package")
        endTag(null, "not")
        endTag(null, "and")
    }
}
