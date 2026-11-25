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

package com.merxury.blocker.feature.debloater.impl

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.merxury.blocker.core.analytics.AnalyticsHelper
import com.merxury.blocker.core.data.device.ComponentStatusRepository
import com.merxury.blocker.core.data.device.SelectedUserRepository
import com.merxury.blocker.core.data.util.PermissionMonitor
import com.merxury.blocker.core.data.util.PermissionStatus
import com.merxury.blocker.core.dispatchers.BlockerDispatchers.DEFAULT
import com.merxury.blocker.core.dispatchers.Dispatcher
import com.merxury.blocker.core.domain.control.BatchKey
import com.merxury.blocker.core.domain.control.ComponentBatchRunner
import com.merxury.blocker.core.domain.control.ControlComponentUseCase
import com.merxury.blocker.core.domain.control.GetControlLayersUseCase
import com.merxury.blocker.core.domain.search.ComponentIndex
import com.merxury.blocker.core.domain.search.IndexState
import com.merxury.blocker.core.extension.debouncedSearchQuery
import com.merxury.blocker.core.model.component.AppKey
import com.merxury.blocker.core.model.component.ComponentId
import com.merxury.blocker.core.model.component.ComponentKey
import com.merxury.blocker.core.model.component.UserId
import com.merxury.blocker.core.model.control.ControlGoal
import com.merxury.blocker.core.model.control.ControlRequest
import com.merxury.blocker.core.privileged.PrivilegedChannel
import com.merxury.blocker.core.result.Result
import com.merxury.blocker.core.ui.control.ControlMessage
import com.merxury.blocker.core.ui.control.QueuedControlMessage
import com.merxury.blocker.core.ui.control.batchSummary
import com.merxury.blocker.core.ui.control.enqueue
import com.merxury.blocker.core.ui.control.toMessage
import com.merxury.blocker.core.ui.data.UiMessage
import com.merxury.blocker.core.ui.data.toErrorMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.ExperimentalForInheritanceCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

data class DebloaterUiState(
    val content: Result<List<MatchedTarget>> = Result.Loading,
    val componentTypeFilter: Set<ComponentClassification> = emptySet(),
    val hasRootPermission: Boolean? = null,
    val inFlight: Set<ComponentKey> = emptySet(),
    val messages: List<QueuedControlMessage> = emptyList(),
    val alert: UiMessage? = null,
)

