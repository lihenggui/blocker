// Copyright 2026 Blocker
// SPDX-License-Identifier: Apache-2.0

package com.merxury.core.ifw

import com.merxury.core.ifw.model.IfwComponentType
import com.merxury.core.ifw.model.IfwFilter
import com.merxury.core.ifw.model.IfwIntentFilter
import com.merxury.core.ifw.model.IfwRule
import com.merxury.core.ifw.model.IfwRules
import com.merxury.core.ifw.model.StringMatcher
import com.merxury.core.ifw.xml.IfwXmlSerializer

/**
 * A user-selected wake rule. Android's broadcast firewall uses the component supplied in the
 * intent for package matching, so the action-only form matches broadcasts with an explicit receiver.
 */
data class UserWakeRule(
    val type: Type,
    val sender: String?,
    val target: String,
    val component: String?,
    val action: String?,
) {
    enum class Type { SERVICE, BROADCAST }

    init {
        require(isWakePackageName(target))
        require(sender == null || isWakePackageName(sender))
        require(component == null || (component.isNotBlank() && !component.startsWith('.') && '/' !in component))
        require(action == null || action.isNotBlank())
        require(component != null || (type == Type.BROADCAST && action != null))
    }

    internal fun toIfwRule(): IfwRule {
        val type = when (type) {
            Type.SERVICE -> IfwComponentType.SERVICE
            Type.BROADCAST -> IfwComponentType.BROADCAST
        }
        val selectors: List<IfwFilter> = if (component != null) {
            buildList {
                add(IfwFilter.ComponentFilter("$target/$component"))
                if (type == IfwComponentType.BROADCAST && action != null) add(IfwFilter.Action(StringMatcher.Equals(action)))
            }
        } else {
            listOf(IfwFilter.ComponentPackage(StringMatcher.Equals(target)))
        }
        val intentFilters = if (component == null) listOf(IfwIntentFilter(actions = listOf(requireNotNull(action)))) else emptyList()
        return IfwRule(type, log = false, intentFilters = intentFilters, filters = selectors + wakeSenderFilters(sender, target))
    }
}

fun renderUserWakeRules(rules: Collection<UserWakeRule>): String? {
    val firewallRules = rules.sortedWith(compareBy({ it.type }, { it.target }, { it.component }, { it.sender }, { it.action }))
        .map(UserWakeRule::toIfwRule)
        .distinct()
    return if (firewallRules.isEmpty()) null else IfwXmlSerializer().serialize(IfwRules(firewallRules))
}
