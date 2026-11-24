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

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.merxury.blocker.core.analytics.AnalyticsHelper
import com.merxury.blocker.core.appcontrol.AppControl
import com.merxury.blocker.core.appcontrol.ServiceControl
import com.merxury.blocker.core.data.device.ComponentStatusRepository
import com.merxury.blocker.core.data.device.SelectedUserRepository
import com.merxury.blocker.core.data.di.RuleBaseFolder
import com.merxury.blocker.core.data.respository.componentdetail.ComponentDetailRepository
import com.merxury.blocker.core.data.respository.generalrule.GeneralRuleRepository
import com.merxury.blocker.core.data.respository.userdata.UserDataRepository
import com.merxury.blocker.core.di.FilesDir
import com.merxury.blocker.core.dispatchers.BlockerDispatchers.DEFAULT
import com.merxury.blocker.core.dispatchers.BlockerDispatchers.IO
import com.merxury.blocker.core.dispatchers.Dispatcher
import com.merxury.blocker.core.model.component.Availability
import com.merxury.blocker.core.model.component.BlockReason
import com.merxury.blocker.core.model.component.ComponentId
import com.merxury.blocker.core.model.component.ComponentKey
import com.merxury.blocker.core.model.privileged.CallFailure
import com.merxury.blocker.core.model.privileged.CallResult
import com.merxury.blocker.core.model.privileged.ChannelState
import com.merxury.blocker.core.model.privileged.UnavailableReason
import com.merxury.blocker.core.model.system.ReadFailure
import com.merxury.blocker.core.model.system.ReadResult
import com.merxury.blocker.core.privileged.PrivilegedChannel
import com.merxury.blocker.core.ui.AppDetailTabs
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import java.io.File

/**
 * The component lists of one app for one user, with search, tabs and selection, and the actions
 * on a single running component. Every row shows what the component repository holds: after a
 * change elsewhere the repository's flow updates the rows, never a copy kept here.
 */
