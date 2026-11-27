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

package com.merxury.blocker.feature.ruledetail.impl

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.merxury.blocker.core.analytics.AnalyticsHelper
import com.merxury.blocker.core.data.device.ComponentActions
import com.merxury.blocker.core.data.device.InstalledAppRepository
import com.merxury.blocker.core.data.device.SelectedUserRepository
import com.merxury.blocker.core.data.repository.generalrule.GeneralRuleRepository
import com.merxury.blocker.core.data.repository.userdata.UserDataRepository
import com.merxury.blocker.core.dispatchers.BlockerDispatchers.DEFAULT
import com.merxury.blocker.core.dispatchers.Dispatcher
import com.merxury.blocker.core.domain.control.BatchKey
import com.merxury.blocker.core.domain.control.ComponentBatchRunner
import com.merxury.blocker.core.domain.control.ControlComponentUseCase
import com.merxury.blocker.core.domain.control.GetControlLayersUseCase
import com.merxury.blocker.core.domain.rule.GeneralRuleMatcher
import com.merxury.blocker.core.domain.search.ComponentIndex
import com.merxury.blocker.core.domain.search.IndexState
import com.merxury.blocker.core.model.component.ComponentDeclaration
import com.merxury.blocker.core.model.component.ComponentId
import com.merxury.blocker.core.model.component.ComponentKey
import com.merxury.blocker.core.model.component.UserId
import com.merxury.blocker.core.model.control.ControlGoal
import com.merxury.blocker.core.model.privileged.CallResult
import com.merxury.blocker.core.model.privileged.ChannelState
import com.merxury.blocker.core.privileged.PrivilegedChannel
import com.merxury.blocker.core.ui.ProcessingProgress
import com.merxury.blocker.core.ui.TabState
import com.merxury.blocker.core.ui.control.ActivityLaunchFailed
import com.merxury.blocker.core.ui.control.ControlMessage
import com.merxury.blocker.core.ui.control.QueuedControlMessage
import com.merxury.blocker.core.ui.control.batchSummary
import com.merxury.blocker.core.ui.control.enqueue
import com.merxury.blocker.core.ui.control.toMessage
import com.merxury.blocker.core.ui.data.toErrorMessage
import com.merxury.blocker.feature.ruledetail.RuleDetailSortType
import com.merxury.blocker.feature.ruledetail.api.navigation.RuleDetailTabs
import com.merxury.blocker.feature.ruledetail.api.navigation.RuleDetailTabs.Applicable
import com.merxury.blocker.feature.ruledetail.api.navigation.RuleDetailTabs.Description
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.retryWhen
import kotlinx.coroutines.flow.runningFold
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import java.io.File

/**
 * The detail of one general rule and the apps of the selected user with components it matches,
 * from the component cache, see [GeneralRuleMatcher]. Every row shows what the cache holds: after
 * a change the repository's flow updates the rows, never a copy kept here.
 *
 * Turning one switch on enables the component through every layer; turning it off blocks it
 * through the layers of the controller type. The block and enable actions of an app or of the
 * whole page run as one batch, see [ComponentBatchRunner]. Batch enable uses the same goal as
 * turning a switch on, including for components disabled in the manifest.
 */
