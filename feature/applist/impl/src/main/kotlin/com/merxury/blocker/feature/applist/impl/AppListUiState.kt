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

package com.merxury.blocker.feature.applist.impl

import com.merxury.blocker.core.model.component.UserId
import com.merxury.blocker.core.model.system.ReadFailure
import com.merxury.blocker.core.ui.applist.AppActionFailed
import com.merxury.blocker.core.ui.applist.AppRow
import com.merxury.blocker.core.ui.profile.ProfileTab

/**
 * What the app list shows: the apps of [selectedUser], and a switcher between [profiles] when
 * there is more than one.
 *
 * @property refreshFailure why the last refresh of [selectedUser] failed while [apps] shows the
 * cached list; null when it did not fail
 * @property message a failed action to report once; see [AppListViewModel.onMessageShown]
 */
data class AppListUiState(
    val ownUser: UserId,
    val selectedUser: UserId,
    val profiles: List<ProfileTab> = emptyList(),
    val apps: AppsUiState = AppsUiState.Loading,
    val isRefreshing: Boolean = false,
    val refreshFailure: ReadFailure? = null,
    val showServiceInfo: Boolean = false,
    val message: AppActionFailed? = null,
) {
    val showProfileSwitcher: Boolean get() = profiles.size > 1

    val selectedProfile: ProfileTab? get() = profiles.find { it.user == selectedUser }
}

sealed interface AppsUiState {
    /** Nothing is cached for the user yet and the first refresh has not finished. */
    data object Loading : AppsUiState

    /** Nothing is cached for the user and the refresh failed. */
    data class Error(val failure: ReadFailure) : AppsUiState

    /** The cached apps, filtered and sorted as the user chose. */
    data class Loaded(val apps: List<AppRow>) : AppsUiState
}
