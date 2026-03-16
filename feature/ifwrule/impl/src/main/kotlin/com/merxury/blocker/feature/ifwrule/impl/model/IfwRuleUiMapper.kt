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

import com.merxury.core.ifw.editor.IfwEditorGroupMode
import com.merxury.core.ifw.editor.IfwEditorNode
import com.merxury.core.ifw.editor.toEditorRootGroup
import com.merxury.core.ifw.editor.toIfwFilterOrNull
import com.merxury.core.ifw.editor.toTopLevelFilters
import com.merxury.core.ifw.model.IfwComponentType
import com.merxury.core.ifw.model.IfwFilter
import com.merxury.core.ifw.model.IfwRule
import com.merxury.core.ifw.model.IfwRules

fun RuleEditorUiState.toIfwFilter(): IfwFilter {
    val componentFilter = IfwFilter.ComponentFilter("$packageName/$componentName")
    if (blockMode == BlockMode.ALL || rootGroup.children.isEmpty()) {
        return componentFilter
    }

    val conditionFilters = rootGroup.toTopLevelFilters()
    if (conditionFilters.isEmpty()) return componentFilter

    val children = if (!rootGroup.excluded && rootGroup.mode == IfwEditorGroupMode.ALL) {
        listOf(componentFilter) + conditionFilters
    } else {
        listOf(componentFilter, rootGroup.toIfwFilterOrNull() ?: return componentFilter)
    }
    return IfwFilter.And(children)
}

fun IfwRules.toEditorStateOrNull(
    componentType: IfwComponentType,
    packageName: String,
    componentName: String,
): RuleEditorUiState? {
    val filterName = "$packageName/$componentName"
    for (rule in rulesFor(componentType)) {
        val state = tryParseRule(
            filters = rule.filters,
            filterName = filterName,
            packageName = packageName,
            componentName = componentName,
            componentType = componentType,
        )
        if (state != null) {
            return state.copy(
                log = rule.log,
                blockEnabled = rule.block,
            )
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

private fun tryParseRule(
    filters: List<IfwFilter>,
    filterName: String,
    packageName: String,
    componentName: String,
    componentType: IfwComponentType,
): RuleEditorUiState? {
    val extraction = extractConditionsForComponent(filters, filterName) ?: return null
    return when (extraction) {
        is ComponentRuleExtraction.Unsupported -> RuleEditorUiState(
            packageName = packageName,
            componentName = componentName,
            componentType = componentType,
            isAdvancedRule = true,
        )
        is ComponentRuleExtraction.Success -> {
            if (extraction.filters.isEmpty()) {
                RuleEditorUiState(
                    packageName = packageName,
                    componentName = componentName,
                    componentType = componentType,
                    blockMode = BlockMode.ALL,
                )
            } else {
                val rootGroup = extraction.filters.toEditorRootGroup() ?: return RuleEditorUiState(
                    packageName = packageName,
                    componentName = componentName,
                    componentType = componentType,
                    isAdvancedRule = true,
                )
                RuleEditorUiState(
                    packageName = packageName,
                    componentName = componentName,
                    componentType = componentType,
                    blockMode = BlockMode.CONDITIONAL,
                    rootGroup = rootGroup,
                )
            }
        }
    }
}

private sealed interface ComponentRuleExtraction {
    data class Success(val filters: List<IfwFilter>) : ComponentRuleExtraction
    data object Unsupported : ComponentRuleExtraction
}

private fun extractConditionsForComponent(
    filters: List<IfwFilter>,
    filterName: String,
): ComponentRuleExtraction? {
    var foundTarget = false
    val remaining = mutableListOf<IfwFilter>()

    filters.forEach { filter ->
        when {
            filter is IfwFilter.ComponentFilter && filter.name == filterName -> {
                foundTarget = true
            }

            filter is IfwFilter.And && filter.filters.any { child ->
                child is IfwFilter.ComponentFilter && child.name == filterName
            } -> {
                foundTarget = true
                val nested = filter.filters.filterNot { child ->
                    child is IfwFilter.ComponentFilter && child.name == filterName
                }
                if (nested.any { child -> child is IfwFilter.ComponentFilter }) {
                    return ComponentRuleExtraction.Unsupported
                }
                remaining += nested
            }

            filter.containsComponentFilter(filterName) -> return ComponentRuleExtraction.Unsupported
            else -> remaining += filter
        }
    }

    if (!foundTarget) return null
    if (remaining.any { filter -> filter is IfwFilter.ComponentFilter }) {
        return ComponentRuleExtraction.Unsupported
    }
    return ComponentRuleExtraction.Success(remaining)
}

private fun IfwFilter.containsComponentFilter(name: String): Boolean = when (this) {
    is IfwFilter.ComponentFilter -> this.name == name
    is IfwFilter.And -> filters.any { child -> child.containsComponentFilter(name) }
    is IfwFilter.Or -> filters.any { child -> child.containsComponentFilter(name) }
    is IfwFilter.Not -> filter.containsComponentFilter(name)
    else -> false
}

fun IfwRules.mergeComponentRule(
    componentType: IfwComponentType,
    componentName: String,
    newFilter: IfwFilter?,
    block: Boolean,
    log: Boolean,
): IfwRules {
    val updatedRules = rules.map { rule ->
        if (rule.componentType != componentType) return@map rule
        val cleanedFilters = rule.filters.filter { filter -> !filter.containsComponentFilter(componentName) }
        rule.copy(filters = cleanedFilters)
    }.filter { rule -> rule.filters.isNotEmpty() }

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
