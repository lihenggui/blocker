// Copyright 2026 Blocker
// SPDX-License-Identifier: Apache-2.0

package com.merxury.blocker.feature.wake.impl

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.merxury.blocker.core.data.device.BackgroundUsageFailure
import com.merxury.blocker.core.data.device.BackgroundUsageRepository
import com.merxury.blocker.core.data.device.BackgroundUsageResult
import com.merxury.blocker.core.data.device.InstalledAppRepository
import com.merxury.blocker.core.data.device.SelectedUserRepository
import com.merxury.blocker.core.data.repository.componentdetail.ComponentDetailRepository
import com.merxury.blocker.core.domain.wake.ObserveAppWakeStateUseCase
import com.merxury.blocker.core.domain.wake.ObserveWakeAvailabilityUseCase
import com.merxury.blocker.core.domain.wake.RestoreAppWakeChangesUseCase
import com.merxury.blocker.core.domain.wake.SetWakePathsBlockedUseCase
import com.merxury.blocker.core.model.component.AppKey
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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
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
    private val setBlocked: SetWakePathsBlockedUseCase,
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
    private val detailed = observed
        .map { it.visibleComponentNames() }
        .distinctUntilChanged()
        .flatMapLatest(componentDetails::getLocalComponentDetails)
        .onStart { emit(emptyMap()) }

    private val data = combine(
        observed,
        observeAvailability().map { (it as? WakeAvailability.Ready)?.lastCheckedMillis }.distinctUntilChanged(),
        installedApps.apps(appKey.userId),
        detailed,
        ::buildWakeAppState,
    )

    val uiState = combine(
        data,
        backgroundUsage.observe(appKey).onStart { emit(null) },
        busy,
        notices,
    ) { state, usage, busy, notice ->
        state.copy(background = usage, busy = busy, notice = notice)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), WakeAppUiState(WakeAppIdentity(packageName)))

    fun toggle(key: String, allowed: Boolean) = act {
        val state = uiState.value
        val row = state.rows.firstOrNull { it.key == key && it.canToggle(state.background) } ?: return@act
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

    fun blockAll(selected: Set<String>) = act {
        val state = uiState.value
        val usage = state.background
        val rows = state.rows.filter { it.key in selected && it.canToggle(usage) && !it.blocked }
        val paths = rows.flatMap { it.paths }
        // A selected path for this app already owns the setting change and its undo.
        val backgroundHandledByPaths = paths.any { it.canRestrictBackground(appKey.packageName) }
        val turnOffBackground = BACKGROUND_CHOICE in selected && usage?.allowed == true && usage.changeable && !backgroundHandledByPaths
        if (rows.isEmpty() && !turnOffBackground) return@act

        val allowed = rows.associateWith { row ->
            row.paths.filter { it.block == WakePathBlock.NONE }.map { it.path.pathKey }.toSet() + listOfNotNull(row.automatic?.pathId)
        }
        val result = setBlocked.blockWithUndo(paths, rows.mapNotNull { it.automatic })
        val backgroundResult = if (turnOffBackground) changeBackground(false) else null

        val completed = rows.count { row -> allowed.getValue(row).all { it in result.outcome.done } }
        val backgroundChanged = backgroundResult?.done?.isNotEmpty() == true
        val undo = WakeUndo.Block(result.undo, backgroundChanged).takeIf { result.undo.hasChanges || backgroundChanged }
        val failure = result.outcome.failure ?: backgroundResult?.failure
        val failed = rows.size - completed + if (backgroundResult?.failure != null) 1 else 0
        val text = if (failed == 0 && failure == null) {
            blockedCount(completed.coerceAtLeast(if (backgroundChanged) 1 else 0))
        } else {
            blockFailureText(failure, completed + if (backgroundChanged) 1 else 0, failed)
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
                    outcome.failure ?: backgroundFailure
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

    private fun show(text: WakeText, undo: WakeUndo? = null) {
        // A write may finish after navigation, even after the same destination is opened again.
        if (actionScreen == screenGeneration) notices.value = WakeNotice(++nextNoticeId, text, undo)
    }
}
