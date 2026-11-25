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

package com.merxury.blocker.feature.generalrule.impl

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.merxury.blocker.core.analytics.AnalyticsHelper
import com.merxury.blocker.core.data.device.SelectedUserRepository
import com.merxury.blocker.core.data.repository.generalrule.GeneralRuleRepository
import com.merxury.blocker.core.data.repository.userdata.AppPropertiesRepository
import com.merxury.blocker.core.dispatchers.BlockerDispatchers.DEFAULT
import com.merxury.blocker.core.dispatchers.BlockerDispatchers.IO
import com.merxury.blocker.core.dispatchers.Dispatcher
import com.merxury.blocker.core.domain.InitializeRuleStorageUseCase
import com.merxury.blocker.core.domain.SearchGeneralRuleUseCase
import com.merxury.blocker.core.domain.control.BatchKey
import com.merxury.blocker.core.domain.control.BatchRun
import com.merxury.blocker.core.domain.control.ComponentBatchRunner
import com.merxury.blocker.core.domain.control.GetControlLayersUseCase
import com.merxury.blocker.core.domain.model.InitializeState
import com.merxury.blocker.core.domain.rule.GeneralRuleMatcher
import com.merxury.blocker.core.domain.search.ComponentIndex
import com.merxury.blocker.core.domain.search.IndexState
import com.merxury.blocker.core.model.component.ComponentDeclaration
import com.merxury.blocker.core.model.component.ComponentKey
import com.merxury.blocker.core.model.component.UserId
import com.merxury.blocker.core.model.control.ControlGoal
import com.merxury.blocker.core.model.data.GeneralRule
import com.merxury.blocker.core.model.privileged.ChannelState
import com.merxury.blocker.core.privileged.PrivilegedChannel
import com.merxury.blocker.core.result.Result
import com.merxury.blocker.core.ui.ProcessingProgress
import com.merxury.blocker.core.ui.control.ControlMessage
import com.merxury.blocker.core.ui.control.QueuedControlMessage
import com.merxury.blocker.core.ui.control.batchSummary
import com.merxury.blocker.core.ui.control.enqueue
import com.merxury.blocker.core.ui.data.UiMessage
import com.merxury.blocker.core.ui.data.toErrorMessage
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.retryWhen
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * The general rules with the number of apps of the selected user each matches, and blocking or
 * enabling every component they match. The counts follow the component cache, see
 * [GeneralRuleMatcher]; while the screen is shown the user's apps are read into the cache, see
 * [ComponentIndex], and the top bar shows how far that got.
 */
