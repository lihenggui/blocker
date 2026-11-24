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

package com.merxury.blocker.feature.appdetail.impl

import com.merxury.blocker.core.data.device.SelectedUserRepository
import com.merxury.blocker.core.model.ComponentType
import com.merxury.blocker.core.model.component.AppKey
import com.merxury.blocker.core.model.component.Availability
import com.merxury.blocker.core.model.component.ComponentId
import com.merxury.blocker.core.model.component.ComponentStatus
import com.merxury.blocker.core.model.component.IfwBlock
import com.merxury.blocker.core.model.component.PmSetting
import com.merxury.blocker.core.model.component.UserId
import com.merxury.blocker.core.model.component.isFilteredByIntentFirewall
import com.merxury.blocker.core.model.data.GeneralRule
import com.merxury.blocker.core.model.preference.ComponentShowPriority
import com.merxury.blocker.core.model.preference.ComponentSorting
import com.merxury.blocker.core.model.preference.SortingOrder
import com.merxury.blocker.core.model.preference.UserPreferenceData
import com.merxury.blocker.core.ui.AppDetailTabs
import com.merxury.blocker.core.ui.TabState
import com.merxury.blocker.core.ui.component.isSwitchOn
import java.io.File

/** The app a detail screen shows: [packageName] for [userId], or for the user Blocker runs in when null. */
internal fun SelectedUserRepository.appKeyOf(packageName: String, userId: Int?): AppKey = AppKey(packageName, userId?.let(::UserId) ?: ownUser)

/**
 * The row for [this] component, from what the repository holds and nothing else.
 *
 * @param running the running services of the app, or null when they cannot be read
 */
internal fun ComponentStatus.toRow(description: String?, running: Set<ComponentId>?): ComponentRow = ComponentRow(
    id = declaration.id,
    type = declaration.type,
    enabled = isSwitchOn,
    isRunning = running?.contains(declaration.id) == true,
    description = description,
)

/** [rows] by type, each sorted as the user chose, running services first. */
internal fun appComponents(rows: List<ComponentRow>, preferences: UserPreferenceData): AppComponents {
    fun of(type: ComponentType) = rows.filter { it.type == type }.sortedFor(preferences)
    return AppComponents(
        receivers = of(ComponentType.RECEIVER),
        services = of(ComponentType.SERVICE).sortedByDescending { it.isRunning },
        activities = of(ComponentType.ACTIVITY),
        providers = of(ComponentType.PROVIDER),
    )
}

// Stable sorts, so each keeps the order of the one before within its groups.
private fun List<ComponentRow>.sortedFor(preferences: UserPreferenceData): List<ComponentRow> {
    val byName: Comparator<ComponentRow> = when (preferences.componentSorting) {
        ComponentSorting.COMPONENT_NAME -> compareBy { it.id.simpleName.lowercase() }
        ComponentSorting.PACKAGE_NAME -> compareBy { it.id.className.lowercase() }
    }
    val ordered = sortedWith(if (preferences.componentSortingOrder == SortingOrder.ASCENDING) byName else byName.reversed())
    return when (preferences.componentShowPriority) {
        ComponentShowPriority.NONE -> ordered
        ComponentShowPriority.DISABLED_COMPONENTS_FIRST -> ordered.sortedBy { it.enabled }
        ComponentShowPriority.ENABLED_COMPONENTS_FIRST -> ordered.sortedByDescending { it.enabled }
    }
}

/** The keywords of a search: comma separated, trimmed, empty ones left out. */
internal fun keywordsOf(keyword: String): List<String> = keyword.split(",").map { it.trim() }.filter { it.isNotEmpty() }

/** The rows whose class name contains any of [keywords], ignoring case; all of them without keywords. */
internal fun AppComponents.matching(keywords: List<String>): AppComponents {
    if (keywords.isEmpty()) return this
    fun List<ComponentRow>.matching() = filter { row -> keywords.any { row.id.className.contains(it, ignoreCase = true) } }
    return AppComponents(receivers.matching(), services.matching(), activities.matching(), providers.matching())
}

/**
 * The general rules that match components of the app, in the rules' order, each with the rows
 * whose class name contains one of its keywords, ignoring case.
 *
 * @param iconOf the file a rule's icon path points to
 */
internal fun matchedRules(rules: List<GeneralRule>, rows: List<ComponentRow>, iconOf: (String) -> File): List<MatchedRule> = rules.mapNotNull { rule ->
    val matched = rows.filter { row -> rule.searchKeyword.any { row.id.className.contains(it, ignoreCase = true) } }
    if (matched.isEmpty()) return@mapNotNull null
    MatchedRule(id = rule.id, title = rule.name, icon = rule.iconUrl?.let(iconOf), rows = matched)
}

/** Whether [this] tab lists rows that can be searched, sorted and selected. */
internal val AppDetailTabs.listsComponents: Boolean
    get() = this != AppDetailTabs.Info && this != AppDetailTabs.Sdk

private val TAB_ORDER = listOf(
    AppDetailTabs.Info,
    AppDetailTabs.Receiver,
    AppDetailTabs.Service,
    AppDetailTabs.Activity,
    AppDetailTabs.Provider,
    AppDetailTabs.Sdk,
)

/**
 * The tabs to show: the app info always, a component tab while loading or when it has rows, and
 * the matched rules while loading or when there are any. [requested] is selected while shown,
 * otherwise the app info.
 */
internal fun tabsOf(components: ComponentsUiState, rules: MatchedRulesUiState, requested: AppDetailTabs): TabState<AppDetailTabs> {
    val counts = buildMap {
        put(AppDetailTabs.Info, 1)
        if (components is ComponentsUiState.Loaded) {
            TAB_ORDER.forEach { tab -> components.components.of(tab).size.takeIf { it > 0 }?.let { put(tab, it) } }
        }
        if (rules is MatchedRulesUiState.Loaded && rules.rules.isNotEmpty()) put(AppDetailTabs.Sdk, rules.rules.size)
    }
    val visible = TAB_ORDER.filter { tab ->
        when (tab) {
            AppDetailTabs.Info -> true
            AppDetailTabs.Sdk -> rules is MatchedRulesUiState.Loading || tab in counts
            else -> components is ComponentsUiState.Loading || tab in counts
        }
    }
    return TabState(items = visible, selectedItem = requested.takeIf { it in visible } ?: AppDetailTabs.Info, itemCount = counts)
}
