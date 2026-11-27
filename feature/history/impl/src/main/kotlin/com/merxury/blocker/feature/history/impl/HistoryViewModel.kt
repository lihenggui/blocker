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
import com.merxury.blocker.core.model.history.Operation
import com.merxury.blocker.core.model.history.OperationEntry
import com.merxury.blocker.core.ui.profile.ProfileLabel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import kotlin.time.toJavaInstant
import kotlin.time.toKotlinInstant

@HiltViewModel
internal class HistoryViewModel @Inject constructor(
    private val history: OperationHistoryRepository,
    private val components: ComponentStatusRepository,
    installedApps: InstalledAppRepository,
    private val deviceUsers: DeviceUserRepository,
    selectedUser: SelectedUserRepository,
    generalRules: GeneralRuleRepository,
    timeZoneMonitor: TimeZoneMonitor,
) : ViewModel() {

    private val ownUser = selectedUser.ownUser
    private val local = MutableStateFlow(LocalState())
    private var expansionJob: Job? = null

    private val operations = history.observeOperations()
        .map { Result.success(it) }
        .catch {
            Timber.e(it, "Could not load operation history")
            emit(Result.failure(it))
        }

    val uiState: StateFlow<HistoryUiState> = combine(
        operations.withRuleLabels(generalRules),
        observeDeviceLabels(deviceUsers, installedApps, ownUser),
        timeZoneMonitor.currentTimeZone,
        local,
    ) { (result, rules), device, timeZone, state ->
        val zone = ZoneId.of(timeZone.id)
        HistoryUiState(
            days = result.getOrNull()?.groupByDay(zone, device, rules),
            zone = zone,
            loadFailed = result.isFailure,
            expanded = state.expanded?.let { expansion ->
                ExpandedOperation(
                    id = expansion.id,
                    entries = expansion.entries?.map { it.toUiEntry(device) },
                    loadFailed = expansion.loadFailed,
                )
            },
            confirmation = state.confirmation,
            isUndoing = state.isUndoing,
            message = state.message,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HistoryUiState())

    fun toggleOperation(operation: HistoryOperation) {
        if (operation.operation.entryCount == 0) return
        val id = operation.operation.id
        val expand = local.value.expanded?.id != id
        expansionJob?.cancel()
        local.update { it.copy(expanded = if (expand) Expansion(id) else null) }
        if (expand) {
            expansionJob = viewModelScope.launch {
                try {
                    val entries = loadEntries(id)
                    local.update { it.copy(expanded = Expansion(id, entries)) }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Timber.e(e, "Could not load operation entries for %d", id)
                    local.update { it.copy(expanded = Expansion(id, loadFailed = true)) }
                }
            }
        }
    }

    fun requestUndo(operation: HistoryOperation) {
        if (!operation.canUndo || local.value.isUndoing) return
        local.update { it.copy(confirmation = UndoRequest.Single(operation.operation.id, operation.operation.entryCount)) }
    }

    fun requestUndoSince(day: LocalDate) {
        val snapshot = uiState.value
        if (local.value.isUndoing || snapshot.days?.any { it.date == day && it.canUndoSince } != true) return
        val start = day.atStartOfDay(snapshot.zone).toInstant().toKotlinInstant()
        local.update { it.copy(confirmation = UndoRequest.Since(day, start)) }
    }

    fun cancelUndo() {
        local.update { it.copy(confirmation = null) }
    }

    fun confirmUndo() {
        val request = local.value.confirmation ?: return
        local.update { it.copy(confirmation = null, isUndoing = true) }
        viewModelScope.launch {
            try {
                val result = when (request) {
                    is UndoRequest.Single -> history.undoOperation(request.id)
                    is UndoRequest.Since -> history.undoSince(request.start)
                }
                local.update { it.copy(message = HistoryMessage.Undone(result)) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.e(e, "Could not undo recorded component changes")
                local.update { it.copy(message = HistoryMessage.UndoFailed()) }
            } finally {
                local.update { it.copy(isUndoing = false) }
            }
        }
    }

    fun messageShown(message: HistoryMessage) {
        local.update { if (it.message === message) it.copy(message = null) else it }
    }

    private suspend fun loadEntries(id: Long): List<LoadedEntry> {
        val entries = history.getEntries(id)
        val defaults = loadManifestDefaults(entries)
        return entries.map { entry ->
            val declaration = ComponentDeclaration(
                id = entry.key.id,
                type = entry.type,
                exported = false,
                enabledInManifest = defaults[entry.key.id] ?: true,
            )
            LoadedEntry(entry, entry.before.displayState(declaration), entry.after.displayState(declaration))
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

    private data class LocalState(
        val expanded: Expansion? = null,
        val confirmation: UndoRequest? = null,
        val isUndoing: Boolean = false,
        val message: HistoryMessage? = null,
    )

    private data class Expansion(val id: Long, val entries: List<LoadedEntry>? = null, val loadFailed: Boolean = false)

    private data class LoadedEntry(val entry: OperationEntry, val before: ComponentState, val after: ComponentState) {
        fun toUiEntry(device: DeviceLabels): HistoryEntry {
            val key = entry.key
            val app = AppKey(key.id.packageName, key.userId)
            val profile = if (device.profiles.size > 1) device.profiles[key.userId] ?: ProfileLabel.Numbered(key.userId.value) else null
            return HistoryEntry(key.id.simpleName, device.apps[app] ?: device.appLabel(app.packageName), profile, before, after)
        }
    }
}

private fun ControlState.displayState(declaration: ComponentDeclaration): ComponentState {
    val status = ComponentStatus(declaration, this)
    return when {
        status.availability == Availability.Unknown -> ComponentState.UNKNOWN
        status.isSwitchOn -> ComponentState.ON
        else -> ComponentState.OFF
    }
}

private fun List<Operation>.groupByDay(zone: ZoneId, device: DeviceLabels, rules: Map<Int, String>): List<HistoryDay> {
    var canUndoSince = false
    return sortedWith(compareByDescending<Operation> { it.timestamp }.thenByDescending { it.id })
        .groupBy { it.timestamp.toJavaInstant().atZone(zone).toLocalDate() }
        .map { (day, operations) ->
            val rows = operations.map { HistoryOperation(it, it.reference(device, rules)) }
            canUndoSince = canUndoSince || rows.any { it.canUndo }
            HistoryDay(day, rows, canUndoSince)
        }
}
