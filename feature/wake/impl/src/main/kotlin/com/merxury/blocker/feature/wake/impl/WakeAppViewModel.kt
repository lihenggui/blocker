// Copyright 2026 Blocker
// SPDX-License-Identifier: Apache-2.0

package com.merxury.blocker.feature.wake.impl

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.merxury.blocker.core.data.device.AppRestrictionRepository
import com.merxury.blocker.core.data.device.AppRestrictionRestoreResult
import com.merxury.blocker.core.data.device.AppRestrictionResult
import com.merxury.blocker.core.data.device.BackgroundUsageFailure
import com.merxury.blocker.core.data.device.BackgroundUsageRepository
import com.merxury.blocker.core.data.device.BackgroundUsageResult
import com.merxury.blocker.core.data.device.InstalledAppRepository
import com.merxury.blocker.core.data.device.SelectedUserRepository
import com.merxury.blocker.core.data.repository.componentdetail.ComponentDetailRepository
import com.merxury.blocker.core.domain.wake.ForceStopAppUseCase
import com.merxury.blocker.core.domain.wake.ObserveAppWakeStateUseCase
import com.merxury.blocker.core.domain.wake.ObservePotentialWakeEntriesUseCase
import com.merxury.blocker.core.domain.wake.ObserveWakeAvailabilityUseCase
import com.merxury.blocker.core.domain.wake.RestoreAppWakeChangesUseCase
import com.merxury.blocker.core.domain.wake.SetWakePathsBlockedUseCase
import com.merxury.blocker.core.model.component.AppKey
import com.merxury.blocker.core.model.privileged.CallResult
import com.merxury.blocker.core.model.wake.PotentialWakeEntry
import com.merxury.blocker.core.model.wake.WakeAvailability
import com.merxury.blocker.core.model.wake.WakeBlockFailure
import com.merxury.blocker.core.model.wake.WakeBlockOutcome
import com.merxury.blocker.core.model.wake.WakePathBlock
import com.merxury.blocker.feature.wake.api.R.string
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel(assistedFactory = WakeAppViewModel.Factory::class)
internal class WakeAppViewModel @AssistedInject constructor(
    @Assisted packageName: String,
    observeState: ObserveAppWakeStateUseCase,
    observeAvailability: ObserveWakeAvailabilityUseCase,
    installedApps: InstalledAppRepository,
    selectedUsers: SelectedUserRepository,
    componentDetails: ComponentDetailRepository,
    private val backgroundUsage: BackgroundUsageRepository,
    private val appRestriction: AppRestrictionRepository,
    observePotentialEntries: ObservePotentialWakeEntriesUseCase,
    private val setBlocked: SetWakePathsBlockedUseCase,
    private val forceStopApp: ForceStopAppUseCase,
    private val restore: RestoreAppWakeChangesUseCase,
) : ViewModel() {
    @AssistedFactory
    interface Factory {
        fun create(packageName: String): WakeAppViewModel
    }

    private val appKey = AppKey(packageName, selectedUsers.ownUser)
    private val busy = MutableStateFlow(false)
    private val notices = MutableStateFlow<WakeNotice?>(null)
    private var nextNoticeId = 0L
    private var screenGeneration = 0L
    private var actionScreen = 0L

    private val observed = observeState(packageName)
        .shareIn(viewModelScope, SharingStarted.WhileSubscribed(), replay = 1)

    @OptIn(ExperimentalCoroutinesApi::class)
    private val potential = observed
        .map { it.observedComponentNames() }
        .distinctUntilChanged()
        .flatMapLatest { excluded -> observePotentialEntries(packageName, excluded) }
        .map<List<PotentialWakeEntry>, List<PotentialWakeEntry>?> { it }
        .onStart { emit(null) }
        .shareIn(viewModelScope, SharingStarted.WhileSubscribed(), replay = 1)

    @OptIn(ExperimentalCoroutinesApi::class)
    private val detailed = combine(observed, potential) { state, entries ->
        state.visibleComponentNames() + entries.orEmpty().mapNotNull { it.state.path.component }
    }
        .distinctUntilChanged()
        .flatMapLatest(componentDetails::getLocalComponentDetails)
        .onStart { emit(emptyMap()) }

    private val data = combine(
        observed,
        observeAvailability().map { (it as? WakeAvailability.Ready)?.lastCheckedMillis }.distinctUntilChanged(),
        installedApps.apps(appKey.userId),
        detailed,
        potential,
    ) { state, lastChecked, apps, details, entries ->
        buildWakeAppState(state, lastChecked, apps, details, entries.orEmpty(), potentialLoaded = entries != null)
    }

    private val installRevisionChanges = installedApps.app(appKey)
        .map { it?.let { app -> Triple(app.apkPath, app.versionCode, app.lastUpdateTime) } }
        .distinctUntilChanged()
        .drop(1)

    private val state = combine(
        data,
        backgroundUsage.observe(appKey).onStart { emit(null) },
        appRestriction.observe(appKey).onStart { emit(null) },
        busy,
        notices,
    ) { state, usage, restriction, busy, notice ->
        state.copy(background = usage, deepRestriction = restriction, busy = busy, notice = notice)
    }

    val uiState = flow {
        coroutineScope {
            launch { installRevisionChanges.collect { refreshRestrictions() } }
            emitAll(state)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), WakeAppUiState(WakeAppIdentity(packageName)))

    fun refreshRestrictions() {
        viewModelScope.launch {
            // The repositories publish the readback to their observers, including unavailable settings.
            backgroundUsage.read(appKey)
            appRestriction.read(appKey)
        }
    }

    fun toggle(key: String, allowed: Boolean) = act {
        val state = uiState.value
        val row = state.controllableRows.firstOrNull { it.key == key && it.canToggle(state.background) } ?: return@act
        val paths = row.paths.filter { (it.block != WakePathBlock.NONE) == allowed }
        val outcome = row.automatic?.let { setBlocked(listOf(it), blocked = !allowed) } ?: setBlocked(paths, blocked = !allowed)
        if (outcome.failure != null) {
            val component = paths.singleOrNull()?.path?.component.takeIf { !allowed }
            show(blockFailureText(outcome.failure, outcome.done.size, (paths.size - outcome.done.size).coerceAtLeast(1), component))
        }
    }

    fun setBackgroundAllowed(allowed: Boolean) = act {
        val usage = uiState.value.background ?: return@act
        if (!usage.changeable || usage.allowed == allowed) return@act
        val result = changeBackground(allowed)
        if (result.failure != null) show(blockFailureText(result.failure, 0, 1))
    }

    fun setDeepRestricted(restricted: Boolean) = act {
        val current = uiState.value.deepRestriction ?: return@act
        if (!current.changeable || (if (restricted) current.active else !current.anyActive)) return@act
        changeDeepRestriction(restricted).failure?.let { show(blockFailureText(it, 0, 1)) }
    }

    fun blockAll(selected: Set<String>) = act {
        val state = uiState.value
        val usage = state.background
        val rows = state.blockableRows.filter { it.key in selected }
        val paths = rows.flatMap { it.paths }
        // A selected path for this app already owns the setting change and its undo.
        val backgroundHandledByPaths = paths.any { it.canRestrictBackground(appKey.packageName) }
        val turnOffBackground = BACKGROUND_CHOICE in selected && usage?.allowed == true && usage.changeable && !backgroundHandledByPaths
        val enableDeepRestriction = DEEP_RESTRICTION_CHOICE in selected && state.canEnableDeepRestriction
        if (rows.isEmpty() && !turnOffBackground && !enableDeepRestriction) return@act

        val allowed = rows.associateWith { row ->
            row.paths.filter { it.block == WakePathBlock.NONE }.map { it.path.pathKey }.toSet() + listOfNotNull(row.automatic?.pathId)
        }
        val result = setBlocked.blockWithUndo(paths, rows.mapNotNull { it.automatic })
        val backgroundResult = if (turnOffBackground) changeBackground(false) else null
        val deepResult = if (enableDeepRestriction) changeDeepRestriction(true) else null

        val completed = rows.count { row -> allowed.getValue(row).all { it in result.outcome.done } }
        val backgroundChanged = backgroundResult?.done?.isNotEmpty() == true
        val deepRestrictionChanged = deepResult?.done?.isNotEmpty() == true
        val undo = WakeUndo.Block(result.undo, backgroundChanged, deepRestrictionChanged)
            .takeIf { result.undo.hasChanges || backgroundChanged || deepRestrictionChanged }
        val settingsFailures = listOfNotNull(backgroundResult?.failure, deepResult?.failure)
        val failure = result.outcome.failure ?: settingsFailures.firstOrNull()
        val failed = rows.size - completed + settingsFailures.size
        val totalCompleted = completed + (if (backgroundChanged) 1 else 0) + (if (deepRestrictionChanged) 1 else 0)
        val anyChanged = result.outcome.done.isNotEmpty() || result.undo.hasChanges || backgroundChanged || deepRestrictionChanged
        val forceStopFailed = FORCE_STOP_CHOICE in selected && anyChanged && forceStopApp(appKey.packageName) is CallResult.Failure
        val blockNotice = if (failed == 0 && failure == null) {
            blockedCount(totalCompleted)
        } else {
            blockFailureText(failure, totalCompleted, failed)
        }
        val text = if (forceStopFailed) {
            val stopNotice = WakeText.Resource(string.feature_wake_api_force_stop_failed, state.app.label)
            WakeText.Resource(string.feature_wake_api_list_pair, blockNotice, stopNotice)
        } else {
            blockNotice
        }
        show(text, undo)
    }

    fun restoreAll() = act {
        val result = restore(appKey.packageName)
        val undo = result.undo?.let(WakeUndo::Restore)
        val text = result.outcome.failure?.let { blockFailureText(it, 0, 1) }
            ?: WakeText.Resource(string.feature_wake_api_restored, uiState.value.app.label)
        show(text, undo)
    }

    fun undo(id: Long) {
        val token = notices.value?.takeIf { it.id == id }?.undo ?: return
        act {
            val failure = when (token) {
                is WakeUndo.Block -> {
                    val outcome = setBlocked.undo(token.token)
                    val backgroundFailure = if (token.background) changeBackground(true).failure else null
                    val deepFailure = if (token.deepRestrictionChanged) {
                        (appRestriction.restoreOwned(appKey) as? AppRestrictionRestoreResult.Failure)?.reason?.wakeFailure()
                    } else {
                        null
                    }
                    outcome.failure ?: backgroundFailure ?: deepFailure
                }

                is WakeUndo.Restore -> restore.undo(token.token).failure
            }
            if (failure != null) show(WakeText.Resource(string.feature_wake_api_undo_failed))
        }
    }

    fun dismissNotice(id: Long) {
        if (notices.value?.id == id) notices.value = null
    }

    fun leaveScreen() {
        screenGeneration++
        notices.value = null
    }

    private fun act(action: suspend () -> Unit) {
        if (busy.value) return
        notices.value = null
        busy.value = true
        actionScreen = screenGeneration
        viewModelScope.launch {
            try {
                action()
            } finally {
                busy.value = false
            }
        }
    }

    private suspend fun changeBackground(allowed: Boolean): WakeBlockOutcome {
        val failure = when (val result = backgroundUsage.setAllowed(appKey, allowed)) {
            is BackgroundUsageResult.Success -> WakeBlockFailure.BackgroundNotChangeable.takeIf { result.value.allowed != allowed }

            is BackgroundUsageResult.Failure -> {
                // The repository keeps its last known state when Android refuses the change.
                if (result.reason == BackgroundUsageFailure.NOT_CHANGEABLE) backgroundUsage.read(appKey)
                result.reason.wakeFailure()
            }
        }
        return WakeBlockOutcome(if (failure == null) setOf(BACKGROUND_CHOICE) else emptySet(), failure)
    }

    private suspend fun changeDeepRestriction(restricted: Boolean): WakeBlockOutcome {
        val failure = when (val result = appRestriction.setDeepRestricted(appKey, restricted)) {
            is AppRestrictionResult.Failure -> result.reason.wakeFailure()

            is AppRestrictionResult.Success -> {
                val value = result.value
                val matches = if (restricted) value.active else !value.anyActive
                WakeBlockFailure.SystemError(null).takeUnless { matches }
            }
        }
        return WakeBlockOutcome(if (failure == null) setOf(DEEP_RESTRICTION_CHOICE) else emptySet(), failure)
    }

    private fun show(text: WakeText, undo: WakeUndo? = null) {
        // A write may finish after navigation, even after the same destination is opened again.
        if (actionScreen == screenGeneration) notices.value = WakeNotice(++nextNoticeId, text, undo)
    }
}
