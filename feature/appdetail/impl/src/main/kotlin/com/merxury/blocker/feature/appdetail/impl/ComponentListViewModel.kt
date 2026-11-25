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

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.merxury.blocker.core.analytics.AnalyticsHelper
import com.merxury.blocker.core.data.device.ComponentActions
import com.merxury.blocker.core.data.device.ComponentStatusRepository
import com.merxury.blocker.core.data.device.SelectedUserRepository
import com.merxury.blocker.core.data.repository.componentdetail.ComponentDetailRepository
import com.merxury.blocker.core.data.repository.generalrule.GeneralRuleRepository
import com.merxury.blocker.core.data.repository.userdata.UserDataRepository
import com.merxury.blocker.core.dispatchers.BlockerDispatchers.DEFAULT
import com.merxury.blocker.core.dispatchers.BlockerDispatchers.IO
import com.merxury.blocker.core.dispatchers.Dispatcher
import com.merxury.blocker.core.extension.debouncedSearchQuery
import com.merxury.blocker.core.model.component.ComponentId
import com.merxury.blocker.core.model.component.ComponentKey
import com.merxury.blocker.core.model.component.ComponentStatus
import com.merxury.blocker.core.model.preference.UserPreferenceData
import com.merxury.blocker.core.model.privileged.CallFailure
import com.merxury.blocker.core.model.privileged.CallResult
import com.merxury.blocker.core.model.privileged.ChannelState
import com.merxury.blocker.core.model.privileged.UnavailableReason
import com.merxury.blocker.core.model.system.ReadFailure
import com.merxury.blocker.core.model.system.ReadResult
import com.merxury.blocker.core.privileged.PrivilegedChannel
import com.merxury.blocker.core.ui.AppDetailTabs
import com.merxury.blocker.core.ui.component.ComponentRow
import com.merxury.blocker.core.ui.component.toRow
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
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber

/**
 * The component lists of one app for one user, with search, tabs and selection, and the actions
 * on a single running component. Every row shows what the component repository holds: after a
 * change elsewhere the repository's flow updates the rows, never a copy kept here.
 */
