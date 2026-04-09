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

import android.os.PatternMatcher
import android.os.PersistableBundle

/**
 * A snapshot of the framework's `<intent-filter>` representation used by IFW rules.
 *
 * Unlike the top-level [IfwFilter] tree, this model mirrors
 * `android.content.IntentFilter.readFromXml()/writeToXml()` semantics. The tag names
 * and attributes here intentionally follow framework XML rather than manifest XML.
 */
data class IfwIntentFilter(
    val actions: List<String> = emptyList(),
    val categories: List<String> = emptyList(),
    val dataTypes: List<IfwMimeTypeEntry> = emptyList(),
    val mimeGroups: List<String> = emptyList(),
    val schemes: List<String> = emptyList(),
    val schemeSpecificParts: List<IfwPatternMatcher> = emptyList(),
    val authorities: List<IfwAuthorityEntry> = emptyList(),
    val paths: List<IfwPatternMatcher> = emptyList(),
    val extras: PersistableBundle? = null,
)

data class IfwMimeTypeEntry(
    val value: String,
    val kind: IfwMimeTypeKind,
)

data class IfwPatternMatcher(
    val value: String,
    val type: IfwPatternMatcherType,
)

data class IfwAuthorityEntry(
    val host: String,
    val port: String? = null,
)

enum class IfwMimeTypeKind(
    val xmlTag: String,
) {
    STATIC("staticType"),
    DYNAMIC("type"),
}

enum class IfwPatternMatcherType(
    val xmlAttribute: String,
    val platformType: Int,
) {
    LITERAL("literal", PatternMatcher.PATTERN_LITERAL),
    PREFIX("prefix", PatternMatcher.PATTERN_PREFIX),
    SIMPLE_GLOB("sglob", PatternMatcher.PATTERN_SIMPLE_GLOB),
    ADVANCED_GLOB("aglob", PATTERN_ADVANCED_GLOB_COMPAT),
    SUFFIX("suffix", PATTERN_SUFFIX_COMPAT),
    ;

    companion object {
        fun fromPlatformType(type: Int): IfwPatternMatcherType = entries.find { it.platformType == type }
            ?: throw IllegalArgumentException("Unsupported PatternMatcher type: $type")
    }
}

// Matches the framework values in android.os.PatternMatcher on API 26+ and 31+.
private const val PATTERN_ADVANCED_GLOB_COMPAT = 3
private const val PATTERN_SUFFIX_COMPAT = 4

fun IfwIntentFilter.toSummary(): String {
    val parts = buildList {
        if (actions.isNotEmpty()) add("action=${actions.joinToString()}")
        if (categories.isNotEmpty()) add("cat=${categories.joinToString()}")
        if (dataTypes.isNotEmpty()) {
            add(
                dataTypes.joinToString { entry ->
                    "${entry.kind.xmlTag}=${entry.value}"
                },
            )
        }
        if (schemes.isNotEmpty()) add("scheme=${schemes.joinToString()}")
        if (authorities.isNotEmpty()) {
            add(
                "auth=" + authorities.joinToString { authority ->
                    authority.port?.let { port -> "${authority.host}:$port" } ?: authority.host
                },
            )
        }
        if (paths.isNotEmpty()) add("path=${paths.joinToString { it.toSummary() }}")
    }
    return parts.joinToString(" | ")
}

private fun IfwPatternMatcher.toSummary(): String = "${type.xmlAttribute}=\"$value\""
