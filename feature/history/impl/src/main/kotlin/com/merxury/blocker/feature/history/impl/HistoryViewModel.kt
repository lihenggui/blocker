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

package com.merxury.blocker.feature.history.impl

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.merxury.blocker.core.data.respository.history.OperationHistoryRepository
import com.merxury.blocker.core.domain.history.UndoOperationsUseCase
import com.merxury.blocker.core.domain.history.UndoResult
import com.merxury.blocker.core.model.data.Operation
import com.merxury.blocker.core.model.data.OperationEntry
import com.merxury.blocker.core.model.data.OperationSource.RESET_IFW
import com.merxury.blocker.core.model.data.OperationSource.UNDO
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.time.Instant

sealed interface UndoRequest {
    data class Single(val operation: Operation) : UndoRequest
    data class Since(val timestamp: Instant, val entryCount: Int) : UndoRequest
}

internal fun Operation.isUndoable() = entryCount > 0 && undoneByOperationId == null && source != RESET_IFW && source != UNDO

@HiltViewModel
class HistoryViewModel @Inject constructor(
    private val historyRepository: OperationHistoryRepository,
    private val undoOperations: UndoOperationsUseCase,
) : ViewModel() {
    private val results = Channel<Result<UndoResult>>(Channel.BUFFERED)
    val undoResults = results.receiveAsFlow()
    val operations = historyRepository.observeOperations()
        .catch {
            results.send(Result.failure(it))
            emit(emptyList())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    private val _expandedOperationId = MutableStateFlow<Long?>(null)
    val expandedOperationId = _expandedOperationId.asStateFlow()
    val expandedEntries = combine(operations, expandedOperationId) { _, id ->
        if (id == null) emptyList() else historyRepository.getEntries(id)
    }
        .catch {
            results.send(Result.failure(it))
            emit(emptyList())
        }
        .stateIn<List<OperationEntry>?>(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    private val _undoRequest = MutableStateFlow<UndoRequest?>(null)
    val undoRequest = _undoRequest.asStateFlow()
    private val _isUndoing = MutableStateFlow(false)
    val isUndoing = _isUndoing.asStateFlow()
    private var requestJob: Job? = null

    fun toggleOperation(operationId: Long) {
        _expandedOperationId.value = if (_expandedOperationId.value == operationId) null else operationId
    }

    fun requestUndo(operation: Operation) {
        if (_isUndoing.value || !operation.isUndoable()) return
        requestJob?.cancel()
        _undoRequest.value = UndoRequest.Single(operation)
    }

    fun requestUndo(since: Instant) {
        if (_isUndoing.value) return
        requestJob?.cancel()
        requestJob = viewModelScope.launch {
            try {
                val count = historyRepository.getEntriesSince(since)
                    .distinctBy { it.packageName to it.componentName }.size
                _undoRequest.value = UndoRequest.Since(since, count)
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                results.send(Result.failure(e))
            }
        }
    }

    fun dismissUndo() {
        requestJob?.cancel()
        _undoRequest.value = null
    }

    fun confirmUndo() {
        val request = _undoRequest.value ?: return
        if (_isUndoing.value) return
        _undoRequest.value = null
        _isUndoing.value = true
        viewModelScope.launch {
            try {
                val result = when (request) {
                    is UndoRequest.Single -> undoOperations(request.operation.id)
                    is UndoRequest.Since -> undoOperations(request.timestamp)
                }
                results.send(Result.success(result))
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                results.send(Result.failure(e))
            } finally {
                _isUndoing.value = false
            }
        }
    }
}