@HiltViewModel(assistedFactory = ComponentListViewModel.Factory::class)
class ComponentListViewModel @AssistedInject constructor(
    selectedUsers: SelectedUserRepository,
    private val componentStatuses: ComponentStatusRepository,
    private val componentActions: ComponentActions,
    private val channel: PrivilegedChannel,
    private val componentDetails: ComponentDetailRepository,
    generalRules: GeneralRuleRepository,
    userDataRepository: UserDataRepository,
    private val analyticsHelper: AnalyticsHelper,
    @Dispatcher(DEFAULT) cpuDispatcher: CoroutineDispatcher,
    @Dispatcher(IO) private val ioDispatcher: CoroutineDispatcher,
    @Assisted("packageName") packageName: String,
    @Assisted userId: Int?,
    @Assisted("tab") tab: String,
    @Assisted searchKeyword: List<String>,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {
    private val app = selectedUsers.appKeyOf(packageName, userId)
    private val initialKeyword = searchKeyword.map { it.trim() }.filter { it.isNotEmpty() }.joinToString(",")
    private val typedQuery = savedStateHandle.getMutableStateFlow("componentSearchQuery", initialKeyword.ifEmpty { null })

    /** The current field text. Null when component search is closed. */
    val searchQuery: StateFlow<String?> = typedQuery.asStateFlow()
    private val requestedTab = MutableStateFlow(tabOf(tab))
    private val selection = MutableStateFlow(Selection())
    private val refresh = MutableStateFlow<RefreshStatus?>(null)

    // What the system last answered; null when it could not be read.
    private val running = MutableStateFlow<Set<ComponentId>?>(null)
    private val message = MutableStateFlow<ListMessage?>(null)
    private var refreshJob: Job? = null
    private var accessCheckJob: Job? = null

    // The access keepFresh last saw, and whether it refreshed since the screen was opened.
    private var lastAccess: Access? = null
    private var refreshedOnce = false

    // Shared, as the rows, the descriptions and keepFresh all follow it: one query of the cache.
    private val statuses: Flow<List<ComponentStatus>> = componentStatuses.components(app)
        .flowOn(ioDispatcher)
        .shareIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), replay = 1)

    @OptIn(ExperimentalCoroutinesApi::class)
    private val descriptions: Flow<Map<String, String>> = statuses
        .map { list -> list.mapTo(mutableSetOf()) { it.declaration.id.className } }
        .distinctUntilChanged()
        .flatMapLatest(componentDetails::getLocalComponentDetails)
        .map { details -> details.mapNotNull { (name, detail) -> detail.description?.takeIf(String::isNotBlank)?.let { name to it } }.toMap() }
        .onStart { emit(emptyMap()) }
        .flowOn(ioDispatcher)

    // In the repository's order, by class name, as soon as the cache has them: descriptions and
    // running services fill in later. Shared, so every list below is made from one copy.
    private val rows: Flow<List<ComponentRow>> = combine(statuses, descriptions, running) { statuses, descriptions, running ->
        statuses.map { it.toRow(descriptions[it.declaration.id.className], running) }
    }
        .distinctUntilChanged()
        .flowOn(cpuDispatcher)
        .shareIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), replay = 1)

    // Only the preferences that order the rows, so another change of the settings sorts nothing.
    private val sortPreferences: Flow<UserPreferenceData> = userDataRepository.userData.distinctUntilChanged { old, new ->
        old.componentSorting == new.componentSorting && old.componentSortingOrder == new.componentSortingOrder && old.componentShowPriority == new.componentShowPriority
    }

    // Sorted again only when the rows or their order change, not for a search or a refresh.
    private val sorted: Flow<AppComponents> = combine(rows, sortPreferences, ::appComponents)

    private val loading: Flow<Boolean> = combine(rows.map { it.isEmpty() }.distinctUntilChanged(), refresh) { empty, refresh ->
        empty && (refresh == null || refresh == RefreshStatus.Running)
    }.distinctUntilChanged()

    private val keywords = searchQuery.debouncedSearchQuery().map(::keywordsOf).distinctUntilChanged()
    private val components: Flow<ComponentsUiState> = combine(sorted, keywords, loading) { sorted, keywords, loading ->
        if (loading) ComponentsUiState.Loading else ComponentsUiState.Loaded(sorted.matching(keywords))
    }

    // From the rows alone, so a search or a refresh matches nothing again. Loading until the rules
    // are read and matched, which never holds the rows back.
    private val matchedRules: Flow<MatchedRulesUiState> = combine(rows, generalRules.getGeneralRules(), loading) { rows, rules, loading ->
        if (loading) MatchedRulesUiState.Loading else MatchedRulesUiState.Loaded(matchedRules(rules, rows))
    }
        .onStart { emit(MatchedRulesUiState.Loading) }
        .distinctUntilChanged()
        .flowOn(cpuDispatcher)

    private val lists: Flow<Lists> = combine(components, matchedRules, refresh, loading) { components, matched, refresh, loading ->
        Lists(
            components = components,
            matchedRules = matched,
            isRefreshing = !loading && refresh == RefreshStatus.Running,
            refreshFailure = (refresh as? RefreshStatus.Failed)?.reason,
            accessCheckFailed = refresh == RefreshStatus.AccessUnchecked,
        )
    }

    val uiState: StateFlow<ComponentListUiState> = combine(
        lists,
        requestedTab,
        selection,
        message,
    ) { lists, requestedTab, selection, message ->
        ComponentListUiState(
            tabs = tabsOf(lists.components, lists.matchedRules, requestedTab),
            components = lists.components,
            matchedRules = lists.matchedRules,
            isSelectionMode = selection.active,
            selected = selection.ids,
            isRefreshing = lists.isRefreshing,
            refreshFailure = lists.refreshFailure,
            accessCheckFailed = lists.accessCheckFailed,
            message = message,
        )
    }
        .flowOn(cpuDispatcher)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = ComponentListUiState(
                tabs = tabsOf(ComponentsUiState.Loading, MatchedRulesUiState.Loading, requestedTab.value),
            ),
        )

    /**
     * Keeps the rows current while the screen is shown. Refreshes the app once the privileged
     * channel's first access check has settled, never before: until then another user's app
     * cannot be read and the firewall state would be stored as unknown. Refreshes again whenever
     * access becomes ready, or ready as another identity, since that changes what can be read,
     * and reads the running services again after a change of the components' state. Returns
     * only when cancelled.
     */
    @OptIn(FlowPreview::class)
    suspend fun keepFresh(): Unit = coroutineScope {
        if (accessOf(channel.state.value) == Access.Unchecked) awaitAccessCheck()
        launch {
            channel.state.map(::accessOf).distinctUntilChanged().collect { access ->
                val previous = lastAccess
                lastAccess = access
                // A screen shown again, such as after a rotation, has nothing new to read.
                val settles = access == Access.None && !refreshedOnce
                val becomesReady = access is Access.Ready && access != previous
                if (settles || becomesReady) {
                    refreshedOnce = true
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

    /**
     * Reads the app again from the system; the cached rows stay shown meanwhile. Before the
     * channel's first access check has settled nothing can be read yet: this asks for that check,
     * after which [keepFresh] refreshes, see [awaitAccessCheck].
     */
    fun refresh() {
        if (accessOf(channel.state.value) == Access.Unchecked) {
            awaitAccessCheck()
            return
        }
        startRefresh(restart = false)
    }

    /**
     * Shows [tab]. Selection mode ends on a tab without selectable rows, and search mode on the
     * matched rules tab, which shows no search; Back then leaves the screen at once.
     */
    fun selectTab(tab: AppDetailTabs) {
        requestedTab.value = tab
        if (!tab.listsComponents) selection.value = Selection()
        if (tab == AppDetailTabs.Sdk) typedQuery.value = null
    }

    fun setSearchMode(enabled: Boolean) {
        if (enabled && typedQuery.value == null) analyticsHelper.logSearchButtonClicked()
        typedQuery.value = if (enabled) typedQuery.value.orEmpty() else null
    }

    fun search(keyword: String) {
        typedQuery.value = keyword
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

    /** Starts [activity]; a blocked one stays blocked, as its switch shows. */
    fun launchActivity(activity: ComponentId) = viewModelScope.launch {
        analyticsHelper.logStartActivityClicked()
        val result = componentActions.startActivity(ComponentKey(activity, app.userId))
        if (result is CallResult.Failure) {
            Timber.w("Cannot launch $activity: ${result.reason}")
            message.value = ListMessage.LaunchFailed(activity.simpleName, result.reason.toActionFailure())
        }
    }

    fun stopService(service: ComponentId) = viewModelScope.launch {
        analyticsHelper.logStopServiceClicked()
        val result = componentActions.stopService(ComponentKey(service, app.userId))
        if (result is CallResult.Failure) {
            Timber.w("Cannot stop $service: ${result.reason}")
            message.value = ListMessage.StopFailed(service.simpleName, result.reason.toActionFailure())
        }
        readRunning()
    }

    fun onMessageShown() {
        message.value = null
    }

    /**
     * Waits for the channel's check that cannot show a prompt; once it settles the access changes,
     * so [keepFresh] refreshes. A check that does not settle in time, such as while the controller
     * type cannot be read, is told instead of leaving the screen loading.
     */
    private fun awaitAccessCheck() {
        if (accessCheckJob?.isActive == true) return
        accessCheckJob = viewModelScope.launch {
            val settled = withTimeoutOrNull(ACCESS_CHECK_TIMEOUT_MILLIS) { channel.connectIfGranted() }
            if (settled == null || accessOf(settled) == Access.Unchecked) {
                Timber.w("The access check of the privileged channel did not settle for $app")
                if (refresh.value == null || refresh.value == RefreshStatus.Running) refresh.value = RefreshStatus.AccessUnchecked
                message.value = ListMessage.AccessCheckFailed
            }
        }
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
                    // Not told while the channel is checked again: its answer decides what can be read.
                    if (accessOf(channel.state.value) != Access.Unchecked) message.value = ListMessage.RefreshFailed(result.reason)
                    RefreshStatus.Failed(result.reason)
                }
            }
        }
    }

    private suspend fun readRunning() {
        // Reading connects the channel, which may prompt; showing a list never asks.
        val result = if (channel.state.value is ChannelState.Ready) componentActions.runningServices(app) else null
        if (result is CallResult.Failure) Timber.d("No running services of $app: ${result.reason}")
        running.value = (result as? CallResult.Success)?.value
    }

    private data class Selection(val active: Boolean = false, val ids: Set<ComponentId> = emptySet())

    private data class Lists(
        val components: ComponentsUiState,
        val matchedRules: MatchedRulesUiState,
        val isRefreshing: Boolean,
        val refreshFailure: ReadFailure?,
        val accessCheckFailed: Boolean,
    )

    private sealed interface RefreshStatus {
        data object Running : RefreshStatus
        data object Done : RefreshStatus
        data class Failed(val reason: ReadFailure) : RefreshStatus

        /** Nothing was read: the channel's first access check did not settle. */
        data object AccessUnchecked : RefreshStatus
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

        /** How long the first access check may take before the screen says it could not be done. */
        const val ACCESS_CHECK_TIMEOUT_MILLIS = 10_000L

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
