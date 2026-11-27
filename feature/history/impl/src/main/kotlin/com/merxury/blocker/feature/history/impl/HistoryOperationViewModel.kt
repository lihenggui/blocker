// Copyright 2026 Blocker
// SPDX-License-Identifier: Apache-2.0

package com.merxury.blocker.feature.history.impl

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.merxury.blocker.core.data.device.ComponentStatusRepository
import com.merxury.blocker.core.data.device.DeviceUserRepository
import com.merxury.blocker.core.data.device.InstalledAppRepository
import com.merxury.blocker.core.data.device.SelectedUserRepository
import com.merxury.blocker.core.data.history.OperationHistoryRepository
import com.merxury.blocker.core.data.repository.generalrule.GeneralRuleRepository
import com.merxury.blocker.core.data.util.TimeZoneMonitor
import com.merxury.blocker.core.model.component.AppKey
import com.merxury.blocker.core.model.component.Availability
import com.merxury.blocker.core.model.component.ComponentDeclaration
import com.merxury.blocker.core.model.component.ComponentId
import com.merxury.blocker.core.model.component.ComponentStatus
import com.merxury.blocker.core.model.component.ControlState
import com.merxury.blocker.core.model.component.PmSetting
import com.merxury.blocker.core.model.history.OperationEntry
import com.merxury.blocker.core.model.history.UndoResult
import com.merxury.blocker.core.ui.profile.ProfileLabel
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import java.time.ZoneId

