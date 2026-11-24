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

import com.merxury.blocker.core.model.ComponentType
import com.merxury.blocker.core.model.component.AppKey
import com.merxury.blocker.core.model.component.BlockReason
import com.merxury.blocker.core.model.component.ComponentId
import com.merxury.blocker.core.model.component.UserId
import com.merxury.blocker.core.model.data.GeneralRule
import com.merxury.blocker.core.ui.ProcessingProgress
import com.merxury.blocker.core.ui.TabState
import com.merxury.blocker.core.ui.applist.AppActionFailed
import com.merxury.blocker.core.ui.applist.AppRow
import com.merxury.blocker.core.ui.coil.ApkIcon
import com.merxury.blocker.core.ui.control.ControlMessage
import com.merxury.blocker.core.ui.control.ModeBanner
import com.merxury.blocker.core.ui.profile.ProfileTab

/**
 * What the search shows: the results for [keyword] among the apps of [selectedUser], and a
 * switcher between [profiles] when there is more than one.
 *
 * @property keyword the keyword the results are for, which trails the typed text by a moment
 * @property selected the components chosen in selection mode, of apps of [selectedUser]
 * @property batch the progress of the batch running for [selectedUser], or null; the selection
 * actions are disabled meanwhile
 * @property indexing how far the reading of every app's components got, or null when it does not
 * run for [selectedUser]
 * @property banners limits of the current mode, shown once above the components
 * @property messages what to tell, oldest first; see [SearchViewModel.onMessageShown]
 */
data class SearchUiState(
    val ownUser: UserId,
    val selectedUser: UserId,
    val profiles: List<ProfileTab> = emptyList(),
    val keyword: String = "",
    val tabs: TabState<SearchTab> = TabState(SearchTab.entries, SearchTab.APPS),
    val results: SearchResults = SearchResults.Idle,
    val isSelectionMode: Boolean = false,
    val selected: Set<ComponentId> = emptySet(),
    val batch: ProcessingProgress? = null,
    val indexing: IndexingProgress? = null,
    val banners: List<ModeBanner> = emptyList(),
    val messages: List<QueuedSearchMessage> = emptyList(),
) {
    val showProfileSwitcher: Boolean get() = profiles.size > 1

    val selectedProfile: ProfileTab? get() = profiles.find { it.user == selectedUser }

    /** The apps that have a selected component, for the title of selection mode. */
    val selectedAppCount: Int get() = selected.mapTo(mutableSetOf()) { it.packageName }.size
}

/** The tabs of the search, in their order. */
enum class SearchTab {
    APPS,
    COMPONENTS,
    RULES,
}

sealed interface SearchResults {
    /** Nothing is searched for. */
    data object Idle : SearchResults

    /** The results of a new keyword are not read yet. */
    data object Loading : SearchResults

    /**
     * What the cache holds for the keyword.
     *
     * @property components by app, in the order of their package names
     * @property matchedRules the general rules that match an installed app; [unmatchedRules] the rest
     * @property showServiceInfo whether the app rows show their service counts, as the user chose
     */
    data class Loaded(
        val apps: List<AppRow> = emptyList(),
        val showServiceInfo: Boolean = false,
        val components: List<ComponentGroup> = emptyList(),
        val matchedRules: List<GeneralRule> = emptyList(),
        val unmatchedRules: List<GeneralRule> = emptyList(),
    ) : SearchResults
}

/** The components of one app whose class name contains the keyword. */
data class ComponentGroup(
    val app: AppKey,
    val label: String,
    val icon: ApkIcon,
    val components: List<FoundComponent>,
) {
    /** The detail tab that lists the first kind of component found, in the order the old search used. */
    val firstType: ComponentType?
        get() = TYPE_ORDER.firstOrNull { type -> components.any { it.type == type } }

    private companion object {
        val TYPE_ORDER = listOf(ComponentType.RECEIVER, ComponentType.SERVICE, ComponentType.ACTIVITY, ComponentType.PROVIDER)
    }
}

/**
 * One component found, with the state the cache holds for its user.
 *
 * @property blockedBy every known reason it cannot start; empty unless [state] is BLOCKED
 * @property conditional whether a conditional Intent Firewall rule names it
 * @property inFlight whether a batch is changing it now
 */
data class FoundComponent(
    val id: ComponentId,
    val type: ComponentType,
    val state: FoundState,
    val blockedBy: Set<BlockReason> = emptySet(),
    val conditional: Boolean = false,
    val inFlight: Boolean = false,
)

enum class FoundState {
    /** The component can run, perhaps limited by a conditional rule. */
    RUNNABLE,

    /** Something blocks the component. */
    BLOCKED,

    /** Nothing known blocks it, but part of its state could not be read. */
    UNKNOWN,
}

/**
 * [done] of [total] apps of the user have their components read.
 *
 * @property total 0 until the apps of the user are listed
 */
data class IndexingProgress(
    val done: Int,
    val total: Int,
)

/** A message waiting to be told; equal messages are queued apart by their [id]. */
data class QueuedSearchMessage(
    val id: Long,
    val message: SearchMessage,
)

sealed interface SearchMessage {
    /** An action on an app of the results failed. */
    data class AppFailed(val failure: AppActionFailed) : SearchMessage

    /** A batch on the selected components ended. */
    data class Control(val message: ControlMessage) : SearchMessage

    /** Indexing ended without reading everything, so some states show as unknown. */
    data object IndexIncomplete : SearchMessage

    /** The apps of the user could not be listed, so nothing was indexed. */
    data object IndexFailed : SearchMessage
}
