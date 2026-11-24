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
import com.merxury.blocker.core.model.component.ComponentId
import com.merxury.blocker.core.model.component.UserId
import com.merxury.blocker.core.model.data.GeneralRule
import com.merxury.blocker.core.ui.ProcessingProgress
import com.merxury.blocker.core.ui.TabState
import com.merxury.blocker.core.ui.applist.AppActionFailed
import com.merxury.blocker.core.ui.applist.AppRow
import com.merxury.blocker.core.ui.coil.ApkIcon
import com.merxury.blocker.core.ui.control.ControlMessage
import com.merxury.blocker.core.ui.data.UiMessage
import com.merxury.blocker.core.ui.profile.ProfileTab

/**
 * What the search shows: the results for [keyword] among the apps of [selectedUser], and a
 * switcher between [profiles] when there is more than one.
 *
 * @property keyword the keyword the results are for, which trails the typed text by a moment
 * @property selected the components chosen in selection mode, of apps of [selectedUser]
 * @property selectionActionsEnabled whether the selection can be blocked or enabled now: false
 * while nothing is selected, a batch of the search runs, or a batch of any screen changes a
 * selected component
 * @property batch the progress of the batch running for [selectedUser], or null; the selection
 * actions are disabled meanwhile
 * @property indexing how far the reading of every app's components got, or null when it does not
 * run for [selectedUser]
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
    val selectionActionsEnabled: Boolean = false,
    val batch: ProcessingProgress? = null,
    val indexing: IndexingProgress? = null,
    val indexError: UiMessage? = null,
    val messages: List<QueuedSearchMessage> = emptyList(),
) {
    val showProfileSwitcher: Boolean get() = profiles.size > 1

    val selectedProfile: ProfileTab? get() = profiles.find { it.user == selectedUser }

    /** The apps selected, for the title of selection mode. */
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
     * @property matchedRules the general rules that match components of the user's apps; [unmatchedRules] the rest
     * @property showServiceInfo whether the app rows show their service counts, as the user chose
     * @property rulesLoading whether the rules are still being matched; the apps and components do
     * not wait for them
     */
    data class Loaded(
        val apps: List<AppRow> = emptyList(),
        val showServiceInfo: Boolean = false,
        val components: List<ComponentGroup> = emptyList(),
        val matchedRules: List<GeneralRule> = emptyList(),
        val unmatchedRules: List<GeneralRule> = emptyList(),
        val rulesLoading: Boolean = false,
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

/** One component found, which its app's row counts by [type]. */
data class FoundComponent(
    val id: ComponentId,
    val type: ComponentType,
)

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

    /** A batch of the selection ended. */
    data class Control(val message: ControlMessage) : SearchMessage
}
