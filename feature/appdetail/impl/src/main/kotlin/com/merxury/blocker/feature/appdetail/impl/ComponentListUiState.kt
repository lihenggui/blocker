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

import com.merxury.blocker.core.model.ComponentType
import com.merxury.blocker.core.model.component.ComponentId
import com.merxury.blocker.core.model.system.ReadFailure
import com.merxury.blocker.core.ui.AppDetailTabs
import com.merxury.blocker.core.ui.TabState
import java.io.File

/**
 * The component lists of one app for one user, with the tabs, search and selection around them.
 *
 * @property refreshFailure why the last refresh failed; null while one runs, after one succeeded and
 * before the first. [ReadFailure.PackageNotFound] means the app is not installed for the user.
 * @property accessCheckFailed whether nothing could be read because the privileged channel's first
 * access check did not settle
 * @property message something to tell once; see [ComponentListViewModel.onMessageShown]
 */
data class ComponentListUiState(
    val tabs: TabState<AppDetailTabs>,
    val components: ComponentsUiState = ComponentsUiState.Loading,
    val matchedRules: MatchedRulesUiState = MatchedRulesUiState.Loading,
    val isSearchMode: Boolean = false,
    val keyword: String = "",
    val isSelectionMode: Boolean = false,
    val selected: Set<ComponentId> = emptySet(),
    val isRefreshing: Boolean = false,
    val refreshFailure: ReadFailure? = null,
    val accessCheckFailed: Boolean = false,
    val message: ListMessage? = null,
)

/**
 * The components the batch actions of the selected tab act on: the rows it shows, or on the
 * matched rules tab every component the rules match, each once. None on the app info tab.
 */
internal fun ComponentListUiState.componentsOfSelectedPage(): List<ComponentId> = when (val tab = tabs.selectedItem) {
    AppDetailTabs.Sdk -> (matchedRules as? MatchedRulesUiState.Loaded)?.rules.orEmpty().flatMap { rule -> rule.rows.map { it.id } }.distinct()
    else -> (components as? ComponentsUiState.Loaded)?.components?.of(tab)?.map { it.id }.orEmpty()
}

sealed interface ComponentsUiState {
    /** Nothing is cached for the app yet and the first refresh has not finished. */
    data object Loading : ComponentsUiState

    /** The cached components, filtered by the search and sorted as the user chose. */
    data class Loaded(val components: AppComponents) : ComponentsUiState
}

/** The rows of one app by type, each list in the order shown. */
data class AppComponents(
    val receivers: List<ComponentRow> = emptyList(),
    val services: List<ComponentRow> = emptyList(),
    val activities: List<ComponentRow> = emptyList(),
    val providers: List<ComponentRow> = emptyList(),
) {
    /** The rows of [tab], or none for a tab that does not list components. */
    fun of(tab: AppDetailTabs): List<ComponentRow> = when (tab) {
        AppDetailTabs.Receiver -> receivers
        AppDetailTabs.Service -> services
        AppDetailTabs.Activity -> activities
        AppDetailTabs.Provider -> providers
        else -> emptyList()
    }
}

/**
 * One component as the cache holds it.
 *
 * @property enabled whether its switch shows on, see [com.merxury.blocker.core.ui.component.isSwitchOn]
 * @property isRunning whether the service runs; false when that cannot be read
 */
data class ComponentRow(
    val id: ComponentId,
    val type: ComponentType,
    val enabled: Boolean,
    val isRunning: Boolean = false,
    val description: String? = null,
)

sealed interface MatchedRulesUiState {
    data object Loading : MatchedRulesUiState

    data class Loaded(val rules: List<MatchedRule>) : MatchedRulesUiState
}

/** A general rule with the components of this app it matches. */
data class MatchedRule(
    val id: Int,
    val title: String,
    val icon: File?,
    val rows: List<ComponentRow>,
)

sealed interface ListMessage {
    /** A refresh failed; the rows shown are the ones cached before. */
    data class RefreshFailed(val reason: ReadFailure) : ListMessage

    data class LaunchFailed(val name: String, val reason: ActionFailure) : ListMessage

    data class StopFailed(val name: String, val reason: ActionFailure) : ListMessage

    /** Asked for root or Shizuku, but the channel is still not ready. */
    data object GrantFailed : ListMessage

    /** The privileged channel's first access check did not settle, so nothing could be read. */
    data object AccessCheckFailed : ListMessage
}

/** Why launching an activity, stopping a service or granting access failed, as the user is told. */
sealed interface ActionFailure {
    data object NoAccess : ActionFailure

    data object NotPermitted : ActionFailure

    data object NotFound : ActionFailure

    data class SystemError(val message: String?) : ActionFailure
}
