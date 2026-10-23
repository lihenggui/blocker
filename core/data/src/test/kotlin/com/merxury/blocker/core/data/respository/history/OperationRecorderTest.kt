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

package com.merxury.blocker.core.data.respository.history

import com.merxury.blocker.core.controllers.IController
import com.merxury.blocker.core.model.ComponentType.SERVICE
import com.merxury.blocker.core.model.data.ComponentInfo
import com.merxury.blocker.core.model.data.Operation
import com.merxury.blocker.core.model.data.OperationSource.APP_DETAIL
import com.merxury.blocker.core.model.data.OperationSource.EXTERNAL_PROVIDER
import com.merxury.blocker.core.model.data.OperationSource.IMPORT_BLOCKER_RULE
import com.merxury.blocker.core.model.data.OperationSource.RESET_IFW
import com.merxury.blocker.core.model.data.OperationSource.UNDO
import com.merxury.blocker.core.testing.repository.TestOperationHistoryRepository
import com.merxury.blocker.core.testing.util.MainDispatcherRule
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days

@OptIn(ExperimentalCoroutinesApi::class)
class OperationRecorderTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()
    private val history = TestOperationHistoryRepository()
    private val pm: IController = mock()
    private val ifw: IController = mock()
    private val pmBlocked = mutableSetOf<String>()
    private val ifwBlocked = mutableSetOf<String>()
    private val component = ComponentInfo("com.example.app", "com.example.app.Service", SERVICE)
    private val recorder = OperationRecorder(history, pm, ifw, mainDispatcherRule.testDispatcher)

    @Before
    fun setup() = runTest {
        whenever(pm.checkComponentEnableState(any(), any())).thenAnswer { it.getArgument<String>(1) !in pmBlocked }
        whenever(ifw.checkComponentEnableState(any(), any())).thenAnswer { it.getArgument<String>(1) !in ifwBlocked }
        whenever(pm.disable(any())).thenAnswer {
            pmBlocked.add(it.getArgument<ComponentInfo>(0).name)
            true
        }
        whenever(pm.enable(any())).thenAnswer {
            pmBlocked.remove(it.getArgument<ComponentInfo>(0).name)
            true
        }
        whenever(ifw.disable(any())).thenAnswer {
            ifwBlocked.add(it.getArgument<ComponentInfo>(0).name)
            true
        }
    }

    @Test
    fun unchangedAndFailedControllerCalls_leaveNoOperation() = runTest {
        recorder.withOperation(APP_DETAIL) { track(listOf(component)) { false } }
        assertTrue(history.observeOperations().first().isEmpty())
    }

    @Test
    fun readsRealStatesInsteadOfCachedFlags_andRecordsBothDimensions() = runTest {
        val result = recorder.withOperation(APP_DETAIL, component.packageName) {
            track(listOf(component.copy(pmBlocked = true))) {
                pm.disable(component)
                ifw.disable(component)
                "result"
            }
        }
        assertEquals("result", result)
        val operation = history.observeOperations().first().single()
        assertEquals(APP_DETAIL, operation.source)
        assertEquals(component.packageName, operation.ref)
        val entry = history.getEntries(operation.id).single()
        assertFalse(entry.beforePmBlocked)
        assertFalse(entry.beforeIfwBlocked)
        assertTrue(entry.afterPmBlocked)
        assertTrue(entry.afterIfwBlocked)
    }

    @Test
    fun eachTrackFlushesBeforeOperationCompletes_andFailureKeepsPartialWork() = runTest {
        assertFailsWith<IllegalStateException> {
            recorder.withOperation(IMPORT_BLOCKER_RULE) {
                track(listOf(component)) { pm.disable(component) }
                assertEquals(1, history.observeOperations().first().single().entryCount)
                error("Next package failed")
            }
        }
        assertEquals(1, history.observeOperations().first().single().entryCount)
    }

    @Test
    fun failingAction_recordsItsPartialChangesAndRethrows() = runTest {
        assertFailsWith<IllegalStateException> {
            recorder.withOperation(APP_DETAIL) {
                track(listOf(component)) {
                    pm.disable(component)
                    error("IFW failed after PM changed")
                }
            }
        }
        val entry = history.getEntries(history.observeOperations().first().single().id).single()
        assertTrue(entry.afterPmBlocked)
        assertFalse(entry.afterIfwBlocked)
    }

    @Test
    fun cancellation_flushesChangesBeforeJobFinishes() = runTest {
        val started = CompletableDeferred<Unit>()
        val job = launch {
            recorder.withOperation(IMPORT_BLOCKER_RULE) {
                track(listOf(component)) {
                    pm.disable(component)
                    started.complete(Unit)
                    awaitCancellation()
                }
            }
        }
        started.await()
        job.cancelAndJoin()
        val operation = history.observeOperations().first().single()
        assertTrue(history.getEntries(operation.id).single().afterPmBlocked)
    }

    @Test
    fun nestedRepositoryCalls_shareOneProviderOperation() = runTest {
        val other = component.copy(name = "com.example.app.OtherService")
        recorder.withOperation(EXTERNAL_PROVIDER, "com.example.caller") {
            listOf(component, other).forEach { target ->
                recorder.withOperation(APP_DETAIL, target.packageName) {
                    track(listOf(target)) { pm.disable(target) }
                }
            }
        }
        val operation = history.observeOperations().first().single()
        assertEquals(EXTERNAL_PROVIDER, operation.source)
        assertEquals("com.example.caller", operation.ref)
        assertEquals(2, operation.entryCount)
    }

    @Test
    fun repeatedComponent_preservesFirstBeforeStateAcrossFlushes() = runTest {
        recorder.withOperation(IMPORT_BLOCKER_RULE) {
            track(listOf(component)) { pm.disable(component) }
            track(listOf(component)) { ifw.disable(component) }
        }
        val entry = history.getEntries(history.observeOperations().first().single().id).single()
        assertFalse(entry.beforePmBlocked)
        assertTrue(entry.afterPmBlocked)
        assertTrue(entry.afterIfwBlocked)
    }

    @Test
    fun repositoryAfterState_isReadOnlyOnce() = runTest {
        recorder.withOperation(APP_DETAIL) {
            track(listOf(component)) {
                pm.disable(component)
                recorder.readComponentState(component)
            }
        }
        verify(pm, times(2)).checkComponentEnableState(component.packageName, component.name)
        verify(ifw, times(2)).checkComponentEnableState(component.packageName, component.name)
    }

    @Test
    fun logOnly_createsResetAndZeroEntryUndoRows() = runTest {
        recorder.logOnly(RESET_IFW, component.packageName)
        recorder.withOperation(UNDO) {
            val id = recorder.logOnly(UNDO)
            track(listOf(component)) { Unit }
            assertEquals(id, recorder.logOnly(UNDO))
        }
        val operations = history.observeOperations().first()
        assertEquals(listOf(UNDO, RESET_IFW), operations.map { it.source })
        assertTrue(operations.all { it.entryCount == 0 })
    }

    @Test
    fun startingOperation_prunesNinetyDayHistory() = runTest {
        val now = Clock.System.now()
        val old = history.insertOperation(Operation(0, now - 91.days, APP_DETAIL), emptyList())
        val retained = history.insertOperation(Operation(0, now - 89.days, APP_DETAIL), emptyList())
        recorder.withOperation(APP_DETAIL) { Unit }
        assertEquals(listOf(retained), history.observeOperations().first().map { it.id })
        assertTrue(history.getEntries(old).isEmpty())
    }

    @Test
    fun overlappingOperations_doNotBlockAndKeepSeparateScopes() = runTest {
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val first = async {
            recorder.withOperation(IMPORT_BLOCKER_RULE) {
                track(listOf(component)) {
                    pm.disable(component)
                    recorder.readComponentState(component)
                    entered.complete(Unit)
                    release.await()
                }
            }
        }
        entered.await()
        val second = async {
            recorder.withOperation(APP_DETAIL, component.packageName) {
                track(listOf(component)) {
                    pm.enable(component)
                    recorder.readComponentState(component)
                }
            }
        }
        runCurrent()
        assertTrue(second.isCompleted)
        second.await()
        val toggle = history.observeOperations().first().single()
        assertEquals(APP_DETAIL, toggle.source)
        assertEquals(component.packageName, toggle.ref)
        val toggleEntry = history.getEntries(toggle.id).single()
        assertTrue(toggleEntry.beforePmBlocked)
        assertFalse(toggleEntry.afterPmBlocked)
        release.complete(Unit)
        first.await()
        val imported = history.observeOperations().first().single { it.source == IMPORT_BLOCKER_RULE }
        val importEntry = history.getEntries(imported.id).single()
        assertFalse(importEntry.beforePmBlocked)
        assertTrue(importEntry.afterPmBlocked)
        assertEquals(2, history.observeOperations().first().size)
    }
}
