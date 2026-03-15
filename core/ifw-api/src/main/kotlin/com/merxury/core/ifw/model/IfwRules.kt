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
 * Root container for all Intent Firewall rules in a single IFW rules file.
 *
 * Corresponds to the top-level `<rules>` XML element. Each IFW XML file
 * (stored at `/data/system/ifw/<packageName>.xml`) contains one [IfwRules] instance.
 *
 * Rules are organized by component type: activities, broadcasts, and services.
 * Each component type section can contain multiple filters (both `<component-filter>`
 * and advanced intent-matching filters).
 *
 * Example XML:
 * ```xml
 * <rules>
 *   <activity block="true" log="false">
 *     <component-filter name="com.example/com.example.MainActivity" />
 *   </activity>
 *   <broadcast block="true" log="true">
 *     <action equals="android.intent.action.BOOT_COMPLETED" />
 *   </broadcast>
 * </rules>
 * ```
 *
 * @property rules the list of individual rules contained in this file
 */
data class IfwRules(
    val rules: List<IfwRule> = emptyList(),
) {
    /**
     * Returns all rules targeting the given [componentType].
     *
     * @param componentType the component type to filter by
     * @return list of matching rules
     */
    fun rulesFor(componentType: IfwComponentType): List<IfwRule> = rules.filter { it.componentType == componentType }

    /**
     * Returns all [IfwFilter.ComponentFilter] entries across all rules of the given type.
     *
     * This is a convenience method for backward compatibility with the existing
     * component-blocking workflow.
     *
     * @param componentType the component type to search
     * @return set of component filter names
     */
    fun componentFiltersFor(componentType: IfwComponentType): Set<String> = rulesFor(componentType)
        .flatMap { it.filters }
        .filterIsInstance<IfwFilter.ComponentFilter>()
        .map { it.name }
        .toSet()

    /**
     * Returns `true` if there are no rules at all (or all rules have empty filter lists).
     */
    fun isEmpty(): Boolean = rules.isEmpty() || rules.all { it.filters.isEmpty() }

    companion object {
        /** Creates an empty [IfwRules] with no rules. */
        fun empty(): IfwRules = IfwRules()
    }
}
