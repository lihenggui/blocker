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

package com.merxury.blocker.feature.ifwrule.impl.model

import com.merxury.core.ifw.model.IfwComponentType
import com.merxury.core.ifw.model.IfwFilter
import com.merxury.core.ifw.model.IfwRule
import com.merxury.core.ifw.model.IfwRules
import com.merxury.core.ifw.model.SenderType
import com.merxury.core.ifw.model.StringMatcher
import java.util.UUID

// ── UI State → IFW ─────────────────────────────────────────

fun RuleEditorUiState.toIfwFilter(): IfwFilter {
    val componentFilter = IfwFilter.ComponentFilter("$packageName/$componentName")
    if (blockMode == BlockMode.ALL) return componentFilter

    val conditionFilters = conditions.flatMap { it.toIfwFilters() }
    if (conditionFilters.isEmpty()) return componentFilter

    val andChildren = when {
        conditionFilters.size == 1 -> listOf(componentFilter, conditionFilters.first())
        combineMode == CombineMode.ALL_MATCH -> listOf(componentFilter) + conditionFilters
        else -> listOf(componentFilter, IfwFilter.Or(conditionFilters))
    }
    return IfwFilter.And(andChildren)
}

private fun ConditionUiState.toIfwFilters(): List<IfwFilter> = when (this) {
    is ConditionUiState.ActionFilter -> listOf(
        IfwFilter.Action(matchMode.toStringMatcher(value)),
    )

    is ConditionUiState.SourceControl -> listOf(option.toIfwFilter())

    is ConditionUiState.CallerApp -> packageNames.map { IfwFilter.SenderPackage(it) }

    is ConditionUiState.CallerPermission -> {
        val filter = IfwFilter.SenderPermission(permission)
        when (mode) {
            PermissionMode.REQUIRE -> listOf(IfwFilter.Not(filter))
            PermissionMode.BLOCK_WITH -> listOf(filter)
        }
    }

    is ConditionUiState.CategoryFilter -> listOf(IfwFilter.Category(name))

    is ConditionUiState.LinkFilter -> buildList {
        if (host.isNotBlank()) add(IfwFilter.Host(StringMatcher.Equals(host)))
        if (path.isNotBlank()) add(IfwFilter.Path(pathMatchMode.toStringMatcher(path)))
        if (scheme.isNotBlank()) add(IfwFilter.Scheme(StringMatcher.Equals(scheme)))
    }

    is ConditionUiState.DataFilter -> listOf(
        IfwFilter.Data(matchMode.toStringMatcher(value)),
    )

    is ConditionUiState.MimeTypeFilter -> listOf(
        IfwFilter.MimeType(matchMode.toStringMatcher(value)),
    )

    is ConditionUiState.PortFilter -> listOf(
        IfwFilter.Port(equals = equals, min = min, max = max),
    )

    is ConditionUiState.ComponentPattern -> {
        val matcher = matchMode.toStringMatcher(value)
        when (patternType) {
            ComponentPatternType.COMPONENT -> listOf(IfwFilter.Component(matcher))
            ComponentPatternType.NAME -> listOf(IfwFilter.ComponentName(matcher))
            ComponentPatternType.PACKAGE -> listOf(IfwFilter.ComponentPackage(matcher))
        }
    }
}

private fun SourceOption.toIfwFilter(): IfwFilter = when (this) {
    SourceOption.ALLOW_SYSTEM_ONLY -> IfwFilter.Not(IfwFilter.Sender(SenderType.SYSTEM))
    SourceOption.ALLOW_SIGNATURE_ONLY -> IfwFilter.Not(IfwFilter.Sender(SenderType.SIGNATURE))
    SourceOption.ALLOW_SYSTEM_OR_SIGNATURE -> IfwFilter.Not(IfwFilter.Sender(SenderType.SYSTEM_OR_SIGNATURE))
    SourceOption.BLOCK_SYSTEM -> IfwFilter.Sender(SenderType.SYSTEM)
}

private fun MatchMode.toStringMatcher(value: String): StringMatcher = when (this) {
    MatchMode.EXACT -> StringMatcher.Equals(value)
    MatchMode.STARTS_WITH -> StringMatcher.StartsWith(value)
    MatchMode.CONTAINS -> StringMatcher.Contains(value)
    MatchMode.PATTERN -> StringMatcher.Pattern(value)
    MatchMode.REGEX -> StringMatcher.Regex(value)
    MatchMode.IS_NULL -> StringMatcher.IsNull(true)
}

// ── IFW → UI State ─────────────────────────────────────────

fun IfwRules.toEditorStateOrNull(
    componentType: IfwComponentType,
    packageName: String,
    componentName: String,
): RuleEditorUiState? {
    val filterName = "$packageName/$componentName"
    val matchingRules = rulesFor(componentType)

    for (rule in matchingRules) {
        for (filter in rule.filters) {
            val state = tryParseFilter(filter, filterName, packageName, componentName, componentType)
            if (state != null) {
                return state.copy(
                    log = rule.log,
                    blockEnabled = rule.block,
                )
            }
        }
    }
    return null
}

