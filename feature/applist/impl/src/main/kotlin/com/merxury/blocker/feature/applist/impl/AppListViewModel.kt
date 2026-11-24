/*
 * Copyright 2025 Blocker
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

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.merxury.blocker.core.analytics.AnalyticsHelper
import com.merxury.blocker.core.data.device.DeviceUserRepository
import com.merxury.blocker.core.data.device.InstalledAppRepository
import com.merxury.blocker.core.data.device.SelectedUserRepository
import com.merxury.blocker.core.data.respository.userdata.UserDataRepository
import com.merxury.blocker.core.di.AppPackageName
import com.merxury.blocker.core.dispatchers.BlockerDispatchers.DEFAULT
import com.merxury.blocker.core.dispatchers.Dispatcher
import com.merxury.blocker.core.domain.applist.CountServicesUseCase
import com.merxury.blocker.core.domain.applist.PerformAppActionUseCase
import com.merxury.blocker.core.domain.applist.ReadRunningPackagesUseCase
import com.merxury.blocker.core.model.component.AppKey
import com.merxury.blocker.core.model.component.ServiceCounts
import com.merxury.blocker.core.model.component.UserId
import com.merxury.blocker.core.model.privileged.ChannelState
import com.merxury.blocker.core.model.privileged.UnavailableReason
import com.merxury.blocker.core.model.system.AppAction
import com.merxury.blocker.core.model.system.InstalledApp
import com.merxury.blocker.core.model.system.ReadFailure
import com.merxury.blocker.core.model.system.ReadResult
import com.merxury.blocker.core.privileged.PrivilegedChannel
import com.merxury.blocker.core.ui.applist.AppActionFailed
import com.merxury.blocker.core.ui.applist.appRows
import com.merxury.blocker.core.ui.profile.toProfileTab
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNot
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * The apps of the selected user, read from the cache, and the actions on them. Every change shows
 * through the repositories' flows: after an action the cache is read again, never patched here.
 */
