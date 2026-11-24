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

package com.merxury.blocker.feature.search.impl

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.merxury.blocker.core.analytics.AnalyticsHelper
import com.merxury.blocker.core.data.device.ComponentStatusRepository
import com.merxury.blocker.core.data.device.InstalledAppRepository
import com.merxury.blocker.core.data.device.SelectedUserRepository
import com.merxury.blocker.core.data.respository.userdata.UserDataRepository
import com.merxury.blocker.core.di.AppPackageName
import com.merxury.blocker.core.dispatchers.BlockerDispatchers.DEFAULT
import com.merxury.blocker.core.dispatchers.Dispatcher
import com.merxury.blocker.core.domain.SearchGeneralRuleUseCase
import com.merxury.blocker.core.domain.applist.CountServicesUseCase
import com.merxury.blocker.core.domain.applist.PerformAppActionUseCase
import com.merxury.blocker.core.domain.applist.ReadRunningPackagesUseCase
import com.merxury.blocker.core.domain.control.BatchKey
import com.merxury.blocker.core.domain.control.ComponentBatchRunner
import com.merxury.blocker.core.domain.control.ComponentBatchRunner.Companion.BATCH_LIMIT
import com.merxury.blocker.core.domain.control.GetControlLayersUseCase
import com.merxury.blocker.core.domain.search.ComponentIndex
import com.merxury.blocker.core.domain.search.IndexState
import com.merxury.blocker.core.model.component.AppKey
import com.merxury.blocker.core.model.component.ComponentId
import com.merxury.blocker.core.model.component.ComponentKey
import com.merxury.blocker.core.model.component.ComponentStatus
import com.merxury.blocker.core.model.component.ServiceCounts
import com.merxury.blocker.core.model.component.UserId
import com.merxury.blocker.core.model.control.ControlGoal
import com.merxury.blocker.core.model.data.GeneralRule
import com.merxury.blocker.core.model.privileged.ChannelState
import com.merxury.blocker.core.model.privileged.PrivilegedIdentity
import com.merxury.blocker.core.model.privileged.UnavailableReason
import com.merxury.blocker.core.model.system.AppAction
import com.merxury.blocker.core.model.system.ReadResult
import com.merxury.blocker.core.privileged.PrivilegedChannel
import com.merxury.blocker.core.ui.ProcessingProgress
import com.merxury.blocker.core.ui.applist.AppActionFailed
import com.merxury.blocker.core.ui.applist.appRows
import com.merxury.blocker.core.ui.control.batchSummary
import com.merxury.blocker.core.ui.profile.toProfileTab
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/**
 * Searches the apps, the components and the general rules for the selected user, and acts on
 * what it finds. Every result shows what the repositories hold: after an action or a batch the
 * repositories' flows update the results, never a copy kept here.
 *
 * Components are found among the apps whose components are cached. The first time the
 * components tab is shown with a keyword for a user, every app of that user is read into the
 * cache, see [ComponentIndex], while the results already found stay shown.
 */
