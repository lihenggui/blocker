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

import app.cash.turbine.test
import com.merxury.blocker.core.domain.history.UndoOperationsUseCase
import com.merxury.blocker.core.domain.history.UndoResult
import com.merxury.blocker.core.model.ComponentType.SERVICE
import com.merxury.blocker.core.model.data.Operation
import com.merxury.blocker.core.model.data.OperationEntry
import com.merxury.blocker.core.model.data.OperationSource.APP_DETAIL
import com.merxury.blocker.core.model.data.OperationSource.RESET_IFW
import com.merxury.blocker.core.model.data.OperationSource.UNDO
import com.merxury.blocker.core.testing.repository.TestOperationHistoryRepository
import com.merxury.blocker.core.testing.util.MainDispatcherRule
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

class HistoryViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()
    private val history = TestOperationHistoryRepository()
    private val undo: UndoOperationsUseCase = mock()
    private val timestamp = Instant.parse("2026-10-23T12:00:00Z")
    private val operation = Operation(0, timestamp, APP_DETAIL, entryCount = 1)
    private val entry = OperationEntry(0, "com.example", "com.example.Service", SERVICE, false, false, true, false)
    private val viewModel = HistoryViewModel(history, undo)

    @Test
    fun operations_loadAndFollowRepositoryChanges() = runTest {
        viewModel.operations.test {
            assertNull(awaitItem())
            assertEquals(emptyList(), awaitItem())
            val id = history.insertOperation(operation, listOf(entry))
            assertEquals(listOf(id), awaitItem()?.map { it.id })
        }
    }

    @Test
    fun expansion_loadsComponentSnapshotsAndTogglesClosed() = runTest {
        val id = history.insertOperation(operation, listOf(entry))
        viewModel.toggleOperation(id)
        viewModel.expandedEntries.test {
            assertNull(awaitItem())
            assertEquals(listOf(entry.copy(operationId = id)), awaitItem())
            viewModel.toggleOperation(id)
            assertEquals(emptyList(), awaitItem())
        }
        assertNull(viewModel.expandedOperationId.value)
    }

    @Test
    fun sinceConfirmation_countsDistinctComponentsFromNonUndoneOperations() = runTest {
        val first = history.insertOperation(operation, listOf(entry))
        history.insertOperation(operation, listOf(entry))
        history.markUndone(listOf(first), 99)
        viewModel.undoRequest.test {
            assertNull(awaitItem())
            viewModel.requestUndo(timestamp)
            assertEquals(UndoRequest.Since(timestamp, 1), awaitItem())
            viewModel.dismissUndo()
            assertNull(awaitItem())
        }
    }

    @Test
    fun noControllerChangeUntilConfirmation_andUndoRunsOnce() = runTest {
        val result = UndoResult(1, 0, 0, emptyMap())
        whenever(undo(operation.id)).thenReturn(result)
        viewModel.requestUndo(operation)
        assertTrue(viewModel.undoRequest.value is UndoRequest.Single)
        viewModel.undoResults.test {
            viewModel.confirmUndo()
            viewModel.confirmUndo()
            assertEquals(result, awaitItem().getOrThrow())
        }
        assertNull(viewModel.undoRequest.value)
        assertFalse(viewModel.isUndoing.value)
        verify(undo, times(1)).invoke(operation.id)
    }

    @Test
    fun undoFailure_isReportedAndClearsProgress() = runTest {
        whenever(undo(operation.id)).thenAnswer { throw IllegalStateException("Database unavailable") }
        viewModel.requestUndo(operation)
        viewModel.undoResults.test {
            viewModel.confirmUndo()
            assertEquals("Database unavailable", awaitItem().exceptionOrNull()?.message)
        }
        assertFalse(viewModel.isUndoing.value)
    }

    @Test
    fun resetUndoAndUndoneRows_doNotOfferUndo() {
        listOf(
            operation.copy(source = RESET_IFW),
            operation.copy(source = UNDO),
            operation.copy(undoneByOperationId = 10),
        ).forEach {
            assertFalse(it.isUndoable())
            viewModel.requestUndo(it)
            assertNull(viewModel.undoRequest.value)
        }
    }
}