@HiltViewModel
@OptIn(ExperimentalCoroutinesApi::class)
class DebloaterViewModel @Inject constructor(
    private val selectedUsers: SelectedUserRepository,
    private val matches: DebloaterMatches,
    private val componentIndex: ComponentIndex,
    private val componentStatuses: ComponentStatusRepository,
    private val batches: ComponentBatchRunner,
    private val controlComponent: ControlComponentUseCase,
    private val layersFor: GetControlLayersUseCase,
    private val channel: PrivilegedChannel,
    permissionMonitor: PermissionMonitor,
    private val analyticsHelper: AnalyticsHelper,
    @Dispatcher(DEFAULT) cpuDispatcher: CoroutineDispatcher,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {
    private val typedQuery = savedStateHandle.getMutableStateFlow("debloaterSearchQuery", "")
    val searchQuery: StateFlow<String> = typedQuery.asStateFlow()
    private val kinds = MutableStateFlow<Set<ComponentClassification>>(emptySet())
    private val filters = combine(searchQuery.debouncedSearchQuery(), kinds, ::Filters)
    private val singles = MutableStateFlow<Set<ComponentKey>>(emptySet())
    private val feedback = MutableStateFlow<Map<UserId, Feedback>>(emptyMap())
    private val loadedContent = mutableMapOf<UserId, Result.Success<List<MatchedTarget>>>()
    private var nextMessageId = 0L

    private val content = selectedUsers.selectedUser.flatMapLatest { user ->
        content(user).map { user to it }
    }.combine(filters) { (user, content), filters ->
        FilteredContent(user, content.filteredBy(filters), filters.kinds)
    }.flowOn(cpuDispatcher)

    private val inFlight = combine(singles, batches.inFlight) { singles, pending -> singles + pending }

    private val permission = permissionMonitor.permissionStatus
        .map<PermissionStatus, Boolean?> { it != PermissionStatus.NO_PERMISSION }
        .catch {
            Timber.e(it, "Cannot read debloater permissions")
            emit(null)
        }
        .onStart { emit(null) }

    private val initialState = DebloaterUiState()
    private val userUiState = combine(
        selectedUsers.selectedUser,
        content,
        inFlight,
        feedback,
        permission,
    ) { user, filtered, inFlight, feedback, hasRootPermission ->
        val feedbackForUser = feedback[user]
        val state = DebloaterUiState(
            content = filtered.content.takeIf { filtered.user == user } ?: Result.Loading,
            componentTypeFilter = filtered.kinds,
            hasRootPermission = hasRootPermission,
            inFlight = inFlight,
            messages = feedbackForUser?.messages.orEmpty(),
            alert = feedbackForUser?.alert,
        )
        UserUiState(user, state)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UserUiState(selectedUsers.selectedUser.value, initialState))

    // The selected user can change while the upstream is stopped and its replay still holds the old rows.
    @OptIn(ExperimentalForInheritanceCoroutinesApi::class)
    val uiState: StateFlow<DebloaterUiState> = object : StateFlow<DebloaterUiState> {
        override val value get() = userUiState.value.forSelectedUser()
        override val replayCache get() = listOf(value)

        override suspend fun collect(collector: FlowCollector<DebloaterUiState>): Nothing {
            var previous: DebloaterUiState? = null
            return userUiState.collect(
                FlowCollector { tagged ->
                    val current = tagged.forSelectedUser()
                    if (current != previous) {
                        collector.emit(current)
                        previous = current
                    }
                },
            )
        }
    }

    private fun UserUiState.forSelectedUser() = state.takeIf { user == selectedUsers.selectedUser.value } ?: initialState

    private fun content(user: UserId): Flow<Result<List<MatchedTarget>>> = combine(
        channel.state.onEach { componentIndex.ensureIndexed(user) },
        componentIndex.state(user),
    ) { _, index -> index }
        .flatMapLatest { index -> indexedContent(user, index) }
        .onStart { emit(loadedContent[user] ?: Result.Loading) }
        .onEach { if (it is Result.Success) loadedContent[user] = it }

    private fun indexedContent(user: UserId, index: IndexState): Flow<Result<List<MatchedTarget>>> = flow {
        when (index) {
            is IndexState.Indexed -> {
                if (index.appListFailure == null) {
                    emitAll(matches.forUser(user))
                } else {
                    error("Cannot list the apps of user ${user.value}: ${index.appListFailure}")
                }
            }

            is IndexState.Failed -> throw index.exception

            is IndexState.Indexing, IndexState.NotIndexed -> emit(loadedContent[user] ?: Result.Loading)
        }
    }.catch { error ->
        Timber.e(error, "Cannot load debloatable components of user ${user.value}")
        emit(loadedContent[user]?.takeIf { it.data.isNotEmpty() } ?: Result.Error(error))
    }

    fun updateSearchQuery(query: String) {
        typedQuery.value = query
        if (query.isNotBlank()) analyticsHelper.logSearchQueryUpdated()
    }

    fun updateComponentTypeFilter(kinds: Set<ComponentClassification>) {
        this.kinds.value = kinds
        analyticsHelper.logComponentTypeFilterChanged(kinds.size)
    }

    fun setEnabled(item: DebloatableComponentUiItem, enabled: Boolean) {
        val key = item.key
        if (key.userId != selectedUsers.selectedUser.value || isInFlight(key)) return
        val goal = if (enabled) ControlGoal.ENABLE else ControlGoal.BLOCK
        singles.update { it + key }
        viewModelScope.launch {
            try {
                analyticsHelper.logComponentControlled(
                    enabled = enabled,
                    isShareable = ComponentClassification.SHAREABLE in item.kinds,
                    isLauncherEntry = ComponentClassification.LAUNCHER in item.kinds,
                    isDeeplinkEntry = ComponentClassification.DEEPLINK in item.kinds,
                )
                controlComponent(key, goal).toMessage(key.id, goal)?.let { tell(it, key) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.e(e, "Cannot change ${key.id} for user ${key.userId.value}")
                tellFailure(key, goal)
            } finally {
                singles.update { it - key }
            }
        }
    }

    fun setAllEnabled(app: AppKey, items: List<DebloatableComponentUiItem>, enabled: Boolean) {
        if (app.userId != selectedUsers.selectedUser.value) return
        val targets = items.filter { it.key.userId == app.userId && it.key.id.packageName == app.packageName && !isInFlight(it.key) }
        if (targets.isEmpty()) return
        val ids = targets.map { it.key.id }.distinct()
        // Enable all has always enabled components explicitly, including ones shipped disabled.
        val goal = if (enabled) ControlGoal.ENABLE else ControlGoal.BLOCK
        val run = batches.reserve(BatchKey.Debloater(app), goal, ids) ?: return
        batches.start(run) {
            val cached = componentStatuses.components(app).first().associateBy { it.declaration.id }
            ControlRequest(app.userId, ids.mapNotNull { cached[it]?.declaration }, goal, layersFor(goal))
        }
        viewModelScope.launch {
            try {
                analyticsHelper.logBatchComponentsControlled(
                    enabled = enabled,
                    componentCount = targets.size,
                    shareableCount = targets.count { ComponentClassification.SHAREABLE in it.kinds },
                    launcherEntryCount = targets.count { ComponentClassification.LAUNCHER in it.kinds },
                    deeplinkEntryCount = targets.count { ComponentClassification.DEEPLINK in it.kinds },
                )
                val outcomes = run.outcomes.await()
                val reported = outcomes.mapTo(mutableSetOf()) { it.key.id }
                val missing = (ids - reported).map { ComponentKey(it, app.userId) }
                val summary = batchSummary(outcomes, missing)
                if (summary.notChanged > 0) tell(summary, app.userId)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.e(e, "Cannot change debloatable components of $app")
                updateFeedback(app.userId) { it.copy(alert = e.toErrorMessage()) }
            }
        }
    }

    fun onMessageShown(id: Long) {
        feedback.update { users ->
            users.mapValues { (_, feedback) -> feedback.copy(messages = feedback.messages.filterNot { it.id == id }) }
        }
    }

    fun grantAccess() {
        viewModelScope.launch { channel.connect() }
    }

    fun dismissError() {
        updateFeedback(selectedUsers.selectedUser.value) { it.copy(alert = null) }
    }

    fun onTestShare() {
        analyticsHelper.logTestShareTriggered()
    }

    private fun isInFlight(key: ComponentKey): Boolean = key in singles.value || batches.isInFlight(key)

    private fun tellFailure(key: ComponentKey, goal: ControlGoal) = tell(ControlMessage.ComponentNotChanged(key.id.simpleName, goal), key)

    private fun tell(message: ControlMessage, key: ComponentKey) = tell(message, key.userId, key.id)

    private fun tell(message: ControlMessage, user: UserId, component: ComponentId? = null) {
        if (user != selectedUsers.selectedUser.value) return
        val queued = QueuedControlMessage(nextMessageId++, message, component)
        updateFeedback(user) { it.copy(messages = it.messages.enqueue(queued)) }
    }

    private fun updateFeedback(user: UserId, update: (Feedback) -> Feedback) {
        feedback.update { users -> users + (user to update(users[user] ?: Feedback())) }
    }

    private fun Result<List<MatchedTarget>>.filteredBy(filters: Filters): Result<List<MatchedTarget>> {
        if (this !is Result.Success || (filters.query.isBlank() && filters.kinds.isEmpty())) return this
        return Result.Success(
            data.mapNotNull { app ->
                val labelMatches = filters.query.isBlank() || app.title.contains(filters.query, ignoreCase = true)
                val targets = app.targets.filter { item ->
                    (
                        labelMatches || item.className.contains(filters.query, ignoreCase = true) ||
                            item.displayName.contains(filters.query, ignoreCase = true)
                        ) &&
                        (filters.kinds.isEmpty() || filters.kinds.any { it in item.kinds })
                }
                app.copy(targets = targets).takeIf { targets.isNotEmpty() }
            },
        )
    }

    private data class Filters(val query: String, val kinds: Set<ComponentClassification>)
    private data class UserUiState(val user: UserId, val state: DebloaterUiState)
    private data class FilteredContent(val user: UserId, val content: Result<List<MatchedTarget>>, val kinds: Set<ComponentClassification>)
    private data class Feedback(val messages: List<QueuedControlMessage> = emptyList(), val alert: UiMessage? = null)
}
