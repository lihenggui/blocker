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

package com.merxury.blocker.feature.search.impl

import com.merxury.blocker.core.model.component.AppKey
import com.merxury.blocker.core.model.component.Availability
import com.merxury.blocker.core.model.component.ComponentId
import com.merxury.blocker.core.model.component.ComponentStatus
import com.merxury.blocker.core.model.component.IfwBlock
import com.merxury.blocker.core.model.component.isFilteredByIntentFirewall
import com.merxury.blocker.core.model.system.InstalledApp
import com.merxury.blocker.core.ui.TabState
import com.merxury.blocker.core.ui.coil.ApkIcon
import com.merxury.blocker.core.ui.control.unknownCause

/**
 * The components found for one user, grouped by app in the order of their package names, with
 * the state the cache holds. Like the apps, system apps only show when the user shows them, and
 * Blocker in the user it runs in is left out: changing it from here could only break the app in
 * use. Components of an app that is not cached as installed are left out.
 *
 * @param apps the cached apps of the user the components were found for
 * @param inFlight the components a batch is changing now
 * @param firewallReadable whether the current mode can read Intent Firewall rules at all, which
 * tells why a state is unknown
 */
internal fun componentGroups(
    found: List<ComponentStatus>,
    apps: List<InstalledApp>,
    showSystemApps: Boolean,
    self: AppKey,
    inFlight: Set<ComponentId>,
    firewallReadable: Boolean,
): List<ComponentGroup> {
    val installed = apps.associateBy { it.key.packageName }
    return found
        .groupBy { it.declaration.id.packageName }
        .toSortedMap()
        .mapNotNull { (packageName, statuses) ->
            val app = installed[packageName] ?: return@mapNotNull null
            if (app.key == self || (app.isSystem && !showSystemApps)) return@mapNotNull null
            ComponentGroup(
                app = app.key,
                label = app.label,
                icon = ApkIcon(packageName, app.apkPath, app.versionCode, app.uid),
                components = statuses.map { it.toFound(it.declaration.id in inFlight, firewallReadable) },
            )
        }
}

private fun ComponentStatus.toFound(inFlight: Boolean, firewallReadable: Boolean): FoundComponent {
    val type = declaration.type
    val conditional = type.isFilteredByIntentFirewall && state.ifw == IfwBlock.CONDITIONAL
    return when (val availability = availability) {
        Availability.Runnable, Availability.Conditional -> FoundComponent(declaration.id, type, FoundState.RUNNABLE, conditional = conditional, inFlight = inFlight)
        is Availability.Blocked -> FoundComponent(declaration.id, type, FoundState.BLOCKED, availability.reasons, conditional, inFlight)
        Availability.Unknown -> FoundComponent(declaration.id, type, FoundState.UNKNOWN, conditional = conditional, inFlight = inFlight, unknownCause = unknownCause(firewallReadable))
    }
}

/**
 * The tabs with the number of results of each: apps, components and rules.
 *
 * @param componentsCounted whether every app of the user has had its components read, so the
 * number of components found is final; until then the components tab shows no number
 */
internal fun tabsOf(results: SearchResults, selected: SearchTab, componentsCounted: Boolean): TabState<SearchTab> {
    val loaded = results as? SearchResults.Loaded
    val counts = buildMap {
        put(SearchTab.APPS, loaded?.apps?.size ?: 0)
        if (componentsCounted) put(SearchTab.COMPONENTS, loaded?.components?.sumOf { it.components.size } ?: 0)
        put(SearchTab.RULES, loaded?.let { it.matchedRules.size + it.unmatchedRules.size } ?: 0)
    }
    return TabState(items = SearchTab.entries, selectedItem = selected, itemCount = counts)
}