@HiltViewModel(assistedFactory = ComponentListViewModel.Factory::class)
class ComponentListViewModel @AssistedInject constructor(
    selectedUsers: SelectedUserRepository,
    private val componentStatuses: ComponentStatusRepository,
    private val serviceControl: ServiceControl,
    private val appControl: AppControl,
    private val channel: PrivilegedChannel,
    private val componentDetails: ComponentDetailRepository,
    generalRules: GeneralRuleRepository,
    userDataRepository: UserDataRepository,
    private val analyticsHelper: AnalyticsHelper,
    @FilesDir private val filesDir: File,
    @RuleBaseFolder private val ruleBaseFolder: String,
    @Dispatcher(DEFAULT) cpuDispatcher: CoroutineDispatcher,
    @Dispatcher(IO) private val ioDispatcher: CoroutineDispatcher,
    @Assisted("packageName") packageName: String,
    @Assisted userId: Int?,
    @Assisted("tab") tab: String,
    @Assisted searchKeyword: List<String>,
) : ViewModel() {
    private val app = selectedUsers.appKeyOf(packageName, userId)
    private val initialKeyword = searchKeyword.map { it.trim() }.filter { it.isNotEmpty() }.joinToString(",")
    private val search = MutableStateFlow(Search(isSearchMode = initialKeyword.isNotEmpty(), keyword = initialKeyword))
    private val requestedTab = MutableStateFlow(tabOf(tab))
    private val selection = MutableStateFlow(Selection())
    private val refresh = MutableStateFlow<RefreshStatus?>(null)

    // What the system last answered; null when it could not be read.
    private val running = MutableStateFlow<Set<ComponentId>?>(null)
    private val message = MutableStateFlow<ListMessage?>(null)
    private var refreshJob: Job? = null
    private var refreshedWith: Access? = null

    private val statuses = componentStatuses.components(app)

    @OptIn(ExperimentalCoroutinesApi::class)
    private val descriptions: Flow<Map<String, String>> = statuses
        .map { list -> list.mapTo(mutableSetOf()) { it.declaration.id.className } }
        .distinctUntilChanged()
        .flatMapLatest(::descriptionsOf)
        .onStart { emit(emptyMap()) }
        .flowOn(ioDispatcher)

    // In the repository's order, by class name.
    private val rows: Flow<List<ComponentRow>> = combine(statuses, descriptions, running, componentStatuses.firewallReadable) { statuses, descriptions, running, readable ->
        statuses.map { it.toRow(descriptions[it.declaration.id.className], running, readable) }
    }

    private val lists: Flow<Lists> = combine(rows, userDataRepository.userData, search, refresh) { rows, preferences, search, refresh ->
        val loading = rows.isEmpty() && (refresh == null || refresh == RefreshStatus.Running)
        val components = if (loading) ComponentsUiState.Loading else ComponentsUiState.Loaded(appComponents(rows, preferences).matching(keywordsOf(search.keyword)))
        Lists(rows, components, search, isRefreshing = !loading && refresh == RefreshStatus.Running)
    }

    private val listsAndRules: Flow<Pair<Lists, MatchedRulesUiState>> = combine(lists, generalRules.getGeneralRules()) { lists, rules ->
        val matched = if (lists.components is ComponentsUiState.Loading) {
            MatchedRulesUiState.Loading
        } else {
            MatchedRulesUiState.Loaded(matchedRules(rules, lists.rows) { filesDir.resolve(ruleBaseFolder).resolve(it) })
        }
        lists to matched
    }

    private val banners: Flow<List<ModeBanner>> = combine(channel.state, componentStatuses.firewallReadable, userDataRepository.userData) { state, readable, preferences ->
        bannersOf(state, readable, preferences.controllerType)
    }

    val uiState: StateFlow<ComponentListUiState> = combine(
        listsAndRules,
        banners,
        requestedTab,
        selection,
        message,
    ) { (lists, matched), banners, requestedTab, selection, message ->
        ComponentListUiState(
            tabs = tabsOf(lists.components, matched, requestedTab),
            components = lists.components,
            matchedRules = matched,
            isSearchMode = lists.search.isSearchMode,
            keyword = lists.search.keyword,
            isSelectionMode = selection.active,
            selected = selection.ids,
            isRefreshing = lists.isRefreshing,
            banners = banners,
            message = message,
        )
    }
        .flowOn(cpuDispatcher)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = ComponentListUiState(
                tabs = tabsOf(ComponentsUiState.Loading, MatchedRulesUiState.Loading, requestedTab.value),
                isSearchMode = search.value.isSearchMode,
                keyword = search.value.keyword,
            ),
        )

    /**
     * Keeps the rows current while the screen is shown: refreshes the app when first called and
     * again whenever the privileged channel's access changes, since that changes what can be
     * read, and reads the running services again after a change of the components' state.
     * Returns only when cancelled.
     */
    @OptIn(FlowPreview::class)
    suspend fun keepFresh(): Unit = coroutineScope {
        launch {
            channel.state.map(::accessOf).distinctUntilChanged().collect { access ->
                // A screen shown again, such as after a rotation, has nothing new to read.
                if (access != refreshedWith) {
                    refreshedWith = access
                    startRefresh(restart = true)
                }
            }
        }
        launch {
            statuses.map { list -> list.map { it.state } }
                .distinctUntilChanged()
                .drop(1)
                .debounce(RUNNING_REREAD_DELAY_MILLIS)
                .collect { readRunning() }
        }
    }

    /** Reads the app again from the system; the cached rows stay shown meanwhile. */
    fun refresh() = startRefresh(restart = false)

    fun selectTab(tab: AppDetailTabs) {
        requestedTab.value = tab
    }

    fun setSearchMode(enabled: Boolean) {
        if (enabled && !search.value.isSearchMode) analyticsHelper.logSearchButtonClicked()
        search.value = if (enabled) search.value.copy(isSearchMode = true) else Search()
    }

    fun search(keyword: String) {
        search.update { it.copy(keyword = keyword) }
    }

    fun setSelectionMode(enabled: Boolean) {
        selection.value = if (enabled) selection.value.copy(active = true) else Selection()
    }

    fun toggleSelected(id: ComponentId) {
        selection.update { it.copy(ids = if (id in it.ids) it.ids - id else it.ids + id) }
    }

    /** Selects every row of the selected tab, or deselects them when all are selected. */
    fun toggleSelectAllInTab() {
        val state = uiState.value
        val ids = (state.components as? ComponentsUiState.Loaded)?.components?.of(state.tabs.selectedItem)?.map { it.id }.orEmpty()
        selection.update { current ->
            current.copy(ids = if (current.ids.containsAll(ids)) current.ids - ids.toSet() else current.ids + ids)
        }
    }

    /** Asks for root or Shizuku access, which may show a prompt. */
    fun grantAccess() = viewModelScope.launch {
        if (channel.connect() !is ChannelState.Ready) message.value = ListMessage.GrantFailed
    }

    /**
     * Starts [activity]. The system accepts a start that the Intent Firewall then blocks, so a
     * firewall block known from the repository is reported instead of a launch.
     */
    fun launchActivity(activity: ComponentId) = viewModelScope.launch {
        analyticsHelper.logStartActivityClicked()
        val key = ComponentKey(activity, app.userId)
        val result = appControl.startActivity(key)
        if (result is CallResult.Failure) {
            Timber.w("Cannot launch $activity: ${result.reason}")
            message.value = ListMessage.LaunchFailed(activity.simpleName, result.reason.toActionFailure())
            return@launch
        }
        val availability = componentStatuses.component(key).first()?.availability
        when {
            availability is Availability.Blocked && BlockReason.IFW_RULE in availability.reasons ->
                message.value = ListMessage.LaunchBlockedByRule(activity.simpleName)

            availability == Availability.Conditional -> message.value = ListMessage.LaunchMayBeBlocked(activity.simpleName)
        }
    }

    fun stopService(service: ComponentId) = viewModelScope.launch {
        analyticsHelper.logStopServiceClicked()
        val result = serviceControl.stop(ComponentKey(service, app.userId))
        if (result is CallResult.Failure) {
            Timber.w("Cannot stop $service: ${result.reason}")
            message.value = ListMessage.StopFailed(service.simpleName, result.reason.toActionFailure())
        }
        readRunning()
    }

    fun onMessageShown() {
        message.value = null
    }

    private fun startRefresh(restart: Boolean) {
        if (!restart && refreshJob?.isActive == true) return
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch {
            refresh.value = RefreshStatus.Running
            val result = componentStatuses.refresh(app)
            readRunning()
            refresh.value = when (result) {
                is ReadResult.Success -> RefreshStatus.Done

                is ReadResult.Failure -> {
                    Timber.w("Cannot refresh $app: ${result.reason}")
                    message.value = ListMessage.RefreshFailed(result.reason)
                    RefreshStatus.Failed(result.reason)
                }
            }
        }
    }

    private suspend fun readRunning() {
        // Reading connects the channel, which may prompt; showing a list never asks.
        val result = if (channel.state.value is ChannelState.Ready) serviceControl.runningServices(app) else null
        if (result is CallResult.Failure) Timber.d("No running services of $app: ${result.reason}")
        running.value = (result as? CallResult.Success)?.value
    }

    private fun descriptionsOf(names: Set<String>): Flow<Map<String, String>> = flow {
        var descriptions = buildMap {
            names.forEach { name -> componentDetails.getLocalComponentDetail(name).first()?.description?.takeIf { it.isNotBlank() }?.let { put(name, it) } }
        }
        emit(descriptions)
        // Edits the user saves in the description dialog.
        componentDetails.updatedComponent.collect { detail ->
            if (detail.name !in names) return@collect
            val description = detail.description?.takeIf { it.isNotBlank() }
            descriptions = if (description == null) descriptions - detail.name else descriptions + (detail.name to description)
            emit(descriptions)
        }
    }

    private data class Search(val isSearchMode: Boolean = false, val keyword: String = "")

    private data class Selection(val active: Boolean = false, val ids: Set<ComponentId> = emptySet())

    private data class Lists(
        val rows: List<ComponentRow>,
        val components: ComponentsUiState,
        val search: Search,
        val isRefreshing: Boolean,
    )

    private sealed interface RefreshStatus {
        data object Running : RefreshStatus
        data object Done : RefreshStatus
        data class Failed(val reason: ReadFailure) : RefreshStatus
    }

    /** What the privileged channel allows, as far as it changes what a refresh reads. */
    private sealed interface Access {
        data object Unchecked : Access
        data object None : Access
        data class Ready(val state: ChannelState.Ready) : Access
    }

    private fun accessOf(state: ChannelState): Access = when (state) {
        is ChannelState.Ready -> Access.Ready(state)
        is ChannelState.Unavailable -> if (state.reason == UnavailableReason.NOT_CHECKED) Access.Unchecked else Access.None
    }

    @AssistedFactory
    interface Factory {
        fun create(
            @Assisted("packageName") packageName: String,
            userId: Int?,
            @Assisted("tab") tab: String,
            searchKeyword: List<String>,
        ): ComponentListViewModel
    }

    private companion object {
        const val RUNNING_REREAD_DELAY_MILLIS = 300L

        fun tabOf(name: String): AppDetailTabs = try {
            AppDetailTabs.fromName(name)
        } catch (e: IllegalArgumentException) {
            Timber.w(e, "Unknown tab $name, show the app info")
            AppDetailTabs.Info
        }
    }
}

/** How a failed call is explained to the user. */
internal fun CallFailure.toActionFailure(): ActionFailure = when (this) {
    CallFailure.ChannelUnavailable -> ActionFailure.NoAccess
    is CallFailure.NotPermitted -> ActionFailure.NotPermitted
    is CallFailure.TargetNotFound -> ActionFailure.NotFound
    is CallFailure.SystemError -> ActionFailure.SystemError(message)
}
