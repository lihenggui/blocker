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
import com.merxury.blocker.core.model.component.ComponentStatus
import com.merxury.blocker.core.model.system.InstalledApp
import com.merxury.blocker.core.ui.TabState
import com.merxury.blocker.core.ui.coil.ApkIcon

/**
 * The components found for one user, grouped by app in the order of their package names. Like the
 * apps, system apps only show when the user shows them,
 * and Blocker in the user it runs in is left out: changing it from here could only break the app
 * in use. Components of an app that is not cached as installed are left out.
 *
 * @param apps the cached apps of the user the components were found for
 */
internal fun componentGroups(
    found: List<ComponentStatus>,
    apps: List<InstalledApp>,
    showSystemApps: Boolean,
    self: AppKey,
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
                components = statuses.map { FoundComponent(it.declaration.id, it.declaration.type) },
            )
        }
}

/** The tabs with the number of results of each: apps, apps with components found, and rules. */
internal fun tabsOf(results: SearchResults, selected: SearchTab): TabState<SearchTab> {
    val loaded = results as? SearchResults.Loaded
    val counts = mapOf(
        SearchTab.APPS to (loaded?.apps?.size ?: 0),
        SearchTab.COMPONENTS to (loaded?.components?.size ?: 0),
        SearchTab.RULES to (loaded?.let { it.matchedRules.size + it.unmatchedRules.size } ?: 0),
    )
    return TabState(items = SearchTab.entries, selectedItem = selected, itemCount = counts)
}
