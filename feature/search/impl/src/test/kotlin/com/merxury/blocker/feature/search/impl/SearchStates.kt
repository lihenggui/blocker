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
import com.merxury.blocker.core.model.component.UserId
import com.merxury.blocker.core.model.data.GeneralRule
import com.merxury.blocker.core.ui.ProcessingProgress
import com.merxury.blocker.core.ui.applist.previewAppRows
import com.merxury.blocker.core.ui.coil.ApkIcon
import com.merxury.blocker.core.ui.control.ModeBanner
import com.merxury.blocker.core.ui.profile.ProfileLabel
import com.merxury.blocker.core.ui.profile.ProfileTab

/** States of the search for screenshot and screen tests. */
internal object SearchStates {
    const val KEYWORD = "push"

    val profiles = listOf(
        ProfileTab(OWNER, ProfileLabel.Personal, running = true),
        ProfileTab(WORK, ProfileLabel.Work, running = true),
    )

    fun groups(user: UserId = OWNER): List<ComponentGroup> = listOf(
        ComponentGroup(
            app = AppKey("com.example.chat", user),
            label = "Chat",
            icon = ApkIcon("com.example.chat", apkPath = null, versionCode = 1, uid = null),
            components = listOf(
                FoundComponent(id("com.example.chat", "PushReceiver"), ComponentType.RECEIVER, FoundState.BLOCKED, setOf(BlockReason.PM_SETTING)),
                FoundComponent(id("com.example.chat", "PushService"), ComponentType.SERVICE, FoundState.RUNNABLE, conditional = true),
            ),
        ),
        ComponentGroup(
            app = AppKey("com.example.mail", user),
            label = "Mail",
            icon = ApkIcon("com.example.mail", apkPath = null, versionCode = 1, uid = null),
            components = listOf(
                FoundComponent(id("com.example.mail", "PushActivity"), ComponentType.ACTIVITY, FoundState.UNKNOWN),
                FoundComponent(id("com.example.mail", "PushProvider"), ComponentType.PROVIDER, FoundState.BLOCKED, setOf(BlockReason.IFW_RULE, BlockReason.MANIFEST_DEFAULT)),
            ),
        ),
    )

    private val rules = listOf(
        GeneralRule(id = 1, name = "Push SDK", company = "Example", matchedAppCount = 2),
        GeneralRule(id = 2, name = "Push kit", company = "Example", matchedAppCount = 0),
    )

    fun loaded(user: UserId = OWNER) = SearchResults.Loaded(
        apps = previewAppRows(user),
        showServiceInfo = true,
        components = groups(user),
        matchedRules = rules.filter { it.matchedAppCount > 0 },
        unmatchedRules = rules.filter { it.matchedAppCount == 0 },
    )

    fun idle() = SearchUiState(ownUser = OWNER, selectedUser = OWNER)

    fun loading() = idle().copy(keyword = KEYWORD, results = SearchResults.Loading)

    fun shown(tab: SearchTab, results: SearchResults.Loaded = loaded()) = idle().copy(
        keyword = KEYWORD,
        results = results,
        tabs = tabsOf(results, tab),
    )

    fun noResult() = shown(SearchTab.APPS, SearchResults.Loaded())

    fun selecting() = shown(SearchTab.COMPONENTS).copy(
        isSelectionMode = true,
        selected = groups().first().components.map { it.id }.toSet() + id("com.example.mail", "PushActivity"),
    )

    /** A batch over the first app's components, which show as changing. */
    fun batchRunning(): SearchUiState {
        val groups = groups().mapIndexed { index, group -> if (index == 0) group.copy(components = group.components.map { it.copy(inFlight = true) }) else group }
        return shown(SearchTab.COMPONENTS, loaded().copy(components = groups)).copy(batch = ProcessingProgress(current = 200, total = 450, isEnabling = false))
    }

    fun indexing() = shown(SearchTab.COMPONENTS).copy(indexing = IndexingProgress(done = 34, total = 212))

    fun banners() = shown(SearchTab.COMPONENTS).copy(banners = listOf(ModeBanner.NEEDS_ROOT, ModeBanner.FIREWALL_UNAVAILABLE))

    fun noAccess() = shown(SearchTab.COMPONENTS).copy(banners = listOf(ModeBanner.NO_ACCESS))

    fun workProfile() = shown(SearchTab.APPS, loaded(WORK)).copy(selectedUser = WORK, profiles = profiles)
}
