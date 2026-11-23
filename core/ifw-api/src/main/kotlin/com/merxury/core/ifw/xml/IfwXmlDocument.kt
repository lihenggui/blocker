/*
 * Copyright 2026 Blocker
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

import com.merxury.core.ifw.xml.IfwXmlConstants.TAG_RULES
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserException
import org.xmlpull.v1.XmlPullParserFactory
import java.io.StringReader

/**
 * Checks that [xml] is a complete, well-formed XML document whose root element is `<rules>`, the
 * element Android's Intent Firewall reads a rule file from, without requiring that
 * [IfwXmlDeserializer] can express every filter in it. For a file written as it is, such as a
 * user's own rule file, whose filters Android may accept even where Blocker's model cannot.
 *
 * @throws IfwXmlParseException if [xml] is not well-formed, ends early, or has another root element
 */
fun requireIfwRulesDocument(xml: String) {
    val parser = XmlPullParserFactory.newInstance().newPullParser()
    parser.setInput(StringReader(xml))
    var root: String? = null
    try {
        var event = parser.next()
        while (event != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG && root == null) root = parser.name
            event = parser.next()
        }
    } catch (e: XmlPullParserException) {
        throw IfwXmlParseException("Not well-formed at line ${parser.lineNumber}: ${e.message}")
    }
    if (parser.depth != 0) throw IfwXmlParseException("Unexpected end of document at line ${parser.lineNumber}")
    if (root != TAG_RULES) throw IfwXmlParseException("The root element is <$root>, not <$TAG_RULES>")
}