@HiltViewModel
class AppListViewModel @Inject internal constructor(
    private val selectedUsers: SelectedUserRepository,
    private val deviceUsers: DeviceUserRepository,
    private val installedApps: InstalledAppRepository,
    private val performAppAction: PerformAppActionUseCase,
    private val readRunningPackages: ReadRunningPackagesUseCase,
    private val countAppServices: CountServicesUseCase,
    private val channel: PrivilegedChannel,
    userDataRepository: UserDataRepository,
    private val analyticsHelper: AnalyticsHelper,
    @Dispatcher(DEFAULT) cpuDispatcher: CoroutineDispatcher,
    @AppPackageName appPackageName: String,
) : ViewModel() {
    private val self = AppKey(appPackageName, selectedUsers.ownUser)
    private val refreshes = MutableStateFlow<Map<UserId, RefreshStatus>>(emptyMap())

    // What the system last answered for each user; absent when it could not be read.
    private val running = MutableStateFlow<Map<UserId, Set<String>>>(emptyMap())
    private val serviceCounts = MutableStateFlow<Map<AppKey, ServiceCounts>>(emptyMap())
    private val message = MutableStateFlow<AppActionFailed?>(null)
    private var refreshJob: Job? = null
    private var refreshedWithAccess: Boolean? = null

    @OptIn(ExperimentalCoroutinesApi::class)
    private val selectedApps = selectedUsers.selectedUser.flatMapLatest { user ->
        combine(
            installedApps.apps(user),
            refreshes.map { it[user] },
            running.map { it[user] },
        ) { apps, refresh, running -> SelectedApps(user, apps, refresh, running) }
    }

    val uiState: StateFlow<AppListUiState> = combine(
        selectedUsers.users,
        selectedApps,
        userDataRepository.userData,
        serviceCounts,
        message,
    ) { users, selected, preferences, counts, message ->
        val cached = selected.apps.isNotEmpty()
        AppListUiState(
            ownUser = selectedUsers.ownUser,
            selectedUser = selected.user,
            profiles = users.map { it.toProfileTab() },
            apps = when {
                cached || selected.refresh == RefreshStatus.Done ->
                    AppsUiState.Loaded(appRows(selected.apps, selected.running, counts, preferences, self))

                selected.refresh is RefreshStatus.Failed -> AppsUiState.Error(selected.refresh.failure)

                else -> AppsUiState.Loading
            },
            isRefreshing = cached && selected.refresh == RefreshStatus.Running,
            refreshFailure = (selected.refresh as? RefreshStatus.Failed)?.failure?.takeIf { cached },
            showServiceInfo = preferences.showServiceInfo,
            message = message,
        )
    }
        .flowOn(cpuDispatcher)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = AppListUiState(ownUser = selectedUsers.ownUser, selectedUser = selectedUsers.selectedUser.value),
        )

    /**
     * Keeps the list current while the screen is shown: refreshes once the privileged channel has
     * been checked, and again whenever it becomes ready or is lost, since that changes which users
     * can be read. Returns only when cancelled.
     */
    suspend fun refreshOnAccessChanges() {
        channel.state
            .filterNot { it == ChannelState.Unavailable(UnavailableReason.NOT_CHECKED) }
            .map { it is ChannelState.Ready }
            .distinctUntilChanged()
            .collect { ready ->
                // A screen shown again, such as after a rotation, has nothing new to read.
                if (ready != refreshedWithAccess) {
                    refreshedWithAccess = ready
                    startRefresh(restart = true)
                }
            }
    }

    /** Reads the users, then the selected user's apps, from the system. */
    fun refresh() = startRefresh(restart = false)

    fun selectUser(user: UserId) {
        selectedUsers.select(user)
        if (selectedUsers.selectedUser.value == user && user !in refreshes.value) {
            viewModelScope.launch { refreshApps(user) }
        }
    }

    /**
     * Reads the service counts of [app] for its row. The caller cancels this when the row leaves
     * the screen; a short wait first keeps rows that only scroll past from reading anything.
     */
    suspend fun countServices(app: AppKey) {
        if (app in serviceCounts.value) return
        delay(SHOWN_BEFORE_COUNTING_MILLIS)
        val counts = countAppServices(app) ?: return
        serviceCounts.update { it + (app to counts) }
    }

    fun setEnabled(app: AppKey, enabled: Boolean) = perform(if (enabled) AppAction.ENABLE else AppAction.DISABLE, app)

    fun forceStop(app: AppKey) = perform(AppAction.FORCE_STOP, app)

    fun clearCache(app: AppKey) = perform(AppAction.CLEAR_CACHE, app)

    fun clearData(app: AppKey) = perform(AppAction.CLEAR_DATA, app)

    fun uninstall(app: AppKey) = perform(AppAction.UNINSTALL, app)

    fun onMessageShown() {
        message.value = null
    }

    private fun startRefresh(restart: Boolean) {
        if (!restart && refreshJob?.isActive == true) return
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch {
            deviceUsers.refresh()
            val user = selectedUsers.selectedUser.value
            // Reading the users drops those no longer reachable with their apps, so any other
            // user is read again when next selected.
            refreshes.update { statuses -> statuses.filterKeys { it == user } }
            refreshApps(user)
        }
    }

    private suspend fun refreshApps(user: UserId) {
        refreshes.update { it + (user to RefreshStatus.Running) }
        val result = installedApps.refresh(user)
        readRunning(user)
        serviceCounts.update { counts -> counts.filterKeys { it.userId != user } }
        val status = when (result) {
            is ReadResult.Success -> RefreshStatus.Done
            is ReadResult.Failure -> RefreshStatus.Failed(result.reason)
        }
        refreshes.update { it + (user to status) }
    }

    private suspend fun readRunning(user: UserId) {
        val packages = readRunningPackages(user)
        running.update { if (packages != null) it + (user to packages) else it - user }
    }

    private fun perform(action: AppAction, app: AppKey) = viewModelScope.launch {
        val label = installedApps.app(app).first()?.label ?: app.packageName
        val failure = performAppAction(app, action)
        analyticsHelper.logAction(action)
        if (failure != null) message.value = AppActionFailed(action, label, failure)
        // Counted again when the row is next shown, as the action may have stopped services.
        serviceCounts.update { it - app }
        readRunning(app.userId)
    }

    private data class SelectedApps(
        val user: UserId,
        val apps: List<InstalledApp>,
        val refresh: RefreshStatus?,
        val running: Set<String>?,
    )

    private sealed interface RefreshStatus {
        data object Running : RefreshStatus
        data object Done : RefreshStatus
        data class Failed(val failure: ReadFailure) : RefreshStatus
    }

    private companion object {
        const val SHOWN_BEFORE_COUNTING_MILLIS = 300L
    }
}