@HiltViewModel(assistedFactory = HistoryOperationViewModel.Factory::class)
internal class HistoryOperationViewModel @AssistedInject constructor(
    @Assisted private val id: Long,
    private val history: OperationHistoryRepository,
    private val components: ComponentStatusRepository,
    installedApps: InstalledAppRepository,
    private val deviceUsers: DeviceUserRepository,
    selectedUser: SelectedUserRepository,
    generalRules: GeneralRuleRepository,
    timeZoneMonitor: TimeZoneMonitor,
) : ViewModel() {
    @AssistedFactory
    interface Factory {
        fun create(id: Long): HistoryOperationViewModel
    }

    private val ownUser = selectedUser.ownUser
    private val actions = MutableStateFlow(ActionState())
    private val device = observeDeviceLabels(deviceUsers, installedApps, ownUser)
    private val operations = history.observeHistory().withRuleLabels(generalRules)
    private val entries = flow<Result<List<LoadedEntry>>?> {
        emit(Result.success(loadEntries()))
    }.onStart {
        emit(null)
    }.catch {
        Timber.e(it, "Could not load operation entries for %d", id)
        emit(Result.failure(it))
    }

    private val content = combine(operations, device, timeZoneMonitor.currentTimeZone, entries) { (result, rules), labels, timeZone, loaded ->
        val allOperations = result.getOrNull().orEmpty()
        val operation = allOperations.find { it.id == id }
        val row = operation?.historyRow(labels, rules, allOperations.filter { it.undoneByOperationId == id })
        val undoneAt = operation?.undoneByOperationId?.let { undoId -> allOperations.find { it.id == undoId }?.timestamp }
        val laterCount = if (operation?.canUndo == true && allOperations.any { it.canUndo && it.timestamp > operation.timestamp }) {
            // undoSince uses an inclusive timestamp, so operations recorded at the same instant are also affected.
            allOperations.count { it.canUndo && it.timestamp >= operation.timestamp } - 1
        } else {
            0
        }
        HistoryOperationUiState(
            row = row,
            zone = ZoneId.of(timeZone.id),
            groups = loaded?.getOrNull()?.groupByApp(labels),
            loadFailed = operation == null,
            entriesFailed = loaded?.isFailure == true,
            undoneAt = undoneAt,
            laterCount = laterCount,
        )
    }

    val uiState: StateFlow<HistoryOperationUiState> = combine(content, actions) { state, action ->
        state.copy(
            confirmLater = state.canUndoLater && action.confirmLater,
            isUndoing = action.isUndoing,
            message = action.message,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HistoryOperationUiState())

    fun undo() {
        if (uiState.value.canUndo) runUndo { history.undoOperation(id) }
    }

    fun requestUndoLater() {
        if (uiState.value.canUndoLater && !actions.value.isUndoing) actions.update { it.copy(confirmLater = true) }
    }

    fun cancelUndoLater() {
        actions.update { it.copy(confirmLater = false) }
    }

    fun confirmUndoLater() {
        val state = uiState.value
        val operation = state.row?.operation ?: return
        if (state.canUndoLater && actions.value.confirmLater) runUndo { history.undoSince(operation.timestamp) }
    }

    fun messageShown(message: HistoryMessage) {
        actions.update { if (it.message === message) it.copy(message = null) else it }
    }

    private fun runUndo(undo: suspend () -> UndoResult) {
        if (actions.value.isUndoing) return
        actions.update { it.copy(confirmLater = false, isUndoing = true) }
        viewModelScope.launch {
            try {
                actions.updateMessage(HistoryMessage.Undone(undo()))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.e(e, "Could not undo recorded component changes for operation %d", id)
                actions.updateMessage(HistoryMessage.UndoFailed())
            } finally {
                actions.update { it.copy(isUndoing = false) }
            }
        }
    }

    private fun MutableStateFlow<ActionState>.updateMessage(message: HistoryMessage) = update { it.copy(message = message) }

    private suspend fun loadEntries(): List<LoadedEntry> {
        val entries = history.getEntries(id)
        val defaults = loadManifestDefaults(entries)
        return entries.map { entry ->
            val declaration = ComponentDeclaration(
                id = entry.key.id,
                type = entry.type,
                exported = false,
                enabledInManifest = defaults[entry.key.id] ?: true,
            )
            val before = entry.before.displayState(declaration)
            val after = entry.after.displayState(declaration)
            val change = if (before == after || before == ComponentState.UNKNOWN) ComponentState.UNKNOWN else after
            LoadedEntry(entry, change)
        }
    }

    private suspend fun loadManifestDefaults(entries: List<OperationEntry>): Map<ComponentId, Boolean> {
        val packages = entries.asSequence()
            .filter { it.before.pm == PmSetting.DEFAULT || it.after.pm == PmSetting.DEFAULT }
            .groupBy { it.key.id.packageName }
        if (packages.isEmpty()) return emptyMap()

        val cachedUsers = deviceUsers.users.catch {
            Timber.w(it, "Could not read other users with cached component declarations")
            emit(emptyList())
        }.first().map { it.id } + ownUser
        val defaults = mutableMapOf<ComponentId, Boolean>()
        for ((packageName, changes) in packages) {
            val missing = changes.mapTo(mutableSetOf()) { it.key.id }
            // The APK declaration is shared across profiles. Prefer the profiles that recorded the change.
            val candidates = changes.map { it.key.userId } + cachedUsers
            for (user in candidates.distinct()) {
                val app = AppKey(packageName, user)
                components.components(app).catch {
                    Timber.w(it, "Could not read cached declarations for %s", app)
                    emit(emptyList())
                }.first().forEach { status ->
                    val declaration = status.declaration
                    if (missing.remove(declaration.id)) defaults[declaration.id] = declaration.enabledInManifest
                }
                if (missing.isEmpty()) break
            }
        }
        return defaults
    }

    private fun List<LoadedEntry>.groupByApp(device: DeviceLabels): List<HistoryAppGroup> = groupBy { it.entry.key.id.packageName }.map { (packageName, entries) ->
        HistoryAppGroup(
            appIcon = device.appIcon(packageName),
            appLabel = device.appLabel(packageName),
            entries = entries.map { (entry, change) ->
                val user = entry.key.userId
                val profile = if (device.profiles.size > 1) device.profiles[user] ?: ProfileLabel.Numbered(user.value) else null
                HistoryEntry(entry.key, profile, change)
            },
        )
    }

    private data class ActionState(
        val confirmLater: Boolean = false,
        val isUndoing: Boolean = false,
        val message: HistoryMessage? = null,
    )

    private data class LoadedEntry(val entry: OperationEntry, val change: ComponentState)
}

private fun ControlState.displayState(declaration: ComponentDeclaration): ComponentState {
    val status = ComponentStatus(declaration, this)
    return when {
        status.availability == Availability.Unknown -> ComponentState.UNKNOWN
        status.isSwitchOn -> ComponentState.ON
        else -> ComponentState.OFF
    }
}
