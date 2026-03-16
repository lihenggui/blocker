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

package com.merxury.core.ifw.editor

import com.merxury.core.ifw.model.IfwFilter
import com.merxury.core.ifw.model.SenderType
import com.merxury.core.ifw.model.StringMatcher

fun List<IfwFilter>.toEditorRootGroup(
    defaultMode: IfwEditorGroupMode = IfwEditorGroupMode.ANY,
): IfwEditorNode.Group? {
    if (isEmpty()) return IfwEditorNode.Group()
    if (size == 1) {
        val node = first().toEditorNodeOrNull() ?: return null
        return node.asRootGroup()
    }
    val nodes = map { filter -> filter.toEditorNodeOrNull() ?: return null }
    return IfwEditorNode.Group(
        mode = defaultMode,
        children = nodes,
    )
}

fun IfwEditorNode.Group.toTopLevelFilters(): List<IfwFilter> = when {
    children.isEmpty() -> emptyList()
    else -> listOfNotNull(toIfwFilterOrNull())
}

fun IfwEditorNode.toIfwFilterOrNull(): IfwFilter? = when (this) {
    is IfwEditorNode.Group -> {
        val childFilters = children.mapNotNull { child -> child.toIfwFilterOrNull() }
        if (childFilters.isEmpty()) return null
        val base = when {
            childFilters.size == 1 -> childFilters.first()
            mode == IfwEditorGroupMode.ALL -> IfwFilter.And(childFilters)
            else -> IfwFilter.Or(childFilters)
        }
        if (excluded) {
            IfwFilter.Not(base)
        } else {
            base
        }
    }

    is IfwEditorNode.Condition -> {
        val base = when (kind) {
            IfwEditorConditionKind.ACTION -> IfwFilter.Action(matcherMode.toStringMatcher(value))
            IfwEditorConditionKind.CATEGORY -> {
                if (value.isBlank()) return null
                IfwFilter.Category(value)
            }
            IfwEditorConditionKind.CALLER_TYPE -> IfwFilter.Sender(senderType)
            IfwEditorConditionKind.CALLER_PACKAGE -> {
                if (value.isBlank()) return null
                IfwFilter.SenderPackage(value)
            }
            IfwEditorConditionKind.CALLER_PERMISSION -> {
                if (value.isBlank()) return null
                IfwFilter.SenderPermission(value)
            }
            IfwEditorConditionKind.COMPONENT -> IfwFilter.Component(matcherMode.toStringMatcher(value))
            IfwEditorConditionKind.COMPONENT_NAME -> IfwFilter.ComponentName(matcherMode.toStringMatcher(value))
            IfwEditorConditionKind.COMPONENT_PACKAGE -> IfwFilter.ComponentPackage(matcherMode.toStringMatcher(value))
            IfwEditorConditionKind.COMPONENT_FILTER -> {
                if (value.isBlank()) return null
                IfwFilter.ComponentFilter(value)
            }
            IfwEditorConditionKind.HOST -> IfwFilter.Host(matcherMode.toStringMatcher(value))
            IfwEditorConditionKind.SCHEME -> IfwFilter.Scheme(matcherMode.toStringMatcher(value))
            IfwEditorConditionKind.SCHEME_SPECIFIC_PART -> {
                IfwFilter.SchemeSpecificPart(matcherMode.toStringMatcher(value))
            }
            IfwEditorConditionKind.PATH -> IfwFilter.Path(matcherMode.toStringMatcher(value))
            IfwEditorConditionKind.DATA -> IfwFilter.Data(matcherMode.toStringMatcher(value))
            IfwEditorConditionKind.MIME_TYPE -> IfwFilter.MimeType(matcherMode.toStringMatcher(value))
            IfwEditorConditionKind.PORT -> when (portMode) {
                IfwEditorPortMode.EXACT -> {
                    val port = exactPort ?: return null
                    IfwFilter.Port(equals = port)
                }
                IfwEditorPortMode.RANGE -> {
                    if (minPort == null && maxPort == null) return null
                    IfwFilter.Port(min = minPort, max = maxPort)
                }
            }
        }
        if (excluded) {
            IfwFilter.Not(base)
        } else {
            base
        }
    }
}

