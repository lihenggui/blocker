// Copyright 2026 Blocker
// SPDX-License-Identifier: Apache-2.0

package com.merxury.core.ifw

import com.merxury.core.ifw.model.IfwComponentType
import com.merxury.core.ifw.model.IfwFilter
import com.merxury.core.ifw.model.IfwRule
import com.merxury.core.ifw.xml.IfwXmlSerializer

/** A user-selected Intent Firewall rule for one sender and one component. */
data class UserWakeRule(
    val sender: String,
    val target: String,
    val component: String,
    val componentType: IfwComponentType = IfwComponentType.SERVICE,
) {
    init {
        require(isWakePackageName(target))
        require(isWakePackageName(sender))
        require(component.isNotBlank() && !component.startsWith('.') && '/' !in component)
    }

    internal fun toIfwRule(): IfwRule = IfwRule(
        componentType,
        log = false,
        filters = listOf(IfwFilter.ComponentFilter("$target/$component")) + wakeSenderFilters(sender, target),
    )
}

fun renderUserWakeRules(rules: Collection<UserWakeRule>): String? {
    val firewallRules = rules.distinct().sortedWith(compareBy({ it.target }, { it.componentType }, { it.component }, { it.sender }))
        .map(UserWakeRule::toIfwRule)
    return if (firewallRules.isEmpty()) null else IfwXmlSerializer().serializeInOrder(firewallRules)
}
