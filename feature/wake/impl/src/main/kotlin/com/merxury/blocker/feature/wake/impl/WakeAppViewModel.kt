// Copyright 2026 Blocker
// SPDX-License-Identifier: Apache-2.0

package com.merxury.blocker.feature.wake.impl

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
import com.merxury.blocker.core.model.system.BackgroundUsage
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
    private val setBlocked: SetWakePathsBlockedUseCase,
    private val restore: RestoreAppWakeChangesUseCase,
) : ViewModel() {
    @AssistedFactory
    interface Factory {
        fun create(packageName: String): WakeAppViewModel
    }

    private val appKey = AppKey(packageName, selectedUsers.ownUser)
    private val background = MutableStateFlow<BackgroundUsage?>(null)
    private var backgroundRead = false
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
    ).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), WakeAppUiState(WakeAppIdentity(packageName)))

    val uiState = combine(
        data,
        flow {
            emit(background.value)
            if (!backgroundRead) {
                refreshBackground()
                backgroundRead = true
            }
            emitAll(background)
        },
        busy,
        notices,
    ) { state, usage, busy, notice ->
        state.copy(background = usage, busy = busy, notice = notice)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), data.value)

    fun toggle(key: String, allowed: Boolean) = act {
        val row = data.value.rows.firstOrNull { it.key == key && it.canToggle } ?: return@act
        val paths = row.paths.filter { (it.block != WakePathBlock.NONE) == allowed }
        val outcome = row.automatic?.let { setBlocked(listOf(it), blocked = !allowed) } ?: setBlocked(paths, blocked = !allowed)
        refreshBackground()
        if (outcome.failure != null) {
            val component = paths.singleOrNull()?.path?.component.takeIf { !allowed }
            show(blockFailureText(outcome.failure, outcome.done.size, (paths.size - outcome.done.size).coerceAtLeast(1), component))
        }
    }

    fun setBackgroundAllowed(allowed: Boolean) = act {
        val usage = background.value ?: return@act
        if (!usage.changeable || usage.allowed == allowed) return@act
        val result = changeBackground(allowed)
        if (result.failure != null) show(blockFailureText(result.failure, 0, 1))
    }

    fun blockAll(selected: Set<String>) = act {
        val state = data.value
        val rows = state.rows.filter { it.key in selected && it.canToggle && !it.blocked }
        val usage = background.value
        val turnOffBackground = BACKGROUND_CHOICE in selected && usage?.allowed == true && usage.changeable
        if (rows.isEmpty() && !turnOffBackground) return@act

        val allowed = rows.associateWith { row ->
            row.paths.filter { it.block == WakePathBlock.NONE }.map { it.path.pathKey }.toSet() + listOfNotNull(row.automatic?.pathId)
        }
        val result = setBlocked.blockWithUndo(rows.flatMap { it.paths }, rows.mapNotNull { it.automatic })
        val backgroundResult = if (turnOffBackground) {
            changeBackground(false)
        } else {
            refreshBackground()
            null
        }

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
        refreshBackground()
        val undo = result.undo?.let(WakeUndo::Restore)
        val text = result.outcome.failure?.let { blockFailureText(it, 0, 1) }
            ?: WakeText.Resource(string.feature_wake_api_restored, data.value.app.label)
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
            if (token !is WakeUndo.Block || !token.background) refreshBackground()
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

    private suspend fun refreshBackground() {
        background.value = (backgroundUsage.read(appKey) as? BackgroundUsageResult.Success)?.value
    }

    private suspend fun changeBackground(allowed: Boolean): WakeBlockOutcome {
        val result = backgroundUsage.setAllowed(appKey, allowed)
        val readBack = backgroundUsage.read(appKey)
        val confirmed = (readBack as? BackgroundUsageResult.Success)?.value
        val returned = (result as? BackgroundUsageResult.Success)?.value
        background.value = confirmed
        val changed = if ((confirmed ?: returned)?.allowed == allowed) setOf(BACKGROUND_CHOICE) else emptySet()
        val failure = when {
            result is BackgroundUsageResult.Failure -> result.reason.wakeFailure()
            changed.isNotEmpty() -> null
            readBack is BackgroundUsageResult.Failure -> readBack.reason.wakeFailure()
            else -> WakeBlockFailure.BackgroundNotChangeable
        }
        return WakeBlockOutcome(changed, failure)
    }

    private fun show(text: WakeText, undo: WakeUndo? = null) {
        // A write may finish after navigation, even after the same destination is opened again.
        if (actionScreen == screenGeneration) notices.value = WakeNotice(++nextNoticeId, text, undo)
    }
}