@HiltViewModel(assistedFactory = GeneralRulesViewModel.Factory::class)
class GeneralRulesViewModel @AssistedInject constructor(
    private val selectedUsers: SelectedUserRepository,
    private val generalRuleRepository: GeneralRuleRepository,
    private val appPropertiesRepository: AppPropertiesRepository,
    private val initGeneralRuleUseCase: InitializeRuleStorageUseCase,
    private val searchRule: SearchGeneralRuleUseCase,
    private val ruleMatcher: GeneralRuleMatcher,
    private val componentIndex: ComponentIndex,
    private val batches: ComponentBatchRunner,
    private val layersFor: GetControlLayersUseCase,
    private val channel: PrivilegedChannel,
    private val analyticsHelper: AnalyticsHelper,
    private val savedStateHandle: SavedStateHandle,
    @Dispatcher(IO) private val ioDispatcher: CoroutineDispatcher,
    @Dispatcher(DEFAULT) cpuDispatcher: CoroutineDispatcher,
    @Assisted val initialRuleId: String,
) : ViewModel() {
    private val rulesReady = MutableStateFlow(false)
    private val alert = MutableStateFlow<UiMessage?>(null)

    // The goal of a batch whose components are being gathered, before it runs; null otherwise.
    private val gathering = MutableStateFlow<ControlGoal?>(null)
    private val messages = MutableStateFlow<List<QueuedControlMessage>>(emptyList())
    private var nextMessageId = 0L
    private var rulesSynced = false

    // Key used to save and retrieve the currently selected rule id from saved state.
    private val selectedRuleIdKey = "selectedRuleIdKey"
    private val selectedRuleId: StateFlow<String?> = savedStateHandle.getStateFlow(
        key = selectedRuleIdKey,
        initialValue = initialRuleId,
    )

    @OptIn(ExperimentalCoroutinesApi::class)
    private val rules: Flow<GeneralRuleUiState> = combine(rulesReady, selectedUsers.selectedUser, ::Pair)
        .flatMapLatest { (ready, user) -> if (ready) rulesOf(user) else flowOf(GeneralRuleUiState.Loading) }

    @OptIn(ExperimentalCoroutinesApi::class)
    private val batch: Flow<ProcessingProgress?> = combine(
        selectedUsers.selectedUser.flatMapLatest { user ->
            batches.running(BatchKey.GeneralRules(user)).flatMapLatest { run ->
                // As before, the count of what is done: 0 until a request of the batch ends.
                run?.step?.map { step -> ProcessingProgress(current = step.done, total = step.total, isEnabling = run.goal != ControlGoal.BLOCK, isComplete = false) } ?: flowOf(null)
            }
        },
        gathering,
    ) { running, gathering ->
        // Until the batch runs, its components are read: shown as loading, as no total is known.
        running ?: gathering?.let { ProcessingProgress(current = 0, total = 0, isEnabling = it != ControlGoal.BLOCK, isComplete = false) }
    }

    val uiState: StateFlow<GeneralRulesScreenUiState> = combine(rules, batch, alert, messages, ::GeneralRulesScreenUiState)
        .flowOn(cpuDispatcher)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = GeneralRulesScreenUiState(),
        )

    /**
     * Keeps the rules and their matches current while the screen is shown: prepares the rule
     * storage and updates the rules from it once, and reads the selected user's apps into the
     * component cache the first time this run shows that user, and again when that ran with less
     * access than the privileged channel has now. Returns only when cancelled.
     */
    suspend fun keepFresh(): Unit = coroutineScope {
        launch { prepareRules() }
        combine(selectedUsers.selectedUser, channel.state) { user, _ -> user }
            .collect(componentIndex::ensureIndexed)
    }

    fun dismissAlert() {
        alert.value = null
    }

    fun onRuleClick(ruleId: String?) {
        savedStateHandle[selectedRuleIdKey] = ruleId
    }

    /** Changes what the rules match for the selected user and reports the batch outcome. */
    fun controlAllComponents(enable: Boolean) {
        val user = selectedUsers.selectedUser.value
        if (gathering.value != null || batches.current(BatchKey.GeneralRules(user)) != null) return
        val shown = (uiState.value.rules as? GeneralRuleUiState.Success)?.matchedComponents.orEmpty()
        val selected = shown.filterKeys { it.userId == user }
        if (selected.isEmpty()) {
            tell(ControlMessage.NoComponents)
            return
        }
        val targets = selected.filterKeys { !batches.isInFlight(it) }
        if (targets.isEmpty()) return
        val goal = if (enable) ControlGoal.ENABLE else ControlGoal.BLOCK
        gathering.value = goal
        viewModelScope.launch {
            try {
                val started = try {
                    analyticsHelper.logControlAllSdksClicked(newState = enable)
                    startBatch(user, goal, targets)
                } finally {
                    gathering.value = null
                }
                if (started != null) tell(batchSummary(started.run.outcomes.await(), started.vanished))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.e(e, "Cannot change the components the general rules match for user ${user.value}")
                alert.value = e.toErrorMessage()
            }
        }
    }

    /** Asks for root or Shizuku access, which may show a prompt. */
    fun grantAccess() = viewModelScope.launch {
        if (channel.connect() !is ChannelState.Ready) Timber.i("Access was not granted from the general rules")
    }

    fun onMessageShown(id: Long) {
        messages.update { queued -> queued.filterNot { it.id == id } }
    }

    /** Sends the matches shown at confirmation, leaving any changes already in flight alone. */
    private suspend fun startBatch(
        user: UserId,
        goal: ControlGoal,
        shown: Map<ComponentKey, ComponentDeclaration>,
    ): StartedBatch? {
        val layers = layersFor(goal)
        val current = componentIndex.readWhenIndexed(user) {
            ruleMatcher.matches(user).first().allComponents.map { it.declaration }
        } ?: shown.values
        val targets = shown.keys.filterNot(batches::isInFlight).toSet()
        if (targets.isEmpty()) return null
        val declarations = current.filter { ComponentKey(it.id, user) in targets }
        val sent = declarations.mapTo(mutableSetOf()) { ComponentKey(it.id, user) }
        val vanished = targets.filterNot { it in sent }
        if (declarations.isEmpty()) {
            tell(batchSummary(emptyList(), vanished))
            return null
        }
        val run = batches.reserve(BatchKey.GeneralRules(user), goal, declarations.map { it.id }) ?: return null
        try {
            batches.start(run, declarations, layers)
        } catch (e: Exception) {
            batches.release(run)
            throw e
        }
        return StartedBatch(run, vanished)
    }

    /**
     * The rules with their matches for [user]; loading until they are matched, so the counts of
     * the user selected before never show for this one. After a failure the error shows until
     * the rules can be matched again.
     */
    private fun rulesOf(user: UserId): Flow<GeneralRuleUiState> = combine(
        searchRule(user).map { results ->
            val components = buildMap {
                results.rules.forEach { rule ->
                    results.matches.componentsOf(rule.id).forEach { put(ComponentKey(it.declaration.id, user), it.declaration) }
                }
            }
            results.rules to components
        },
        componentIndex.state(user),
        selectedRuleId,
    ) { (rules, components), index, selected ->
        if (index is IndexState.Failed) {
            GeneralRuleUiState.Error(index.exception.toErrorMessage())
        } else {
            GeneralRuleUiState.Success(
                matchedRules = rules.filter { it.matchedAppCount > 0 },
                unmatchedRules = rules.filter { it.matchedAppCount == 0 },
                matchProgress = index.progress(),
                batchActionsEnabled = index is IndexState.Indexed,
                matchedComponents = components,
                selectedRuleId = selected,
            )
        }
    }
        .onStart { emit(GeneralRuleUiState.Loading) }
        .retryWhen { e, _ ->
            if (e is CancellationException) return@retryWhen false
            Timber.e(e, "Cannot load the general rules")
            emit(GeneralRuleUiState.Error(e.toErrorMessage()))
            delay(RETRY_MILLIS)
            true
        }

    /** Copies the rules into storage when missing, then updates the cached rules from it once. */
    private suspend fun prepareRules() {
        if (!rulesReady.value) {
            initGeneralRuleUseCase()
                .catch { Timber.e(it, "Cannot prepare the rule storage") }
                .takeWhile { it != InitializeState.Done }
                .collect { Timber.d("Initialize general rule: $it") }
            rulesReady.value = true
        }
        if (rulesSynced) return
        rulesSynced = true
        syncRules()
    }

    /** Updates the cached rules from storage when the rules there changed since the last update. */
    private suspend fun syncRules() {
        try {
            val ruleHash = generalRuleRepository.getRuleHash().first()
            if (ruleHash == appPropertiesRepository.appProperties.first().lastOpenedRuleHash) return
            generalRuleRepository.updateGeneralRule()
                .flowOn(ioDispatcher)
                .collect { result ->
                    when (result) {
                        is Result.Success -> appPropertiesRepository.updateLastOpenedRuleHash(ruleHash)
                        is Result.Error -> alert.value = result.exception.toErrorMessage()
                        Result.Loading -> Unit
                    }
                }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.e(e, "Cannot update the general rules")
            alert.value = e.toErrorMessage()
        }
    }

    private fun tell(message: ControlMessage) {
        val queued = QueuedControlMessage(nextMessageId++, message)
        messages.update { it.enqueue(queued) }
    }

    private data class StartedBatch(val run: BatchRun, val vanished: List<ComponentKey>)

    @AssistedFactory
    interface Factory {
        fun create(
            @Assisted initialRuleId: String,
        ): GeneralRulesViewModel
    }
}

