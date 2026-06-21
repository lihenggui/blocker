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

package com.merxury.blocker.feature.globalifwrule.impl

import com.merxury.blocker.core.model.ComponentType
import com.merxury.core.ifw.editor.IfwEditorNode
import com.merxury.core.ifw.editor.hasTopLevelComponentFilter
import com.merxury.core.ifw.editor.toEditorRootGroup
import com.merxury.core.ifw.editor.toTopLevelFilters
import com.merxury.core.ifw.model.IfwComponentType
import com.merxury.core.ifw.model.IfwFilter
import com.merxury.core.ifw.model.IfwRule
import com.merxury.core.ifw.model.StringMatcher
import com.merxury.core.ifw.model.toSummary

internal fun SimpleGlobalIfwRuleDraft.toIfwRule(): IfwRule = IfwRule(
    componentType = componentType,
    block = block,
    log = log,
    filters = buildList {
        targets.distinct().sorted().forEach { target ->
            add(IfwFilter.ComponentFilter(target))
        }
        action.trim().takeIf { it.isNotBlank() }?.let { value ->
            add(IfwFilter.Action(StringMatcher.Equals(value)))
        }
        category.trim().takeIf { it.isNotBlank() }?.let { value ->
            add(IfwFilter.Category(value))
        }
        callerPackage.trim().takeIf { it.isNotBlank() }?.let { value ->
            add(IfwFilter.SenderPackage(value))
        }
    },
)

internal fun AdvancedGlobalIfwRuleDraft.toIfwRuleOrNull(): IfwRule? {
    if (!hasReadOnlyIntentFilters && !rootGroup.hasTopLevelComponentFilter()) {
        return null
    }
    val filters = rootGroup.toTopLevelFilters()
    if (filters.isEmpty() && intentFilters.isEmpty()) return null
    return IfwRule(
        componentType = componentType,
        block = block,
        log = log,
        intentFilters = intentFilters,
        filters = filters,
    )
}

internal fun IfwRule.toSimpleDraftOrNull(
    storagePackageName: String,
    editingRuleIndex: Int? = null,
): SimpleGlobalIfwRuleDraft? {
    if (intentFilters.isNotEmpty()) return null

    val targets = mutableListOf<String>()
    var action: String? = null
    var category: String? = null
    var callerPackage: String? = null

    for (filter in filters) {
        when (filter) {
            is IfwFilter.ComponentFilter -> targets += filter.name
            is IfwFilter.Action -> {
                val value = (filter.matcher as? StringMatcher.Equals)?.value ?: return null
                if (action != null) return null
                action = value
            }
            is IfwFilter.Category -> {
                if (category != null) return null
                category = filter.name
            }
            is IfwFilter.SenderPackage -> {
                if (callerPackage != null) return null
                callerPackage = filter.name
            }
            else -> return null
        }
    }

    if (targets.isEmpty()) return null
    val targetPackages = targets.map { it.substringBefore("/") }.distinct()
    if (targetPackages.size != 1) return null

    return SimpleGlobalIfwRuleDraft(
        originStoragePackageName = storagePackageName,
        selectedPackageName = targetPackages.single(),
        componentType = componentType,
        targetMode = if (targets.size == 1) SimpleTargetMode.SINGLE else SimpleTargetMode.MULTIPLE,
        targets = targets.distinct().sorted(),
        block = block,
        log = log,
        action = action.orEmpty(),
        category = category.orEmpty(),
        callerPackage = callerPackage.orEmpty(),
        editingRuleIndex = editingRuleIndex,
    )
}

internal fun IfwRule.toAdvancedDraft(
    storagePackageName: String,
    editingRuleIndex: Int? = null,
): AdvancedGlobalIfwRuleDraft = AdvancedGlobalIfwRuleDraft(
    originStoragePackageName = storagePackageName,
    storagePackageName = storagePackageName,
    componentType = componentType,
    block = block,
    log = log,
    intentFilters = intentFilters,
    rootGroup = filters.toEditorRootGroup() ?: IfwEditorNode.Group(),
    editingRuleIndex = editingRuleIndex,
)

