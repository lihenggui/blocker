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

import com.merxury.blocker.core.model.component.AppKey
import com.merxury.blocker.core.model.component.UserId
import com.merxury.blocker.core.model.system.ReadFailure
import com.merxury.blocker.core.ui.applist.ServiceCounts
import com.merxury.blocker.core.ui.coil.ApkIcon

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
    val message: AppListMessage? = null,
) {
    val showProfileSwitcher: Boolean get() = profiles.size > 1

    /** The app detail screen can show only apps of the user Blocker runs in so far. */
    val canOpenDetail: Boolean get() = selectedUser == ownUser

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

/**
 * One app in the list.
 *
 * @property isRunning whether a process of the app runs in its user; null when that cannot be read
 * @property services the app's service counts; null while they are not read or cannot be
 */
data class AppRow(
    val key: AppKey,
    val label: String,
    val versionName: String,
    val versionCode: Long,
    val icon: ApkIcon,
    val isDisabled: Boolean,
    val isRunning: Boolean?,
    val services: ServiceCounts?,
)

/** A user in the profile switcher. */
data class ProfileTab(
    val user: UserId,
    val label: ProfileLabel,
    val running: Boolean,
)

sealed interface ProfileLabel {
    /** The device owner. */
    data object Personal : ProfileLabel

    /** A managed work profile. */
    data object Work : ProfileLabel

    /** Another user or profile, with the name the system gives it. */
    data class Named(val name: String) : ProfileLabel

    /** Another user or profile whose name cannot be read. */
    data class Numbered(val id: Int) : ProfileLabel
}

enum class AppAction {
    ENABLE,
    DISABLE,
    FORCE_STOP,
    CLEAR_CACHE,
    CLEAR_DATA,
    UNINSTALL,
}

sealed interface AppListMessage {
    data class ActionFailed(
        val action: AppAction,
        val appLabel: String,
        val reason: FailureReason,
    ) : AppListMessage
}

/** Why an action failed, as the user is told. */
sealed interface FailureReason {
    /** Blocker has no root or Shizuku access. */
    data object NoAccess : FailureReason

    /** Shizuku runs as the shell, which may not do this; root may. */
    data object NeedsRoot : FailureReason

    /** The system refused it for the identity Blocker's access runs as. */
    data object NotPermitted : FailureReason

    /** The app is not installed for the user. */
    data object NotFound : FailureReason

    /** The system failed, with its message when it gave one. */
    data class SystemError(val message: String?) : FailureReason
}
