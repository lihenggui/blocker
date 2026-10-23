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

package com.merxury.blocker.core.data.respository.component

import com.merxury.blocker.core.controllers.IController
import com.merxury.blocker.core.data.respository.history.OperationRecorder
import com.merxury.blocker.core.database.app.AppComponentDao
import com.merxury.blocker.core.database.app.toAppComponentEntity
import com.merxury.blocker.core.model.ComponentType.PROVIDER
import com.merxury.blocker.core.model.ComponentType.SERVICE
import com.merxury.blocker.core.model.data.ComponentInfo
import com.merxury.blocker.core.model.data.ControllerType.IFW
import com.merxury.blocker.core.model.data.ControllerType.IFW_PLUS_PM
import com.merxury.blocker.core.model.data.OperationSource.APP_DETAIL
import com.merxury.blocker.core.model.data.OperationSource.DEBLOATER
import com.merxury.blocker.core.model.data.OperationSource.EXTERNAL_PROVIDER
import com.merxury.blocker.core.testing.repository.TestOperationHistoryRepository
import com.merxury.blocker.core.testing.repository.TestUserDataRepository
import com.merxury.blocker.core.testing.util.MainDispatcherRule
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LocalComponentRepositoryHistoryTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()
    private val history = TestOperationHistoryRepository()
    private val pm: IController = mock()
    private val ifw: IController = mock()
    private val shizuku: IController = mock()
    private val combined: IController = mock()
    private val dao: AppComponentDao = mock()
    private val userData = TestUserDataRepository()
    private val pmBlocked = mutableSetOf<String>()
    private val ifwBlocked = mutableSetOf<String>()
    private val component = ComponentInfo("com.example.app", "com.example.app.Component", SERVICE)
    private val recorder = OperationRecorder(history, pm, ifw, mainDispatcherRule.testDispatcher)
    private val repository = LocalComponentRepository(
        localDataSource = mock(),
        appComponentDao = dao,
        userDataRepository = userData,
        recorder = recorder,
        pmController = pm,
        ifwController = ifw,
        shizukuController = shizuku,
        combinedController = combined,
        ioDispatcher = mainDispatcherRule.testDispatcher,
    )

    @Before
    fun setup() = runTest {
        userData.setControllerType(IFW)
        whenever(pm.checkComponentEnableState(any(), any())).thenAnswer { it.getArgument<String>(1) !in pmBlocked }
        whenever(ifw.checkComponentEnableState(any(), any())).thenAnswer { it.getArgument<String>(1) !in ifwBlocked }
        whenever(pm.disable(any())).thenAnswer {
            pmBlocked.add(it.getArgument<ComponentInfo>(0).name)
            true
        }
        whenever(dao.getByPackageNameAndComponentName(any(), any())).thenAnswer {
            flowOf(component.copy(packageName = it.getArgument(0), name = it.getArgument(1)).toAppComponentEntity())
        }
    }

    @Test
    fun ifwProvider_usesPmFallbackAndRecordsPmSnapshot() = runTest {
        val provider = component.copy(type = PROVIDER)
        repository.controlComponent(provider, false, source = APP_DETAIL, ref = provider.packageName).first()
        val operation = history.observeOperations().first().single()
        val entry = history.getEntries(operation.id).single()
        assertEquals(PROVIDER, entry.componentType)
        assertTrue(entry.afterPmBlocked)
        assertFalse(entry.afterIfwBlocked)
        assertEquals(APP_DETAIL, operation.source)
        assertEquals(provider.packageName, operation.ref)
        verify(ifw, never()).disable(any())
        verify(pm, times(2)).checkComponentEnableState(provider.packageName, provider.name)
        verify(ifw, times(2)).checkComponentEnableState(provider.packageName, provider.name)
    }

    @Test
    fun ifwBatchProvider_doesNotPassProviderToIfwController() = runTest {
        val provider = component.copy(type = PROVIDER)
        assertEquals(listOf(provider), repository.batchControlComponent(listOf(provider), false, source = APP_DETAIL).toList())
        verify(ifw, never()).batchDisable(any(), any())
        val entry = history.getEntries(history.observeOperations().first().single().id).single()
        assertTrue(entry.afterPmBlocked)
        assertFalse(entry.afterIfwBlocked)
        verify(pm, times(2)).checkComponentEnableState(provider.packageName, provider.name)
        verify(ifw, times(2)).checkComponentEnableState(provider.packageName, provider.name)
    }

    @Test
    fun providerCall_enclosesMultipleRepositoryFlowsInOneOperation() = runTest {
        val other = component.copy(name = "com.example.app.Other", type = PROVIDER)
        recorder.withOperation(EXTERNAL_PROVIDER, "com.example.caller") {
            listOf(component.copy(type = PROVIDER), other).forEach { target ->
                repository.controlComponent(target, false, source = EXTERNAL_PROVIDER, ref = "com.example.caller").first()
            }
        }
        val operation = history.observeOperations().first().single()
        assertEquals("com.example.caller", operation.ref)
        assertEquals(2, operation.entryCount)
    }

    @Test
    fun failedControllerCall_withoutStateChangeLeavesNoRow() = runTest {
        val provider = component.copy(type = PROVIDER)
        whenever(pm.disable(provider)).thenReturn(false)
        assertFalse(repository.controlComponent(provider, false, source = APP_DETAIL).first())
        assertTrue(history.observeOperations().first().isEmpty())
    }

    @Test
    fun combinedSingleChange_recordsBothDimensionsWithDebloaterSource() = runTest {
        whenever(combined.disable(component)).thenAnswer {
            pmBlocked.add(component.name)
            ifwBlocked.add(component.name)
            true
        }
        repository.controlComponent(component, false, source = DEBLOATER, controllerType = IFW_PLUS_PM).first()
        val operation = history.observeOperations().first().single()
        val entry = history.getEntries(operation.id).single()
        assertEquals(DEBLOATER, operation.source)
        assertTrue(entry.afterPmBlocked)
        assertTrue(entry.afterIfwBlocked)
    }
}