fun IfwFilter.toEditorNodeOrNull(): IfwEditorNode? = when (this) {
    is IfwFilter.And -> {
        val children = filters.map { child -> child.toEditorNodeOrNull() ?: return null }
        IfwEditorNode.Group(
            mode = IfwEditorGroupMode.ALL,
            children = children,
        )
    }
    is IfwFilter.Or -> {
        val children = filters.map { child -> child.toEditorNodeOrNull() ?: return null }
        IfwEditorNode.Group(
            mode = IfwEditorGroupMode.ANY,
            children = children,
        )
    }
    is IfwFilter.Not -> {
        val child = filter.toEditorNodeOrNull() ?: return null
        when (child) {
            is IfwEditorNode.Condition -> child.copy(excluded = !child.excluded)
            is IfwEditorNode.Group -> child.copy(excluded = !child.excluded)
        }
    }
    is IfwFilter.Action -> stringCondition(IfwEditorConditionKind.ACTION, matcher)
    is IfwFilter.Category -> IfwEditorNode.Condition(
        kind = IfwEditorConditionKind.CATEGORY,
        value = name,
    )
    is IfwFilter.Sender -> IfwEditorNode.Condition(
        kind = IfwEditorConditionKind.CALLER_TYPE,
        senderType = type,
    )
    is IfwFilter.SenderPackage -> IfwEditorNode.Condition(
        kind = IfwEditorConditionKind.CALLER_PACKAGE,
        value = name,
    )
    is IfwFilter.SenderPermission -> IfwEditorNode.Condition(
        kind = IfwEditorConditionKind.CALLER_PERMISSION,
        value = name,
    )
    is IfwFilter.Component -> stringCondition(IfwEditorConditionKind.COMPONENT, matcher)
    is IfwFilter.ComponentName -> stringCondition(IfwEditorConditionKind.COMPONENT_NAME, matcher)
    is IfwFilter.ComponentPackage -> stringCondition(IfwEditorConditionKind.COMPONENT_PACKAGE, matcher)
    is IfwFilter.ComponentFilter -> IfwEditorNode.Condition(
        kind = IfwEditorConditionKind.COMPONENT_FILTER,
        value = name,
    )
    is IfwFilter.Host -> stringCondition(IfwEditorConditionKind.HOST, matcher)
    is IfwFilter.Scheme -> stringCondition(IfwEditorConditionKind.SCHEME, matcher)
    is IfwFilter.SchemeSpecificPart -> stringCondition(IfwEditorConditionKind.SCHEME_SPECIFIC_PART, matcher)
    is IfwFilter.Path -> stringCondition(IfwEditorConditionKind.PATH, matcher)
    is IfwFilter.Data -> stringCondition(IfwEditorConditionKind.DATA, matcher)
    is IfwFilter.MimeType -> stringCondition(IfwEditorConditionKind.MIME_TYPE, matcher)
    is IfwFilter.Port -> IfwEditorNode.Condition(
        kind = IfwEditorConditionKind.PORT,
        portMode = if (equals != null) IfwEditorPortMode.EXACT else IfwEditorPortMode.RANGE,
        exactPort = equals,
        minPort = min,
        maxPort = max,
    )
}

private fun stringCondition(
    kind: IfwEditorConditionKind,
    matcher: StringMatcher,
): IfwEditorNode.Condition = IfwEditorNode.Condition(
    kind = kind,
    matcherMode = matcher.toEditorMatcherMode(),
    value = matcher.valueOrEmpty(),
)

private fun IfwEditorNode.asRootGroup(): IfwEditorNode.Group = when (this) {
    is IfwEditorNode.Condition -> IfwEditorNode.Group(
        mode = IfwEditorGroupMode.ALL,
        children = listOf(this),
    )
    is IfwEditorNode.Group -> this
}

private fun IfwEditorStringMatcherMode.toStringMatcher(value: String): StringMatcher = when (this) {
    IfwEditorStringMatcherMode.EXACT -> StringMatcher.Equals(value)
    IfwEditorStringMatcherMode.STARTS_WITH -> StringMatcher.StartsWith(value)
    IfwEditorStringMatcherMode.CONTAINS -> StringMatcher.Contains(value)
    IfwEditorStringMatcherMode.PATTERN -> StringMatcher.Pattern(value)
    IfwEditorStringMatcherMode.REGEX -> StringMatcher.Regex(value)
    IfwEditorStringMatcherMode.IS_NULL -> StringMatcher.IsNull(true)
    IfwEditorStringMatcherMode.IS_NOT_NULL -> StringMatcher.IsNull(false)
}

private fun StringMatcher.toEditorMatcherMode(): IfwEditorStringMatcherMode = when (this) {
    is StringMatcher.Equals -> IfwEditorStringMatcherMode.EXACT
    is StringMatcher.StartsWith -> IfwEditorStringMatcherMode.STARTS_WITH
    is StringMatcher.Contains -> IfwEditorStringMatcherMode.CONTAINS
    is StringMatcher.Pattern -> IfwEditorStringMatcherMode.PATTERN
    is StringMatcher.Regex -> IfwEditorStringMatcherMode.REGEX
    is StringMatcher.IsNull -> if (isNull) {
        IfwEditorStringMatcherMode.IS_NULL
    } else {
        IfwEditorStringMatcherMode.IS_NOT_NULL
    }
}

private fun StringMatcher.valueOrEmpty(): String = when (this) {
    is StringMatcher.Equals -> value
    is StringMatcher.StartsWith -> value
    is StringMatcher.Contains -> value
    is StringMatcher.Pattern -> value
    is StringMatcher.Regex -> value
    is StringMatcher.IsNull -> ""
}