fun IfwRules.toEditorState(
    componentType: IfwComponentType,
    packageName: String,
    componentName: String,
): RuleEditorUiState = toEditorStateOrNull(componentType, packageName, componentName)
    ?: RuleEditorUiState(
        packageName = packageName,
        componentName = componentName,
        componentType = componentType,
    )

private fun tryParseFilter(
    filter: IfwFilter,
    filterName: String,
    packageName: String,
    componentName: String,
    componentType: IfwComponentType,
): RuleEditorUiState? = when {
    // Case 1: Direct ComponentFilter
    filter is IfwFilter.ComponentFilter && filter.name == filterName -> {
        RuleEditorUiState(
            packageName = packageName,
            componentName = componentName,
            componentType = componentType,
            blockMode = BlockMode.ALL,
        )
    }

    // Case 2: And with ComponentFilter
    filter is IfwFilter.And && filter.filters.any {
        it is IfwFilter.ComponentFilter && it.name == filterName
    } -> {
        parseAndFilter(filter, filterName, packageName, componentName, componentType)
    }

    // Case 3: Unrecognizable structure containing this component
    filter.containsComponentFilter(filterName) -> {
        RuleEditorUiState(
            packageName = packageName,
            componentName = componentName,
            componentType = componentType,
            isAdvancedRule = true,
        )
    }

    else -> null
}

private fun parseAndFilter(
    and: IfwFilter.And,
    filterName: String,
    packageName: String,
    componentName: String,
    componentType: IfwComponentType,
): RuleEditorUiState {
    val conditions = and.filters.filter { !(it is IfwFilter.ComponentFilter && it.name == filterName) }

    // Check if there's a single Or child → ANY_MATCH mode
    val singleOr = conditions.singleOrNull() as? IfwFilter.Or
    val (combineMode, effectiveConditions) = if (singleOr != null) {
        CombineMode.ANY_MATCH to singleOr.filters
    } else {
        CombineMode.ALL_MATCH to conditions
    }

    val parsedConditions = effectiveConditions.map { it.toConditionUiState() }
    if (parsedConditions.any { it == null }) {
        return RuleEditorUiState(
            packageName = packageName,
            componentName = componentName,
            componentType = componentType,
            isAdvancedRule = true,
        )
    }
    val uiConditions = parsedConditions.filterNotNull().mergeRelatedConditions()

    return RuleEditorUiState(
        packageName = packageName,
        componentName = componentName,
        componentType = componentType,
        blockMode = BlockMode.CONDITIONAL,
        combineMode = combineMode,
        conditions = uiConditions,
    )
}

private fun IfwFilter.toConditionUiState(): ConditionUiState? {
    val id = UUID.randomUUID().toString()
    return when (this) {
        is IfwFilter.Action -> ConditionUiState.ActionFilter(
            id = id,
            matchMode = matcher.toMatchMode(),
            value = matcher.stringValue(),
        )

        is IfwFilter.Not -> when (val inner = filter) {
            is IfwFilter.Sender -> ConditionUiState.SourceControl(
                id = id,
                option = when (inner.type) {
                    SenderType.SYSTEM -> SourceOption.ALLOW_SYSTEM_ONLY
                    SenderType.SIGNATURE -> SourceOption.ALLOW_SIGNATURE_ONLY
                    SenderType.SYSTEM_OR_SIGNATURE -> SourceOption.ALLOW_SYSTEM_OR_SIGNATURE
                    else -> return null
                },
            )

            is IfwFilter.SenderPermission -> ConditionUiState.CallerPermission(
                id = id,
                permission = inner.name,
                mode = PermissionMode.REQUIRE,
            )

            else -> null
        }

        is IfwFilter.Sender -> ConditionUiState.SourceControl(
            id = id,
            option = when (type) {
                SenderType.SYSTEM -> SourceOption.BLOCK_SYSTEM
                else -> return null
            },
        )

        is IfwFilter.SenderPackage -> ConditionUiState.CallerApp(
            id = id,
            packageNames = listOf(name),
        )

        is IfwFilter.SenderPermission -> ConditionUiState.CallerPermission(
            id = id,
            permission = name,
            mode = PermissionMode.BLOCK_WITH,
        )

        is IfwFilter.Category -> ConditionUiState.CategoryFilter(id = id, name = name)

        is IfwFilter.Host -> ConditionUiState.LinkFilter(
            id = id,
            host = matcher.stringValue(),
        )

        is IfwFilter.Data -> ConditionUiState.DataFilter(
            id = id,
            matchMode = matcher.toMatchMode(),
            value = matcher.stringValue(),
        )

        is IfwFilter.MimeType -> ConditionUiState.MimeTypeFilter(
            id = id,
            matchMode = matcher.toMatchMode(),
            value = matcher.stringValue(),
        )

        is IfwFilter.Port -> ConditionUiState.PortFilter(
            id = id,
            portMode = if (equals != null) PortMode.EXACT else PortMode.RANGE,
            equals = equals,
            min = min,
            max = max,
        )

        is IfwFilter.Component -> ConditionUiState.ComponentPattern(
            id = id,
            patternType = ComponentPatternType.COMPONENT,
            matchMode = matcher.toMatchMode(),
            value = matcher.stringValue(),
        )

        is IfwFilter.ComponentName -> ConditionUiState.ComponentPattern(
            id = id,
            patternType = ComponentPatternType.NAME,
            matchMode = matcher.toMatchMode(),
            value = matcher.stringValue(),
        )

        is IfwFilter.ComponentPackage -> ConditionUiState.ComponentPattern(
            id = id,
            patternType = ComponentPatternType.PACKAGE,
            matchMode = matcher.toMatchMode(),
            value = matcher.stringValue(),
        )

        is IfwFilter.Scheme -> ConditionUiState.LinkFilter(id = id, scheme = matcher.stringValue())
        is IfwFilter.Path -> ConditionUiState.LinkFilter(
            id = id,
            path = matcher.stringValue(),
            pathMatchMode = matcher.toMatchMode(),
        )

        // Composite and unsupported filters
        else -> null
    }
}

