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

package com.merxury.core.ifw

import com.merxury.blocker.core.model.ComponentType
import com.merxury.blocker.core.model.component.ComponentDeclaration
import com.merxury.blocker.core.model.component.ComponentId
import com.merxury.blocker.core.model.component.IfwBlock
import com.merxury.core.ifw.model.IfwComponentType
import com.merxury.core.ifw.model.IfwFilter
import com.merxury.core.ifw.model.IfwRule
import com.merxury.core.ifw.model.IfwRules

// What blocks a component and how blocking changes the rules, as IfwStore describes.

/** The rule type that filters components of this type, or null for providers. */
internal val ComponentType.ifwType: IfwComponentType?
    get() = when (this) {
        ComponentType.ACTIVITY -> IfwComponentType.ACTIVITY
        ComponentType.RECEIVER -> IfwComponentType.BROADCAST
        ComponentType.SERVICE -> IfwComponentType.SERVICE
        ComponentType.PROVIDER -> null
    }

/**
 * `block="true"` and no top-level filter element but component-filters. Android selects a rule by
 * its component-filters or its intent-filters and treats only the other top-level elements as
 * conditions, so intent-filters do not make a rule conditional; measured on API 28 and API 34,
 * see [IfwStore].
 */
private val IfwRule.isUnconditional: Boolean
    get() = block && filters.all { it is IfwFilter.ComponentFilter }

/** Names [id] in a top-level component-filter, in its full or `package/.Class` form. */
private fun IfwRule.names(id: ComponentId): Boolean = filters.any { it.isFilterFor(id) }

private fun IfwFilter.isFilterFor(id: ComponentId): Boolean = (this as? IfwFilter.ComponentFilter)?.let { ComponentId.unflatten(it.name) } == id

internal fun IfwRules.blockOf(component: ComponentDeclaration): IfwBlock {
    val type = component.type.ifwType ?: return IfwBlock.NOT_BLOCKED
    val naming = rulesFor(type).filter { it.block && it.names(component.id) }
    return when {
        naming.any { it.isUnconditional } -> IfwBlock.BLOCKED
        naming.isNotEmpty() -> IfwBlock.CONDITIONAL
        else -> IfwBlock.NOT_BLOCKED
    }
}

/** These rules with [component] blocked or unblocked. A provider leaves them as they are. */
internal fun IfwRules.withBlocked(component: ComponentDeclaration, blocked: Boolean, canExtend: (IfwRule) -> Boolean): IfwRules {
    val type = component.type.ifwType ?: return this
    return if (blocked) withBlocked(type, component.id, canExtend) else withUnblocked(type, component.id)
}

private fun IfwRules.withBlocked(type: IfwComponentType, id: ComponentId, canExtend: (IfwRule) -> Boolean): IfwRules {
    if (rulesFor(type).any { it.isUnconditional && it.names(id) }) return this
    val filter = IfwFilter.ComponentFilter(id.flatten())
    // Never one with intent-filters: those are the user's rules, not Blocker's list.
    val target = rules.indexOfFirst { it.componentType == type && it.isUnconditional && it.intentFilters.isEmpty() && canExtend(it) }
    if (target < 0) return IfwRules(rules + IfwRule(componentType = type, filters = listOf(filter)))
    return IfwRules(rules.mapIndexed { index, rule -> if (index == target) rule.copy(filters = rule.filters + filter) else rule })
}

private fun IfwRules.withUnblocked(type: IfwComponentType, id: ComponentId): IfwRules {
    if (rulesFor(type).none { it.isUnconditional && it.names(id) }) return this
    return IfwRules(
        rules.mapNotNull { rule ->
            if (rule.componentType != type || !rule.isUnconditional || !rule.names(id)) return@mapNotNull rule
            val remaining = rule.filters.filterNot { it.isFilterFor(id) }
            if (remaining.isEmpty() && rule.intentFilters.isEmpty()) null else rule.copy(filters = remaining)
        },
    )
}