internal fun IfwRule.toRuleItemUiState(
    index: Int,
    storagePackageName: String,
): RuleItemUiState {
    val simpleDraft = toSimpleDraftOrNull(
        storagePackageName = storagePackageName,
        editingRuleIndex = index,
    )
    val advancedDraft = toAdvancedDraft(
        storagePackageName = storagePackageName,
        editingRuleIndex = index,
    )
    return RuleItemUiState(
        componentType = componentType,
        block = block,
        log = log,
        filtersSummary = simpleDraft?.toSummary() ?: toSummary(storagePackageName),
        editMode = if (simpleDraft != null) {
            GlobalIfwRuleEditMode.SIMPLE
        } else {
            GlobalIfwRuleEditMode.ADVANCED
        },
        simpleDraft = simpleDraft,
        advancedDraft = advancedDraft,
        ruleIndex = index,
    )
}

internal fun AdvancedGlobalIfwRuleDraft.toDetailUiState(): AdvancedRuleDetailUiState = AdvancedRuleDetailUiState(
    storagePackageName = storagePackageName,
    componentType = componentType,
    block = block,
    log = log,
    filtersSummary = toSummary(),
    ruleIndex = editingRuleIndex ?: -1,
    draft = this,
)

internal fun SimpleGlobalIfwRuleDraft.toSummary(): String = buildList {
    val names = targets.map { target -> target.toDisplayName(selectedPackageName) }
    val targetSummary = if (targetMode == SimpleTargetMode.SINGLE) {
        names.firstOrNull()
    } else {
        val preview = names.take(3).joinToString()
        if (names.size > 3) "$preview +${names.size - 3}" else preview
    }
    targetSummary?.let { add(it) }
    action.trim().takeIf { it.isNotBlank() }?.let { value -> add("action = $value") }
    category.trim().takeIf { it.isNotBlank() }?.let { value -> add("category = $value") }
    callerPackage.trim().takeIf { it.isNotBlank() }?.let { value -> add("caller package = $value") }
}.joinToString("\n")

internal fun AdvancedGlobalIfwRuleDraft.toSummary(): String = buildList {
    intentFilters.forEach { intentFilter ->
        add("intent-filter: ${intentFilter.toSummary()}")
    }
    rootGroup.toTopLevelFilters().forEach { filter ->
        add(filter.toDisplaySummary(storagePackageName))
    }
}.joinToString("\n")

internal fun IfwRule.toSummary(storagePackageName: String): String = buildList {
    intentFilters.forEach { intentFilter ->
        add("intent-filter: ${intentFilter.toSummary()}")
    }
    filters.forEach { filter ->
        add(filter.toDisplaySummary(storagePackageName))
    }
}.joinToString("\n")

internal fun IfwComponentType.toComponentType(): ComponentType = when (this) {
    IfwComponentType.ACTIVITY -> ComponentType.ACTIVITY
    IfwComponentType.BROADCAST -> ComponentType.RECEIVER
    IfwComponentType.SERVICE -> ComponentType.SERVICE
}

internal fun flattenComponentName(
    packageName: String,
    componentName: String,
): String = "$packageName/$componentName"

private fun IfwFilter.toDisplaySummary(storagePackageName: String): String = when (this) {
    is IfwFilter.ComponentFilter -> name.toDisplayName(storagePackageName)
    is IfwFilter.And -> filters.joinToString(" AND ") { child -> child.toDisplaySummary(storagePackageName) }
    is IfwFilter.Or -> filters.joinToString(" OR ") { child -> child.toDisplaySummary(storagePackageName) }
    is IfwFilter.Not -> "NOT (${filter.toDisplaySummary(storagePackageName)})"
    else -> toSummary()
}

private fun String.toDisplayName(packageName: String): String {
    val componentName = substringAfter("/")
    return if (componentName.startsWith(packageName)) {
        componentName.removePrefix(packageName).ifBlank { componentName }
    } else {
        componentName.substringAfterLast('.')
    }
}
