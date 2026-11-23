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

package com.merxury.blocker.core.rule.backup

import com.merxury.blocker.core.model.component.ComponentId
import com.merxury.core.ifw.model.IfwFilter
import com.merxury.core.ifw.model.IfwRule
import com.merxury.core.ifw.model.IfwRules
import com.merxury.core.ifw.xml.IfwXmlSerializer

/**
 * The rules of [MergedIfwRules.rules] with how many backup rules were added or merged in
 * ([MergedIfwRules.added]) and how many were already there ([MergedIfwRules.present]).
 */
internal data class MergedIfwRules(val rules: IfwRules, val added: Int, val present: Int)

/**
 * Merges the whole rules of a backup into the rules on the device.
 *
 * Every rule on the device is kept. A backup rule equal to one on the device, field by field, is
 * already present. A backup rule made only of component-filters with `block="true"`, the form
 * Blocker writes, adds the components no unconditional rule of its type names yet to the first
 * unconditional rule of that type without intent-filters, as blocking one does, or becomes a rule
 * of its own when there is none; so a plain backup restores to one rule per type. Any other rule,
 * such as one with conditions or `block="false"`, is added as it is.
 */
internal class IfwRuleMerge(private val serializer: IfwXmlSerializer) {

    fun merge(current: IfwRules, backup: IfwRules): MergedIfwRules {
        val rules = current.rules.toMutableList()
        var added = 0
        var present = 0
        backup.rules.filterNot { it.filters.isEmpty() && it.intentFilters.isEmpty() }.forEach { rule ->
            val changed = when {
                rules.any { same(it, rule) } -> false
                rule.isComponentList -> mergeComponents(rules, rule)
                else -> rules.add(rule)
            }
            if (changed) added++ else present++
        }
        return MergedIfwRules(IfwRules(rules), added, present)
    }

    /** Adds the components of [rule] that are not blocked unconditionally yet; false when none. */
    private fun mergeComponents(rules: MutableList<IfwRule>, rule: IfwRule): Boolean {
        val missing = rule.filters.filterIsInstance<IfwFilter.ComponentFilter>().distinct().filter { filter ->
            rules.none { it.componentType == rule.componentType && it.isUnconditional && it.names(filter) }
        }
        if (missing.isEmpty()) return false
        val target = rules.indexOfFirst { it.componentType == rule.componentType && it.isUnconditional && it.intentFilters.isEmpty() }
        if (target < 0) {
            rules += rule.copy(filters = missing)
        } else {
            rules[target] = rules[target].copy(filters = rules[target].filters + missing)
        }
        return true
    }

    // Intent-filter extras are bundles, which compare by identity, so compare those rules as XML.
    private fun same(a: IfwRule, b: IfwRule): Boolean = a == b ||
        ((a.hasExtras || b.hasExtras) && serializer.serialize(IfwRules(listOf(a))) == serializer.serialize(IfwRules(listOf(b))))
}

/** `block="true"` with only component-filters as filter elements, as IfwStore defines it. */
private val IfwRule.isUnconditional: Boolean get() = block && filters.all { it is IfwFilter.ComponentFilter }

/** A rule in the form Blocker writes: an unconditional rule of component-filters only. */
private val IfwRule.isComponentList: Boolean get() = isUnconditional && filters.isNotEmpty() && intentFilters.isEmpty()

private val IfwRule.hasExtras: Boolean get() = intentFilters.any { it.extras != null }

/** Names the component of [filter] in a top-level component-filter, in its full or `package/.Class` form. */
private fun IfwRule.names(filter: IfwFilter.ComponentFilter): Boolean {
    val id = ComponentId.unflatten(filter.name)
    return filters.any { it is IfwFilter.ComponentFilter && (it.name == filter.name || (id != null && ComponentId.unflatten(it.name) == id)) }
}