/** How long the rules wait after a failure before they are read again. */
private const val RETRY_MILLIS = 5_000L

/** How far the reading of the user's apps into the cache got, from 0 to 1. */
private fun IndexState.progress(): Float = when (this) {
    IndexState.NotIndexed, is IndexState.Failed -> 0F
    is IndexState.Indexing -> if (total == 0) 0F else done.toFloat() / total
    is IndexState.Indexed -> 1F
}

/**
 * What the general rules screen shows.
 *
 * @property rules the rules with their matches
 * @property batch the progress of the batch of every matched component, or null
 * @property alert a failure to update the rules, shown until dismissed
 * @property messages outcomes of batches to tell, oldest first; see [GeneralRulesViewModel.onMessageShown]
 */
data class GeneralRulesScreenUiState(
    val rules: GeneralRuleUiState = GeneralRuleUiState.Loading,
    val batch: ProcessingProgress? = null,
    val alert: UiMessage? = null,
    val messages: List<QueuedControlMessage> = emptyList(),
)

sealed interface GeneralRuleUiState {
    data object Loading : GeneralRuleUiState

    /**
     * @property matchProgress how far indexing has progressed, from 0 to 1
     * @property batchActionsEnabled whether indexing has finished
     */
    data class Success(
        val matchedRules: List<GeneralRule>,
        val unmatchedRules: List<GeneralRule>,
        val matchProgress: Float = 0F,
        val selectedRuleId: String? = null,
        val batchActionsEnabled: Boolean = matchProgress == 1F,
        internal val matchedComponents: Map<ComponentKey, ComponentDeclaration> = emptyMap(),
    ) : GeneralRuleUiState

    data class Error(val error: UiMessage) : GeneralRuleUiState
}
