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

package com.merxury.core.ifw.model

/**
 * A type-safe representation of all filter types supported by the Android Intent Firewall.
 *
 * Filters form a tree structure: leaf filters match against specific intent properties,
 * while composite filters ([And], [Or], [Not]) combine other filters using boolean logic.
 *
 * This hierarchy mirrors the AOSP IntentFirewall's FilterFactory registry:
 * [IntentFirewall.java](https://cs.android.com/android/platform/superproject/+/main:frameworks/base/services/core/java/com/android/server/firewall/IntentFirewall.java)
 *
 * ## Example: Block BOOT_COMPLETED from non-system senders
 * ```kotlin
 * val filter = IfwFilter.And(
 *     filters = listOf(
 *         IfwFilter.Action(StringMatcher.Equals("android.intent.action.BOOT_COMPLETED")),
 *         IfwFilter.Not(IfwFilter.Sender(SenderType.SYSTEM)),
 *     )
 * )
 * ```
 */
sealed interface IfwFilter {

    // ── Composite Filters ──────────────────────────────────────────────

    /**
     * Matches when **all** child filters match.
     * Corresponds to the `<and>` XML element.
     *
     * @property filters the child filters that must all match
     */
    data class And(val filters: List<IfwFilter>) : IfwFilter

    /**
     * Matches when **any** child filter matches.
     * Corresponds to the `<or>` XML element.
     *
     * @property filters the child filters, at least one of which must match
     */
    data class Or(val filters: List<IfwFilter>) : IfwFilter

    /**
     * Matches when the child filter does **not** match.
     * Corresponds to the `<not>` XML element.
     *
     * @property filter the child filter to negate
     */
    data class Not(val filter: IfwFilter) : IfwFilter

    // ── String-Based Filters ───────────────────────────────────────────

    /**
     * Matches the intent's action string.
     * Corresponds to the `<action>` XML element.
     *
     * @property matcher the string matching strategy
     */
    data class Action(val matcher: StringMatcher) : IfwFilter

    /**
     * Matches the fully qualified component name (`package/class`).
     * Corresponds to the `<component>` XML element.
     *
     * @property matcher the string matching strategy
     */
    data class Component(val matcher: StringMatcher) : IfwFilter

    /**
     * Matches the component's class name only (without package prefix).
     * Corresponds to the `<component-name>` XML element.
     *
     * @property matcher the string matching strategy
     */
    data class ComponentName(val matcher: StringMatcher) : IfwFilter

    /**
     * Matches the component's package name.
     * Corresponds to the `<component-package>` XML element.
     *
     * @property matcher the string matching strategy
     */
    data class ComponentPackage(val matcher: StringMatcher) : IfwFilter

    /**
     * Matches the intent's data URI (as a full string).
     * Corresponds to the `<data>` XML element.
     *
     * @property matcher the string matching strategy
     */
    data class Data(val matcher: StringMatcher) : IfwFilter

    /**
     * Matches the data URI's host component.
     * Corresponds to the `<host>` XML element.
     *
     * @property matcher the string matching strategy
     */
    data class Host(val matcher: StringMatcher) : IfwFilter

    /**
     * Matches the intent's resolved MIME type.
     * Corresponds to the `<mime-type>` XML element.
     *
     * @property matcher the string matching strategy
     */
    data class MimeType(val matcher: StringMatcher) : IfwFilter

    /**
     * Matches the data URI's scheme (e.g., "http", "content", "file").
     * Corresponds to the `<scheme>` XML element.
     *
     * @property matcher the string matching strategy
     */
    data class Scheme(val matcher: StringMatcher) : IfwFilter

    /**
     * Matches the data URI's scheme-specific part.
     * Corresponds to the `<scheme-specific-part>` XML element.
     *
     * @property matcher the string matching strategy
     */
    data class SchemeSpecificPart(val matcher: StringMatcher) : IfwFilter

    /**
     * Matches the data URI's path component.
     * Corresponds to the `<path>` XML element.
     *
     * @property matcher the string matching strategy
     */
    data class Path(val matcher: StringMatcher) : IfwFilter

    // ── Non-String Filters ─────────────────────────────────────────────

    /**
     * Matches an intent category.
     * Corresponds to the `<category>` XML element.
     *
     * @property name the exact category string (e.g., "android.intent.category.LAUNCHER")
     */
    data class Category(val name: String) : IfwFilter

    /**
     * Matches the data URI's port number or range.
     * Corresponds to the `<port>` XML element.
     *
     * Use either [equals] for an exact port, or [min]/[max] for a range.
     * Using [equals] together with [min]/[max] is invalid per AOSP.
     *
     * @property equals exact port number to match, or `null` for range mode
     * @property min minimum port (inclusive) for range matching, or `null`
     * @property max maximum port (inclusive) for range matching, or `null`
     */
    data class Port(
        val equals: Int? = null,
        val min: Int? = null,
        val max: Int? = null,
    ) : IfwFilter {
        init {
            require(
                (equals != null && min == null && max == null) ||
                    (equals == null),
            ) { "Cannot use 'equals' together with 'min'/'max'" }
        }
    }

    /**
     * Matches the identity of the intent's sender process.
     * Corresponds to the `<sender>` XML element.
     *
     * @property type the sender identity type to match
     * @see SenderType
     */
    data class Sender(val type: SenderType) : IfwFilter

    /**
     * Matches the package name of the intent's sender.
     * Corresponds to the `<sender-package>` XML element.
     *
     * @property name the sender's package name
     */
    data class SenderPackage(val name: String) : IfwFilter

    /**
     * Matches a permission held by the intent's sender.
     * Corresponds to the `<sender-permission>` XML element.
     *
     * @property name the Android permission string
     */
    data class SenderPermission(val name: String) : IfwFilter

    // ── Legacy Component Filter ────────────────────────────────────────

    /**
     * Matches an exact component by its flattened name (`package/class`).
     * Corresponds to the `<component-filter>` XML element.
     *
     * This is the original IFW filter type used by Blocker for component blocking.
     * Unlike [Component] which supports flexible [StringMatcher] modes,
     * this filter only does exact matching via the `name` attribute.
     *
     * @property name the flattened component name (e.g., "com.example/com.example.MainActivity")
     */
    data class ComponentFilter(val name: String) : IfwFilter
}

/**
 * Sender identity types for the [IfwFilter.Sender] filter.
 *
 * @property xmlValue the string value used in the `type` XML attribute
 */
enum class SenderType(val xmlValue: String) {
    /** Matches if the sender shares the same signing certificate. */
    SIGNATURE("signature"),

    /** Matches if the sender is a privileged/system process. */
    SYSTEM("system"),

    /** Matches if the sender is either system or shares the signature. */
    SYSTEM_OR_SIGNATURE("system|signature"),

    /** Matches if the sender shares the same user ID. */
    USER_ID("userId"),
    ;

    companion object {
        /**
         * Finds the [SenderType] for the given XML attribute value.
         *
         * @param value the XML `type` attribute value
         * @return the matching [SenderType]
         * @throws IllegalArgumentException if the value is not recognized
         */
        fun fromXmlValue(value: String): SenderType = entries.find { it.xmlValue == value }
            ?: throw IllegalArgumentException("Unknown sender type: $value")
    }
}
