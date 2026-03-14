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

package com.merxury.core.ifw

import android.content.ComponentName
import com.merxury.core.ifw.model.IfwRules

/**
 * Interface for managing Android Intent Firewall rules.
 *
 * Provides both full rule CRUD via [IfwRules] and convenience methods
 * for component-level blocking via `<component-filter>`.
 */
interface IIntentFirewall {

    // ── Core API ───────────────────────────────────────────────────────

    /**
     * Loads and returns the full [IfwRules] for a package.
     *
     * @param packageName the package to load rules for
     * @return the [IfwRules] for the package, or [IfwRules.empty] if none exist
     */
    suspend fun getRules(packageName: String): IfwRules

    /**
     * Saves the full [IfwRules] for a package.
     *
     * If the rules are empty, the IFW file is deleted.
     *
     * @param packageName the package to save rules for
     * @param rules the rules to write
     */
    suspend fun saveRules(packageName: String, rules: IfwRules)

    /**
     * Remove the IFW rules for specific package.
     */
    suspend fun clear(packageName: String)

    /**
     * Reset internal cache, forcing next read to go to file system.
     */
    fun resetCache()

    // ── Component Filter Convenience Methods ───────────────────────────

    /**
     * Add a component-filter rule to block a single component.
     *
     * @return true if the component was successfully blocked
     */
    suspend fun addComponentFilter(packageName: String, componentName: String): Boolean

    /**
     * Remove a component-filter rule to unblock a single component.
     *
     * @return true if the component was successfully unblocked
     */
    suspend fun removeComponentFilter(packageName: String, componentName: String): Boolean

    /**
     * Add component-filter rules for multiple components.
     */
    suspend fun addAllComponentFilters(
        list: List<ComponentName>,
        callback: suspend (ComponentName) -> Unit = {},
    )

    /**
     * Remove component-filter rules for multiple components.
     */
    suspend fun removeAllComponentFilters(
        list: List<ComponentName>,
        callback: suspend (ComponentName) -> Unit = {},
    )

    /**
     * Check whether a component is enabled (not blocked by IFW).
     *
     * @return false if the component is blocked by a component-filter rule
     */
    suspend fun getComponentEnableState(packageName: String, componentName: String): Boolean
}