/**
 * Merges adjacent Host, Path, Scheme conditions into single LinkFilter conditions.
 * Also merges adjacent SenderPackage conditions into single CallerApp conditions.
 */
private fun List<ConditionUiState>.mergeRelatedConditions(): List<ConditionUiState> {
    val result = mutableListOf<ConditionUiState>()
    var pendingLink: ConditionUiState.LinkFilter? = null
    val pendingPackages = mutableListOf<String>()
    var callerAppId: String? = null

    for (condition in this) {
        when (condition) {
            is ConditionUiState.LinkFilter -> {
                pendingLink = if (pendingLink == null) {
                    condition
                } else {
                    pendingLink.copy(
                        host = condition.host.ifBlank { pendingLink.host },
                        path = condition.path.ifBlank { pendingLink.path },
                        pathMatchMode = if (condition.path.isNotBlank()) condition.pathMatchMode else pendingLink.pathMatchMode,
                        scheme = condition.scheme.ifBlank { pendingLink.scheme },
                    )
                }
            }

            is ConditionUiState.CallerApp -> {
                if (callerAppId == null) callerAppId = condition.id
                pendingPackages.addAll(condition.packageNames)
            }

            else -> {
                // Flush pending link/packages before adding non-link condition
                pendingLink?.let {
                    result.add(it)
                    pendingLink = null
                }
                if (pendingPackages.isNotEmpty()) {
                    result.add(ConditionUiState.CallerApp(id = callerAppId!!, packageNames = pendingPackages.toList()))
                    pendingPackages.clear()
                    callerAppId = null
                }
                result.add(condition)
            }
        }
    }
    // Flush remaining
    pendingLink?.let { result.add(it) }
    if (pendingPackages.isNotEmpty()) {
        result.add(ConditionUiState.CallerApp(id = callerAppId!!, packageNames = pendingPackages.toList()))
    }
    return result
}

private fun StringMatcher.toMatchMode(): MatchMode = when (this) {
    is StringMatcher.Equals -> MatchMode.EXACT
    is StringMatcher.StartsWith -> MatchMode.STARTS_WITH
    is StringMatcher.Contains -> MatchMode.CONTAINS
    is StringMatcher.Pattern -> MatchMode.PATTERN
    is StringMatcher.Regex -> MatchMode.REGEX
    is StringMatcher.IsNull -> MatchMode.IS_NULL
}

private fun StringMatcher.stringValue(): String = when (this) {
    is StringMatcher.Equals -> value
    is StringMatcher.StartsWith -> value
    is StringMatcher.Contains -> value
    is StringMatcher.Pattern -> value
    is StringMatcher.Regex -> value
    is StringMatcher.IsNull -> ""
}

private fun IfwFilter.containsComponentFilter(name: String): Boolean = when (this) {
    is IfwFilter.ComponentFilter -> this.name == name
    is IfwFilter.And -> filters.any { it.containsComponentFilter(name) }
    is IfwFilter.Or -> filters.any { it.containsComponentFilter(name) }
    is IfwFilter.Not -> filter.containsComponentFilter(name)
    else -> false
}

// ── Merge Strategy ─────────────────────────────────────────

fun IfwRules.mergeComponentRule(
    componentType: IfwComponentType,
    componentName: String,
    newFilter: IfwFilter?,
    block: Boolean,
    log: Boolean,
): IfwRules {
    // Step 1: Remove old filters for this component from all matching rules
    val updatedRules = rules.map { rule ->
        if (rule.componentType != componentType) return@map rule
        val cleanedFilters = rule.filters.filter { !it.containsComponentFilter(componentName) }
        rule.copy(filters = cleanedFilters)
    }.filter { it.filters.isNotEmpty() }

    // Step 2: Add new dedicated rule if newFilter is provided
    val finalRules = if (newFilter != null) {
        updatedRules + IfwRule(
            componentType = componentType,
            block = block,
            log = log,
            filters = listOf(newFilter),
        )
    } else {
        updatedRules
    }

    return IfwRules(finalRules)
}
