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

package com.merxury.blocker.feature.ruledetail.impl

import com.merxury.blocker.core.model.component.AppKey
import com.merxury.blocker.core.model.component.ComponentStatus
import com.merxury.blocker.core.model.system.InstalledApp
import com.merxury.blocker.core.ui.coil.ApkIcon
import com.merxury.blocker.core.ui.component.toRow
import com.merxury.blocker.feature.ruledetail.RuleDetailSortType
import com.merxury.blocker.feature.ruledetail.RuleDetailSortType.MOST_BLOCKED
import com.merxury.blocker.feature.ruledetail.RuleDetailSortType.MOST_ENABLED
import com.merxury.blocker.feature.ruledetail.RuleDetailSortType.MOST_MATCHED
import com.merxury.blocker.feature.ruledetail.RuleDetailSortType.NAME
import com.merxury.blocker.feature.ruledetail.RuleDetailSortType.RECENTLY_INSTALLED

/**
 * The apps of [matched] components with the components of each, in the order of their package
 * names; components of an app not among [apps] are left out.
 */
internal fun matchedApps(matched: List<ComponentStatus>, apps: List<InstalledApp>): List<MatchedApp> {
    val installed = apps.associateBy { it.key.packageName }
    return matched.groupBy { it.declaration.id.packageName }
        .toSortedMap()
        .mapNotNull { (packageName, statuses) ->
            val app = installed[packageName] ?: return@mapNotNull null
            MatchedApp(
                app = app.key,
                label = app.label,
                icon = ApkIcon(packageName, app.apkPath, app.versionCode, app.uid),
                rows = statuses.map { it.toRow() },
                firstInstallTime = app.firstInstallTime,
            )
        }
}

/**
 * The matched apps in the order of a sort, which stays while the sort and the apps stay the same:
 * a component that is blocked or enabled does not move its app under the user's finger.
 */
internal data class SortedApps(
    val sort: RuleDetailSortType,
    val order: List<AppKey>,
    val apps: List<MatchedApp>,
) {
    /** [apps] sorted by [sort], in the order of [this] when the sort and the apps are the same. */
    fun next(apps: List<MatchedApp>, sort: RuleDetailSortType): SortedApps {
        val keys = apps.map { it.app }
        val keep = sort == this.sort && keys.toSet() == order.toSet()
        val order = if (keep) order else apps.sortedFor(sort).map { it.app }
        val byKey = apps.associateBy { it.app }
        return SortedApps(sort, order, order.mapNotNull { byKey[it] })
    }

    companion object {
        val NONE = SortedApps(NAME, emptyList(), emptyList())
    }
}

private fun List<MatchedApp>.sortedFor(sort: RuleDetailSortType): List<MatchedApp> = when (sort) {
    NAME -> sortedBy { it.label.lowercase() }
    RECENTLY_INSTALLED -> sortedByDescending { it.firstInstallTime }
    MOST_MATCHED -> sortedByDescending { it.rows.size }
    MOST_BLOCKED -> sortedByDescending { app -> app.rows.count { !it.enabled } }
    MOST_ENABLED -> sortedByDescending { app -> app.rows.count { it.enabled } }
}
