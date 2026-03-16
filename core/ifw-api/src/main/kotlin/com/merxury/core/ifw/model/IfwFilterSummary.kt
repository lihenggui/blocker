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
 * Generates a human-readable summary of an IFW filter.
 */
fun IfwFilter.toSummary(): String = when (this) {
    is IfwFilter.ComponentFilter -> name.substringAfter("/")
    is IfwFilter.Action -> "action ${matcher.toSummary()}"
    is IfwFilter.Component -> "component ${matcher.toSummary()}"
    is IfwFilter.ComponentName -> "class name ${matcher.toSummary()}"
    is IfwFilter.ComponentPackage -> "package ${matcher.toSummary()}"
    is IfwFilter.Data -> "data ${matcher.toSummary()}"
    is IfwFilter.Host -> "host ${matcher.toSummary()}"
    is IfwFilter.MimeType -> "MIME type ${matcher.toSummary()}"
    is IfwFilter.Scheme -> "scheme ${matcher.toSummary()}"
    is IfwFilter.SchemeSpecificPart -> "SSP ${matcher.toSummary()}"
    is IfwFilter.Path -> "path ${matcher.toSummary()}"
    is IfwFilter.Category -> "category: $name"
    is IfwFilter.Port -> when {
        equals != null -> "port = $equals"
        else -> "port $min..$max"
    }
    is IfwFilter.Sender -> "sender: ${type.xmlValue}"
    is IfwFilter.SenderPackage -> "sender package: $name"
    is IfwFilter.SenderPermission -> "sender permission: $name"
    is IfwFilter.And -> filters.joinToString(" AND ") { it.toSummary() }
    is IfwFilter.Or -> filters.joinToString(" OR ") { it.toSummary() }
    is IfwFilter.Not -> "NOT (${filter.toSummary()})"
}

private fun StringMatcher.toSummary(): String = when (this) {
    is StringMatcher.Equals -> "= \"$value\""
    is StringMatcher.StartsWith -> "starts with \"$value\""
    is StringMatcher.Contains -> "contains \"$value\""
    is StringMatcher.Pattern -> "matches pattern \"$value\""
    is StringMatcher.Regex -> "matches regex \"$value\""
    is StringMatcher.IsNull -> if (isNull) "is null" else "is not null"
}
