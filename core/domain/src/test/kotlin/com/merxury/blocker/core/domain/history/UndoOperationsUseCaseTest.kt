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

package com.merxury.blocker.core.domain.history

import android.content.pm.PackageInfo
import android.content.pm.ProviderInfo
import android.content.pm.ServiceInfo
import com.merxury.blocker.core.controllers.IController
import com.merxury.blocker.core.data.respository.history.OperationRecorder
import com.merxury.blocker.core.domain.history.UndoFailureReason.COMPONENT_MISSING
import com.merxury.blocker.core.domain.history.UndoFailureReason.ERROR
import com.merxury.blocker.core.domain.history.UndoFailureReason.IFW_FAILED
import com.merxury.blocker.core.domain.history.UndoFailureReason.IFW_PERMISSION_UNAVAILABLE
import com.merxury.blocker.core.domain.history.UndoFailureReason.PM_FAILED
import com.merxury.blocker.core.model.ComponentType.PROVIDER
import com.merxury.blocker.core.model.ComponentType.SERVICE
import com.merxury.blocker.core.model.data.ComponentInfo
import com.merxury.blocker.core.model.data.ControllerType.IFW
import com.merxury.blocker.core.model.data.ControllerType.PM
import com.merxury.blocker.core.model.data.ControllerType.SHIZUKU
import com.merxury.blocker.core.model.data.Operation
import com.merxury.blocker.core.model.data.OperationEntry
import com.merxury.blocker.core.model.data.OperationSource.APP_DETAIL
import com.merxury.blocker.core.model.data.OperationSource.RESET_IFW
import com.merxury.blocker.core.model.data.OperationSource.UNDO
import com.merxury.blocker.core.testing.controller.FakePackageInfoDataSource
import com.merxury.blocker.core.testing.controller.FakeRootAvailabilityChecker
import com.merxury.blocker.core.testing.repository.TestComponentRepository
import com.merxury.blocker.core.testing.repository.TestOperationHistoryRepository
import com.merxury.blocker.core.testing.repository.TestUserDataRepository
import com.merxury.blocker.core.testing.util.MainDispatcherRule
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.spy
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

class UndoOperationsUseCaseTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()
    private val history = TestOperationHistoryRepository()
    private val pm: IController = mock()
    private val shizuku: IController = mock()
    private val ifw: IController = mock()
    private val pmBlocked = mutableSetOf<String>()
    private val ifwBlocked = mutableSetOf<String>()
    private val userData = TestUserDataRepository()
    private val packages = spy(FakePackageInfoDataSource())
    private val rootChecker = spy(FakeRootAvailabilityChecker())
    private val component = ComponentInfo("com.example.app", "com.example.app.Service", SERVICE)
    private val timestamp = Clock.System.now() - 1.days
    private val recorder = OperationRecorder(history, pm, ifw, mainDispatcherRule.testDispatcher)
    private val undo = UndoOperationsUseCase(
        historyRepository = history,
        recorder = recorder,
        userDataRepository = userData,
        componentRepository = TestComponentRepository(),
        packageInfoDataSource = packages,
        rootChecker = rootChecker,
        pmController = pm,
        shizukuController = shizuku,
        ifwController = ifw,
    )

    @Before
    fun setup() = runTest {
        userData.setControllerType(PM)
        listOf(pm, shizuku).forEach { controller ->
            whenever(controller.checkComponentEnableState(any(), any())).thenAnswer { it.getArgument<String>(1) !in pmBlocked }
            whenever(controller.enable(any())).thenAnswer {
                pmBlocked.remove(it.getArgument<ComponentInfo>(0).name)
                true
            }
            whenever(controller.disable(any())).thenAnswer {
                pmBlocked.add(it.getArgument<ComponentInfo>(0).name)
                true
            }
        }
        whenever(ifw.checkComponentEnableState(any(), any())).thenAnswer { it.getArgument<String>(1) !in ifwBlocked }
        whenever(ifw.enable(any())).thenAnswer {
            ifwBlocked.remove(it.getArgument<ComponentInfo>(0).name)
            true
        }
        whenever(ifw.disable(any())).thenAnswer {
            ifwBlocked.add(it.getArgument<ComponentInfo>(0).name)
            true
        }
        packages.components = mapOf(
            component.packageName to mock<PackageInfo>().apply {
                services = arrayOf(mock<ServiceInfo>().apply { name = component.name })
            },
        )
    }

    private suspend fun record(
        component: ComponentInfo = this.component,
        timestamp: Instant = this.timestamp,
        beforePm: Boolean = false,
        afterPm: Boolean = true,
        beforeIfw: Boolean = false,
        afterIfw: Boolean = false,
    ) = history.insertOperation(
        Operation(0, timestamp, APP_DETAIL),
        listOf(OperationEntry(0, component.packageName, component.name, component.type, beforePm, beforeIfw, afterPm, afterIfw)),
    )

    @Test
    fun singleOperation_restoresBothDimensionsAndLogsUndo() = runTest {
        val id = record(afterIfw = true)
        pmBlocked.add(component.name)
        ifwBlocked.add(component.name)
        assertEquals(UndoResult(1, 0, 0, emptyMap()), undo(id))
        assertTrue(pmBlocked.isEmpty())
        assertTrue(ifwBlocked.isEmpty())
        val undoOperation = history.observeOperations().first().single { it.source == UNDO }
        assertEquals(undoOperation.id, history.observeOperations().first().find { it.id == id }?.undoneByOperationId)
        val entry = history.getEntries(undoOperation.id).single()
        assertTrue(entry.beforePmBlocked)
        assertTrue(entry.beforeIfwBlocked)
        assertFalse(entry.afterPmBlocked)
        assertFalse(entry.afterIfwBlocked)
    }

    @Test
    fun sinceDay_usesEarliestSnapshotAndDeduplicatesComponents() = runTest {
        val first = record()
        val second = record(timestamp = timestamp + 1.minutes, beforePm = true, afterPm = false)
        val third = record(timestamp = timestamp + 2.minutes, afterIfw = true)
        pmBlocked.add(component.name)
        ifwBlocked.add(component.name)
        assertEquals(UndoResult(1, 0, 0, emptyMap()), undo(timestamp))
        verify(pm, times(1)).enable(component)
        verify(ifw, times(1)).enable(component)
        assertTrue(history.observeOperations().first().filter { it.id in listOf(first, second, third) }.all { it.undoneByOperationId != null })
    }

    @Test
    fun undoingEnable_restoresBlockedStateInEachDimension() = runTest {
        val id = record(beforePm = true, afterPm = false, beforeIfw = true)
        assertEquals(UndoResult(1, 0, 0, emptyMap()), undo(id))
        verify(pm).disable(component)
        verify(ifw).disable(component)
    }

    @Test
    fun providerRecordedInIfwMode_restoresPmDimension() = runTest {
        userData.setControllerType(IFW)
        val provider = component.copy(type = PROVIDER)
        packages.components = mapOf(
            component.packageName to mock<PackageInfo>().apply {
                providers = arrayOf(mock<ProviderInfo>().apply { name = component.name })
            },
        )
        val id = record(component = provider)
        pmBlocked.add(component.name)
        assertEquals(1, undo(id).restored)
        verify(pm).enable(provider)
        verify(ifw, never()).enable(any())
    }

    @Test
    fun activeShizuku_restoresPmWithoutRoot() = runTest {
        userData.setControllerType(SHIZUKU)
        rootChecker.rootAvailable = false
        val id = record()
        pmBlocked.add(component.name)
        assertEquals(UndoResult(1, 0, 0, emptyMap()), undo(id))
        verify(shizuku).enable(component)
        verify(pm, never()).enable(any())
        verify(ifw, never()).enable(any())
    }

    @Test
    fun alreadyInTarget_afterReset_createsZeroEntryUndoAndMarksOriginal() = runTest {
        val id = record(afterPm = false, afterIfw = true)
        recorder.logOnly(RESET_IFW)
        assertEquals(UndoResult(0, 1, 0, emptyMap()), undo(id))
        val operations = history.observeOperations().first()
        val undoOperation = operations.single { it.source == UNDO }
        assertEquals(0, undoOperation.entryCount)
        assertEquals(undoOperation.id, operations.single { it.id == id }.undoneByOperationId)
        assertNull(operations.single { it.source == RESET_IFW }.undoneByOperationId)
        verify(pm, never()).enable(any())
        verify(ifw, never()).enable(any())
    }

    @Test
    fun missingComponent_reportsFailureAndLeavesOriginalRetryable() = runTest {
        val id = record()
        packages.components = mapOf(component.packageName to mock<PackageInfo>())
        val result = undo(id)
        assertEquals(1, result.failed)
        assertEquals(mapOf(component to listOf(COMPONENT_MISSING)), result.failureReasons)
        assertNull(history.observeOperations().first().single { it.id == id }.undoneByOperationId)
        verify(pm, never()).enable(any())
    }

    @Test
    fun missingIfwPermission_stillRestoresPmAndReportsFailure() = runTest {
        val id = record(afterIfw = true)
        userData.setControllerType(SHIZUKU)
        rootChecker.rootAvailable = false
        pmBlocked.add(component.name)
        // Unprivileged IFW reads appear enabled; do not mistake that for a successful undo.
        whenever(ifw.checkComponentEnableState(any(), any())).thenReturn(true)
        val result = undo(id)
        assertEquals(1, result.failed)
        assertEquals(mapOf(component to listOf(IFW_PERMISSION_UNAVAILABLE)), result.failureReasons)
        verify(shizuku).enable(component)
        verify(ifw, never()).enable(any())
        assertNull(history.observeOperations().first().single { it.id == id }.undoneByOperationId)
    }

    @Test
    fun failedPmRestore_stillRestoresIfwAndKeepsOriginalRetryable() = runTest {
        val id = record(afterIfw = true)
        pmBlocked.add(component.name)
        ifwBlocked.add(component.name)
        doReturn(false).whenever(pm).enable(component)
        assertTrue(component.name in pmBlocked)
        val result = undo(id)
        assertEquals(1, result.failed)
        assertEquals(mapOf(component to listOf(PM_FAILED)), result.failureReasons)
        verify(pm).enable(component)
        verify(ifw).enable(component)
        assertTrue(ifwBlocked.isEmpty())
        assertNull(history.observeOperations().first().single { it.id == id }.undoneByOperationId)
        val undoEntry = history.getEntries(history.observeOperations().first().single { it.source == UNDO }.id).single()
        assertTrue(undoEntry.afterPmBlocked)
        assertFalse(undoEntry.afterIfwBlocked)
    }

    @Test
    fun sinceDay_marksOnlyOperationsWhoseComponentsSucceeded() = runTest {
        val succeeded = record()
        val missing = record(component = component.copy(name = "com.example.app.Missing"))
        pmBlocked.add(component.name)
        val result = undo(timestamp)
        assertEquals(1, result.restored)
        assertEquals(1, result.failed)
        val operations = history.observeOperations().first()
        assertNotNull(operations.single { it.id == succeeded }.undoneByOperationId)
        assertNull(operations.single { it.id == missing }.undoneByOperationId)
    }

    @Test
    fun eachRun_cachesComponentsPerPackageAndInitializesControllersOnce() = runTest {
        val other = component.copy(name = "com.example.app.OtherService")
        val otherPackage = component.copy(packageName = "com.example.other", name = "com.example.other.Service")
        packages.components = mapOf(
            component.packageName to mock<PackageInfo>().apply {
                services = arrayOf(
                    mock<ServiceInfo>().apply { name = component.name },
                    mock<ServiceInfo>().apply { name = other.name },
                )
            },
            otherPackage.packageName to mock<PackageInfo>().apply {
                services = arrayOf(mock<ServiceInfo>().apply { name = otherPackage.name })
            },
        )
        repeat(2) {
            listOf(component, other, otherPackage).forEach {
                record(component = it)
                pmBlocked.add(it.name)
            }
            assertEquals(UndoResult(3, 0, 0, emptyMap()), undo(timestamp))
        }
        verify(packages, times(2)).getApplicationComponents(component.packageName)
        verify(packages, times(2)).getApplicationComponents(otherPackage.packageName)
        verify(pm, times(2)).init()
        verify(shizuku, never()).init()
        verify(rootChecker, times(2)).isRootAvailable()
    }

    @Test
    fun failedPmInitialization_stillRestoresIfwAndReportsPmFailure() = runTest {
        val id = record(afterIfw = true)
        pmBlocked.add(component.name)
        ifwBlocked.add(component.name)
        doThrow(IllegalStateException("PM permission unavailable")).whenever(pm).init()
        val result = undo(id)
        assertEquals(UndoResult(0, 0, 1, mapOf(component to listOf(PM_FAILED))), result)
        verify(pm).init()
        verify(pm, never()).enable(any())
        verify(ifw).enable(component)
        assertTrue(component.name in pmBlocked)
        assertTrue(ifwBlocked.isEmpty())
        assertNull(history.observeOperations().first().single { it.id == id }.undoneByOperationId)
    }

    @Test
    fun failedIfwRestore_stillRestoresPmAndReportsIfwFailure() = runTest {
        val id = record(afterIfw = true)
        pmBlocked.add(component.name)
        ifwBlocked.add(component.name)
        doReturn(false).whenever(ifw).enable(component)
        val result = undo(id)
        assertEquals(UndoResult(0, 0, 1, mapOf(component to listOf(IFW_FAILED))), result)
        verify(pm).enable(component)
        verify(ifw).enable(component)
        assertTrue(pmBlocked.isEmpty())
        assertTrue(component.name in ifwBlocked)
        assertNull(history.observeOperations().first().single { it.id == id }.undoneByOperationId)
    }

    @Test
    fun packageLookupFailure_isCachedAndReportsErrorForEachComponent() = runTest {
        val other = ComponentInfo(component.packageName, "com.example.app.OtherService", SERVICE)
        record()
        record(component = other)
        doThrow(IllegalStateException("Package lookup failed")).whenever(packages).getApplicationComponents(component.packageName)
        val result = undo(timestamp)
        assertEquals(
            UndoResult(0, 0, 2, mapOf(component to listOf(ERROR), other to listOf(ERROR))),
            result,
        )
        verify(packages).getApplicationComponents(component.packageName)
        verify(pm, never()).enable(any())
        verify(ifw, never()).enable(any())
    }

    @Test
    fun undoneResetAndUndoOperations_areNotReversedAgain() = runTest {
        val id = record()
        pmBlocked.add(component.name)
        undo(id)
        val undoId = history.observeOperations().first().single { it.source == UNDO }.id
        val resetId = recorder.logOnly(RESET_IFW)
        assertEquals(UndoResult(0, 0, 0, emptyMap()), undo(id))
        assertEquals(UndoResult(0, 0, 0, emptyMap()), undo(resetId))
        assertEquals(UndoResult(0, 0, 0, emptyMap()), undo(undoId))
        assertEquals(UndoResult(0, 0, 0, emptyMap()), undo(timestamp))
        assertEquals(5, history.observeOperations().first().count { it.source == UNDO })
        verify(pm, times(1)).enable(component)
    }
}