@HiltViewModel(assistedFactory = RuleDetailViewModel.Factory::class)
class RuleDetailViewModel @AssistedInject constructor(
    private val selectedUsers: SelectedUserRepository,
    ruleRepository: GeneralRuleRepository,
    private val ruleMatcher: GeneralRuleMatcher,
    private val installedApps: InstalledAppRepository,
    private val componentIndex: ComponentIndex,
    private val batches: ComponentBatchRunner,
    private val controlComponent: ControlComponentUseCase,
    private val layersFor: GetControlLayersUseCase,
    private val componentActions: ComponentActions,
    private val channel: PrivilegedChannel,
    userDataRepository: UserDataRepository,
    private val analyticsHelper: AnalyticsHelper,
    @Dispatcher(DEFAULT) cpuDispatcher: CoroutineDispatcher,
    @Assisted("ruleId") val ruleId: String,
    @Assisted("tab") val tab: String = Applicable.routeName,
) : ViewModel() {
    private val id = ruleId.toInt()
    private val tabState = MutableStateFlow(TabState(items = listOf(Applicable, Description), selectedItem = RuleDetailTabs.fromName(tab)))
    private val sortType = MutableStateFlow(RuleDetailSortType.NAME)
    private val singles = MutableStateFlow<Set<ComponentKey>>(emptySet())
    private val messages = MutableStateFlow<List<QueuedControlMessage>>(emptyList())
    private val actionFailure = MutableStateFlow<ActivityLaunchFailed?>(null)
    private var nextMessageId = 0L

    @OptIn(ExperimentalCoroutinesApi::class)
    private val matched: Flow<MatchedAppsUiState> = selectedUsers.selectedUser.flatMapLatest { user ->
        // Only this rule is matched, which is quicker than matching every rule the first time.
        combine(ruleMatcher.matches(user, id), installedApps.apps(user), ::matchedApps)
            .combine(sortType, ::Pair)
            .runningFold(SortedApps.NONE) { sorted, (apps, sort) -> sorted.next(apps, sort) }
            .drop(1)
            .map<SortedApps, MatchedAppsUiState> { MatchedAppsUiState.Loaded(it.apps) }
            // Another user's apps are never shown, nor acted on, while this user's are matched.
            .onStart { emit(MatchedAppsUiState.Loading) }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private val ruleInfo: Flow<RuleInfoUiState> = combine(
        ruleRepository.getGeneralRule(id),
        matched,
        userDataRepository.userData.map { it.useDynamicColor }.distinctUntilChanged(),
        selectedUsers.selectedUser.flatMapLatest { componentIndex.state(it) },
    ) { rule, matched, useDynamicColor, index ->
        if (index is IndexState.Failed) {
            RuleInfoUiState.Error(index.exception.toErrorMessage())
        } else {
            RuleInfoUiState.Success(
                rule,
                matched,
                seedIcon = rule.iconUrl?.takeIf { useDynamicColor }?.let(::File),
            )
        }
    }.retryWhen { e, _ ->
        if (e is CancellationException) return@retryWhen false
        // Shown until the rule and its matches can be read again.
        Timber.e(e, "Cannot load general rule $ruleId")
        emit(RuleInfoUiState.Error(e.toErrorMessage()))
        delay(RETRY_MILLIS)
        true
    }

    // The selected user's components a change of any screen has not ended for.
    private val inFlight: Flow<Set<ComponentId>> = combine(selectedUsers.selectedUser, batches.inFlight, singles) { user, keys, singles ->
        (keys + singles).filter { it.userId == user }.mapTo(mutableSetOf()) { it.id }
    }.distinctUntilChanged()

    @OptIn(ExperimentalCoroutinesApi::class)
    private val batch: Flow<ProcessingProgress?> = selectedUsers.selectedUser.flatMapLatest { user ->
        batches.running(BatchKey.Rule(id, user)).flatMapLatest { run ->
            // As before, the count of what is done: 0 until a request of the batch ends.
            run?.step?.map { step -> ProcessingProgress(current = step.done, total = step.total, isEnabling = run.goal != ControlGoal.BLOCK, isComplete = false) } ?: flowOf(null)
        }
    }

    private val controls = combine(inFlight, batch, messages, actionFailure, ::Controls)

    val uiState: StateFlow<RuleDetailUiState> = combine(ruleInfo, tabState, sortType, controls) { ruleInfo, tabs, sort, controls ->
        RuleDetailUiState(
            ownUser = selectedUsers.ownUser,
            ruleInfo = ruleInfo,
            tabState = tabs,
            sortType = sort,
            inFlight = controls.inFlight,
            batch = controls.batch,
            messages = controls.messages,
            actionFailure = controls.actionFailure,
        )
    }
        .flowOn(cpuDispatcher)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = RuleDetailUiState(ownUser = selectedUsers.ownUser, tabState = tabState.value),
        )

    /**
     * Reads the selected user's apps into the component cache the first time this run shows that
     * user, and again when that ran with less access than the privileged channel has now, so the
     * rule matches apps never opened. Returns only when cancelled.
     */
    suspend fun keepFresh() {
        combine(selectedUsers.selectedUser, channel.state) { user, _ -> user }
            .collect(componentIndex::ensureIndexed)
    }

    fun switchTab(newTab: RuleDetailTabs) {
        tabState.update { it.copy(selectedItem = newTab) }
    }

    fun updateSortType(sortType: RuleDetailSortType) {
        this.sortType.value = sortType
    }

    /**
     * Turns the switch of [component], for the user of the row it is on: on enables it through
     * every layer, off blocks it.
     */
    fun setEnabled(component: ComponentKey, enabled: Boolean) {
        if (isInFlight(component)) return
        val goal = if (enabled) ControlGoal.ENABLE else ControlGoal.BLOCK
        singles.update { it + component }
        viewModelScope.launch {
            try {
                analyticsHelper.logSwitchComponentStateClicked(newState = enabled)
                controlComponent(component, goal).toMessage(component.id, goal)?.let { tell(it, component.id) }
            } finally {
                singles.update { it - component }
            }
        }
    }

    /** Blocks or enables every component the rule matches in [app], in one batch. */
    fun controlAllComponents(app: MatchedApp, enable: Boolean) {
        analyticsHelper.logControlAllComponentsClicked(newState = enable)
        startBatch(app.app.userId, app.rows.map { it.id }, enable)
    }

    /** Blocks or enables every component the rule matches in the apps shown, in one batch. */
    fun controlAllComponentsInPage(enable: Boolean) {
        val apps = ((uiState.value.ruleInfo as? RuleInfoUiState.Success)?.matchedAppsUiState as? MatchedAppsUiState.Loaded)?.apps
        if (apps.isNullOrEmpty()) {
            tell(ControlMessage.NoComponents)
            return
        }
        analyticsHelper.logControlAllInPageClicked(newState = enable)
        val user = apps.first().app.userId
        val shown = apps.flatMap { it.declarations }
        startBatch(user, shown.map { it.id }, enable) {
            componentIndex.readWhenIndexed(user) { currentMatches(user) } ?: shown
        }
    }

    /** Starts [activity], for the user of its row; a blocked one stays blocked, as its switch shows. */
    fun launchActivity(activity: ComponentKey) = viewModelScope.launch {
        analyticsHelper.logLaunchActivityClicked()
        val result = componentActions.startActivity(activity)
        if (result is CallResult.Failure) {
            Timber.w("Cannot launch $activity: ${result.reason}")
            actionFailure.value = ActivityLaunchFailed.of(activity.id.simpleName, result.reason)
        }
    }

    fun onActionFailureShown() {
        actionFailure.value = null
    }

    /** Asks for root or Shizuku access, which may show a prompt. */
    fun grantAccess() = viewModelScope.launch {
        if (channel.connect() !is ChannelState.Ready) Timber.i("Access was not granted from rule $ruleId")
    }

    fun onMessageShown(id: Long) {
        messages.update { queued -> queued.filterNot { it.id == id } }
    }

    /**
     * Sends [components] of [user] as one batch through [batches], so it ends even when the screen
     * goes away: blocking through the layers of the controller type, or enabling through every
     * layer. The batch counts as running from the moment it is chosen. Components already in
     * flight are left out, and an existing batch of this rule for the user keeps running.
     */
    private fun startBatch(
        user: UserId,
        components: List<ComponentId>,
        enable: Boolean,
        matches: suspend () -> List<ComponentDeclaration> = { currentMatches(user) },
    ) {
        val batchKey = BatchKey.Rule(id, user)
        if (batches.current(batchKey) != null) return
        val goal = if (enable) ControlGoal.ENABLE else ControlGoal.BLOCK
        if (components.isEmpty()) {
            tell(ControlMessage.NoComponents)
            return
        }
        val ids = components.distinct().filterNot { isInFlight(ComponentKey(it, user)) }
        if (ids.isEmpty()) return
        val run = batches.reserve(batchKey, goal, ids) ?: return
        viewModelScope.launch {
            var started = false
            try {
                val layers = layersFor(goal)
                val cached = matches().associateBy { it.id }
                val declarations = ids.mapNotNull { cached[it] }
                val vanished = (ids - declarations.mapTo(mutableSetOf()) { it.id }).map { ComponentKey(it, user) }
                if (vanished.isNotEmpty()) Timber.w("${vanished.size} components of rule $ruleId are no longer matched, tell them as not changed")
                batches.start(run, declarations, layers)
                started = true
                tell(batchSummary(run.outcomes.await(), vanished))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.e(e, "Cannot read the components of rule $ruleId to change for user ${user.value}")
                tell(batchSummary(emptyList(), ids.map { ComponentKey(it, user) }))
            } finally {
                if (!started) batches.release(run)
            }
        }
    }

    private suspend fun currentMatches(user: UserId): List<ComponentDeclaration> = ruleMatcher.matches(user, id).first().map { it.declaration }

    private fun isInFlight(component: ComponentKey): Boolean = component in singles.value || batches.isInFlight(component)

    private fun tell(message: ControlMessage, component: ComponentId? = null) {
        messages.update { it.enqueue(QueuedControlMessage(nextMessageId++, message, component)) }
    }

    private data class Controls(
        val inFlight: Set<ComponentId>,
        val batch: ProcessingProgress?,
        val messages: List<QueuedControlMessage>,
        val actionFailure: ActivityLaunchFailed?,
    )

    private companion object {
        /** How long the screen waits after a failure to read the rule before it reads it again. */
        const val RETRY_MILLIS = 5_000L
    }

    @AssistedFactory
    interface Factory {
        fun create(
            @Assisted("ruleId") ruleId: String,
            @Assisted("tab") tab: String = Applicable.routeName,
        ): RuleDetailViewModel
    }
}
