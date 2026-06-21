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

/**
 * Constants for all XML element and attribute names used by the Android Intent Firewall.
 *
 * These values must exactly match the strings used in the AOSP IntentFirewall implementation.
 * Changing them would produce XML that Android's framework cannot parse.
 *
 * @see [AOSP IntentFirewall.java](https://cs.android.com/android/platform/superproject/+/main:frameworks/base/services/core/java/com/android/server/firewall/IntentFirewall.java)
 */
internal object IfwXmlConstants {
    // Root element
    const val TAG_RULES = "rules"

    // Component type elements
    const val TAG_ACTIVITY = "activity"
    const val TAG_BROADCAST = "broadcast"
    const val TAG_SERVICE = "service"

    // Component type attributes
    const val ATTR_BLOCK = "block"
    const val ATTR_LOG = "log"

    // Composite filter elements
    const val TAG_AND = "and"
    const val TAG_OR = "or"
    const val TAG_NOT = "not"
    const val TAG_INTENT_FILTER = "intent-filter"

    // String-based filter elements
    const val TAG_ACTION = "action"
    const val TAG_COMPONENT = "component"
    const val TAG_COMPONENT_NAME = "component-name"
    const val TAG_COMPONENT_PACKAGE = "component-package"
    const val TAG_DATA = "data"
    const val TAG_HOST = "host"
    const val TAG_MIME_TYPE = "mime-type"
    const val TAG_SCHEME = "scheme"
    const val TAG_SCHEME_SPECIFIC_PART = "scheme-specific-part"
    const val TAG_PATH = "path"

    // Non-string filter elements
    const val TAG_CATEGORY = "category"
    const val TAG_PORT = "port"
    const val TAG_SENDER = "sender"
    const val TAG_SENDER_PACKAGE = "sender-package"
    const val TAG_SENDER_PERMISSION = "sender-permission"

    // Legacy component-filter element
    const val TAG_COMPONENT_FILTER = "component-filter"

    // StringMatcher attributes
    const val ATTR_EQUALS = "equals"
    const val ATTR_STARTS_WITH = "startsWith"
    const val ATTR_CONTAINS = "contains"
    const val ATTR_PATTERN = "pattern"
    const val ATTR_REGEX = "regex"
    const val ATTR_IS_NULL = "isNull"

    // Common attributes
    const val ATTR_NAME = "name"
    const val ATTR_TYPE = "type"

    // Port attributes
    const val ATTR_MIN = "min"
    const val ATTR_MAX = "max"

    // IntentFilter.readFromXml / writeToXml tags
    const val TAG_INTENT_CATEGORY = "cat"
    const val TAG_INTENT_STATIC_TYPE = "staticType"
    const val TAG_INTENT_TYPE = "type"
    const val TAG_INTENT_GROUP = "group"
    const val TAG_INTENT_SSP = "ssp"
    const val TAG_INTENT_AUTH = "auth"
    const val TAG_INTENT_EXTRAS = "extras"

    // IntentFilter.readFromXml / writeToXml attributes
    const val ATTR_HOST = "host"
    const val ATTR_PORT = "port"
    const val ATTR_LITERAL = "literal"
    const val ATTR_PREFIX = "prefix"
    const val ATTR_SGLOB = "sglob"
    const val ATTR_AGLOB = "aglob"
    const val ATTR_SUFFIX = "suffix"
}
