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
 * Represents the string matching strategies supported by the Android Intent Firewall.
 *
 * Each mode corresponds to an XML attribute in the AOSP IntentFirewall's
 * [StringFilter](https://cs.android.com/android/platform/superproject/+/main:frameworks/base/services/core/java/com/android/server/firewall/StringFilter.java).
 *
 * Example XML usage:
 * ```xml
 * <action equals="android.intent.action.VIEW" />
 * <component-package startsWith="com.example" />
 * <path regex=".*\.apk" />
 * ```
 */
sealed interface StringMatcher {
    /** The raw string value used for matching. */
    val value: String

    /**
     * Exact string equality match.
     * Corresponds to the `equals` XML attribute.
     *
     * @property value the exact string to match against
     */
    data class Equals(override val value: String) : StringMatcher

    /**
     * Prefix match — checks if the target string starts with [value].
     * Corresponds to the `startsWith` XML attribute.
     *
     * @property value the prefix to match against
     */
    data class StartsWith(override val value: String) : StringMatcher

    /**
     * Substring match — checks if the target string contains [value].
     * Corresponds to the `contains` XML attribute.
     *
     * @property value the substring to search for
     */
    data class Contains(override val value: String) : StringMatcher

    /**
     * Simple glob-style pattern match.
     * Supports `*` (match any sequence) and other basic glob operators.
     * Corresponds to the `pattern` XML attribute.
     *
     * @property value the glob pattern
     */
    data class Pattern(override val value: String) : StringMatcher

    /**
     * Regular expression match.
     * Uses Java [java.util.regex.Pattern] syntax.
     * Corresponds to the `regex` XML attribute.
     *
     * @property value the regular expression
     */
    data class Regex(override val value: String) : StringMatcher

    /**
     * Null/non-null check — matches when the target value is null (or non-null).
     * Corresponds to the `isNull` XML attribute.
     *
     * When [isNull] is `true`, matches if the target is null.
     * When [isNull] is `false`, matches if the target is non-null.
     *
     * @property isNull whether to match null (`true`) or non-null (`false`)
     */
    data class IsNull(val isNull: Boolean) : StringMatcher {
        override val value: String get() = isNull.toString()
    }
}