@HiltViewModel
class SearchViewModel @Inject internal constructor(
    private val selectedUsers: SelectedUserRepository,
    private val installedApps: InstalledAppRepository,
    private val componentStatuses: ComponentStatusRepository,
    private val componentIndex: ComponentIndex,
    private val batches: ComponentBatchRunner,
    private val layersFor: GetControlLayersUseCase,
    private val searchRules: SearchGeneralRuleUseCase,
    private val performAppAction: PerformAppActionUseCase,
    private val readRunningPackages: ReadRunningPackagesUseCase,
    private val countAppServices: CountServicesUseCase,
    private val channel: PrivilegedChannel,
    private val userDataRepository: UserDataRepository,
    private val analyticsHelper: AnalyticsHelper,
    @Dispatcher(DEFAULT) cpuDispatcher: CoroutineDispatcher,
    @AppPackageName appPackageName: String,
) : ViewModel() {
    private val self = AppKey(appPackageName, selectedUsers.ownUser)
    private val typed = MutableStateFlow("")
    private val tab = MutableStateFlow(SearchTab.APPS)
    private val selection = MutableStateFlow(Selection())

    // What the system last answered for each user; absent when it could not be read.
    private val running = MutableStateFlow<Map<UserId, Set<String>>>(emptyMap())
    private val serviceCounts = MutableStateFlow<Map<AppKey, ServiceCounts>>(emptyMap())
    private val queued = MutableStateFlow<List<QueuedSearchMessage>>(emptyList())
    private var nextMessageId = 0L

    // Users whose apps this screen has read from the system.
    private val refreshedUsers = mutableSetOf<UserId>()

    /** The keyword searched for: the typed one once typing pauses, trimmed; at once when cleared. */
    @OptIn(FlowPreview::class)
    private val query: Flow<String> = typed
        .map { it.trim() }
        .debounce { if (it.isEmpty()) 0L else TYPING_PAUSE_MILLIS }
        .distinctUntilChanged()

    // The selected user's components that a batch of any screen changes now.
    private val inFlight: Flow<Set<ComponentId>> = combine(selectedUsers.selectedUser, batches.inFlight) { user, keys ->
        keys.filter { it.userId == user }.mapTo(mutableSetOf()) { it.id }
    }.distinctUntilChanged()

    @OptIn(ExperimentalCoroutinesApi::class)
    private val batch: Flow<ProcessingProgress?> = selectedUsers.selectedUser.flatMapLatest { user ->
        batches.running(BatchKey.Search(user)).flatMapLatest { run ->
            run?.step?.map { step ->
                // A batch of one request reports only once it ends.
                val done = step.done.takeIf { step.total > BATCH_LIMIT }
                ProcessingProgress(current = done ?: 0, total = step.total, isEnabling = run.goal != ControlGoal.BLOCK, isIndeterminate = done == null)
            } ?: flowOf(null)
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private val results: Flow<Pair<String, SearchResults>> = combine(query, selectedUsers.selectedUser, ::Pair)
        .flatMapLatest { (keyword, user) ->
            if (keyword.isEmpty()) {
                flowOf(keyword to SearchResults.Idle)
            } else {
                combine(appResults(user, keyword), componentResults(user, keyword), ruleResults(user, keyword)) { apps, components, rules ->
                    keyword to SearchResults.Loaded(
                        apps = apps.first,
                        showServiceInfo = apps.second,
                        components = components,
                        matchedRules = rules.orEmpty().filter { it.matchedAppCount > 0 },
                        unmatchedRules = rules.orEmpty().filter { it.matchedAppCount == 0 },
                        rulesLoading = rules == null,
                    )
                }.onStart<Pair<String, SearchResults>> { emit(keyword to SearchResults.Loading) }
            }
        }

    @OptIn(ExperimentalCoroutinesApi::class)
    private val indexState: Flow<IndexState> = selectedUsers.selectedUser.flatMapLatest { componentIndex.state(it) }

    private val activity = combine(batch, inFlight, indexState, ::Activity)

    private val controls = combine(selection, activity, queued, ::Controls)

    val uiState: StateFlow<SearchUiState> = combine(selectedUsers.users, selectedUsers.selectedUser, results, tab, controls) { users, user, (keyword, results), tab, controls ->
        // A selection made for another user has ended: the user changed elsewhere or fell back.
        val selection = controls.selection.takeIf { it.user == user } ?: Selection()
        val activity = controls.activity
        SearchUiState(
            ownUser = selectedUsers.ownUser,
            selectedUser = user,
            profiles = users.map { it.toProfileTab() },
            keyword = keyword,
            tabs = tabsOf(results, tab),
            results = results,
            isSelectionMode = selection.active,
            selected = selection.ids,
            selectionActionsEnabled = activity.batch == null && selection.ids.isNotEmpty() && selection.ids.none { it in activity.inFlight },
            batch = activity.batch,
            indexing = (activity.index as? IndexState.Indexing)?.let { IndexingProgress(it.done, it.total) },
            messages = controls.messages,
        )
    }
        .flowOn(cpuDispatcher)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = SearchUiState(ownUser = selectedUsers.ownUser, selectedUser = selectedUsers.selectedUser.value),
        )

    /**
     * Keeps the results current while the screen is shown. Once the privileged channel's first
     * access check has ended, reads the selected user's apps from the system the first time this
     * screen shows that user, and which of them run each time the user or the access changes.
     * Indexes the user's components the first time the components tab is shown with a keyword,
     * and again when it ran with less access than the channel has now. Returns only when
     * cancelled.
     */
    suspend fun keepFresh(): Unit = coroutineScope {
        launch {
            combine(selectedUsers.selectedUser, channel.state.map(::accessOf).distinctUntilChanged(), ::Pair).collect { (user, access) ->
                if (access == Access.Unchecked) return@collect
                if (user !in refreshedUsers && installedApps.refresh(user) is ReadResult.Success) refreshedUsers += user
                readRunning(user)
            }
        }
        launch {
            combine(tab, query, selectedUsers.selectedUser, channel.state) { tab, keyword, user, _ ->
                user.takeIf { tab == SearchTab.COMPONENTS && keyword.isNotEmpty() }
            }
                .filterNotNull()
                .collect(componentIndex::ensureIndexed)
        }
    }

    /** Searches for [keyword] once typing pauses; the previous search is dropped. */
    fun search(keyword: String) {
        typed.value = keyword
    }

    /** Shows [tab]. Selection mode ends on a tab other than the components. */
    fun selectTab(tab: SearchTab) {
        this.tab.value = tab
        if (tab != SearchTab.COMPONENTS) selection.value = Selection()
    }

    /** Searches the apps of [user]; a selection of another user's components ends. */
    fun selectUser(user: UserId) {
        selectedUsers.select(user)
        if (selectedUsers.selectedUser.value == user) selection.value = Selection()
    }

    /** Reads every app of the selected user into the cache again, as a pull to refresh asks. */
    fun refreshComponents() {
        componentIndex.reindex(selectedUsers.selectedUser.value)
    }

    /** Asks for root or Shizuku access, which may show a prompt. */
    fun grantAccess() = viewModelScope.launch {
        if (channel.connect() !is ChannelState.Ready) Timber.i("Access was not granted from the search")
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

    /** Runs [action] on [app], as the app list does, and tells a failure. */
    fun perform(action: AppAction, app: AppKey) = viewModelScope.launch {
        val label = installedApps.app(app).first()?.label ?: app.packageName
        val failure = performAppAction(app, action)
        analyticsHelper.logAppAction(action)
        if (failure != null) tell(SearchMessage.AppFailed(AppActionFailed(action, label, failure)))
        // Counted again when the row is next shown, as the action may have stopped services.
        serviceCounts.update { it - app }
        readRunning(app.userId)
    }

    fun setSelectionMode(enabled: Boolean) {
        selection.value = if (enabled) currentSelection().copy(active = true) else Selection()
    }

    /** Selects every component found in [app], or deselects them when all are selected. */
    fun toggleSelectedApp(app: AppKey) = toggleAll(foundComponents().filter { it.packageName == app.packageName })

    /** Selects every component found, or deselects them when all are selected. */
    fun toggleSelectAll() = toggleAll(foundComponents())

    /** Blocks the selected components through the layers of the controller type; one message tells how it ended. */
    fun blockSelected() = startBatch(ControlGoal.BLOCK)

    /**
     * Removes every block of the selected components, through every layer; one message tells how
     * it ended. Components the app ships disabled stay as they are, as with a batch in the app's
     * detail screen.
     */
    fun enableSelected() = startBatch(ControlGoal.UNBLOCK)

    fun onMessageShown(id: Long) {
        queued.update { messages -> messages.filterNot { it.id == id } }
    }

    private fun toggleAll(ids: List<ComponentId>) {
        val current = currentSelection()
        selection.value = current.copy(ids = if (current.ids.containsAll(ids)) current.ids - ids.toSet() else current.ids + ids)
    }

    /** The selection of the selected user; one made for another user has ended. */
    private fun currentSelection(): Selection {
        val user = selectedUsers.selectedUser.value
        return selection.value.takeIf { it.user == user } ?: Selection(user = user)
    }

    private fun foundComponents(): List<ComponentId> = (uiState.value.results as? SearchResults.Loaded)?.components.orEmpty().flatMap { group -> group.components.map { it.id } }

    /**
     * Sends the selection as one batch through [batches], so it ends even when the screen goes
     * away. Selection mode ends at once; the batch counts as running from the moment it is
     * chosen, before the cache and the settings are read, and nothing starts while another batch
     * of the search runs for the user. The batch acts on the user the selection was made for, and
     * not at all once another user is selected, so it never reaches components not chosen there.
     */
    private fun startBatch(goal: ControlGoal) {
        val chosen = selection.value
        selection.value = Selection()
        val user = chosen.user ?: return
        if (user != selectedUsers.selectedUser.value) {
            Timber.i("The selection was made for user ${user.value}, which is no longer selected; nothing is changed")
            return
        }
        val ids = chosen.ids.toList()
        if (ids.isEmpty()) return
        val run = batches.reserve(BatchKey.Search(user), goal, ids) ?: return
        analyticsHelper.logBatchOperationPerformed(enable = goal != ControlGoal.BLOCK)
        viewModelScope.launch {
            var started = false
            try {
                val statuses = cachedStatuses(user, ids)
                val vanished = (ids - statuses.mapTo(mutableSetOf()) { it.declaration.id }).map { ComponentKey(it, user) }
                batches.start(run, statuses.map { it.declaration }, layersFor(goal))
                started = true
                tell(SearchMessage.Control(batchSummary(run.outcomes.await(), vanished)))
            } finally {
                if (!started) batches.release(run)
            }
        }
    }

    private suspend fun cachedStatuses(user: UserId, ids: List<ComponentId>): List<ComponentStatus> {
        val cached = ids.map { it.packageName }.distinct()
            .flatMap { packageName -> componentStatuses.components(AppKey(packageName, user)).first() }
            .associateBy { it.declaration.id }
        return ids.mapNotNull { id ->
            cached[id] ?: run {
                Timber.w("$id is no longer cached for user ${user.value}, tell it as not found")
                null
            }
        }
    }

    private suspend fun readRunning(user: UserId) {
        val packages = readRunningPackages(user)
        running.update { if (packages != null) it + (user to packages) else it - user }
    }

    private fun tell(message: SearchMessage) {
        queued.update { it + QueuedSearchMessage(nextMessageId++, message) }
    }

    private fun appResults(user: UserId, keyword: String) = combine(
        installedApps.search(user, keyword),
        running.map { it[user] }.distinctUntilChanged(),
        serviceCounts,
        userDataRepository.userData,
    ) { apps, running, counts, preferences -> appRows(apps, running, counts, preferences, self) to preferences.showServiceInfo }

    /**
     * The rules with their matches for [user], or null until the rules are matched, which the
     * first time takes longer than the apps and components need, so they do not wait for it.
     */
    private fun ruleResults(user: UserId, keyword: String): Flow<List<GeneralRule>?> = searchRules(user, keyword)
        .map<List<GeneralRule>, List<GeneralRule>?> { it }
        .onStart { emit(null) }

    private fun componentResults(user: UserId, keyword: String) = combine(
        componentStatuses.search(user, keyword),
        installedApps.apps(user),
        userDataRepository.userData.map { it.showSystemApps }.distinctUntilChanged(),
    ) { found, apps, showSystemApps -> componentGroups(found, apps, showSystemApps, self) }

    /** Components chosen in selection mode, of the [user] selected when the selection began. */
    private data class Selection(val active: Boolean = false, val ids: Set<ComponentId> = emptySet(), val user: UserId? = null)

    private data class Activity(
        val batch: ProcessingProgress?,
        val inFlight: Set<ComponentId>,
        val index: IndexState,
    )

    private data class Controls(
        val selection: Selection,
        val activity: Activity,
        val messages: List<QueuedSearchMessage>,
    )

    /** Whether the privileged channel's first access check has ended, and with what access. */
    private sealed interface Access {
        data object Unchecked : Access
        data object None : Access
        data class Ready(val identity: PrivilegedIdentity) : Access
    }

    private fun accessOf(state: ChannelState): Access = when (state) {
        is ChannelState.Ready -> Access.Ready(state.identity)
        is ChannelState.Unavailable -> if (state.reason == UnavailableReason.NOT_CHECKED) Access.Unchecked else Access.None
    }

    private companion object {
        const val TYPING_PAUSE_MILLIS = 300L
        const val SHOWN_BEFORE_COUNTING_MILLIS = 300L
    }
}
