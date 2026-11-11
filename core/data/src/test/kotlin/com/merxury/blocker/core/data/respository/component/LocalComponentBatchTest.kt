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
import com.merxury.blocker.core.database.app.AppComponentDao
import com.merxury.blocker.core.database.app.AppComponentEntity
import com.merxury.blocker.core.model.ComponentState
import com.merxury.blocker.core.model.ComponentType.PROVIDER
import com.merxury.blocker.core.model.ComponentType.SERVICE
import com.merxury.blocker.core.model.data.ComponentInfo
import com.merxury.blocker.core.model.data.ControllerType
import com.merxury.blocker.core.testing.repository.TestUserDataRepository
import com.merxury.blocker.core.testing.repository.defaultUserData
import com.merxury.blocker.core.testing.util.MainDispatcherRule
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LocalComponentBatchTest {
    @get:Rule
    val dispatcher = MainDispatcherRule()
    private val dao = mock<AppComponentDao>()
    private val users = TestUserDataRepository()
    private val pm = StatefulController()
    private val ifw = StatefulController()
    private val shizuku = StatefulController()
    private val repository = LocalComponentRepository(mock(), dao, users, pm, ifw, shizuku, mock(), dispatcher.testDispatcher)
    private val first = ComponentInfo("com.example", "com.tracker.First", SERVICE)
    private val second = first.copy(name = "com.tracker.Second")

    @Test
    fun failedCommandWithAlreadyEnabledStateDoesNotReportSuccess() = runTest {
        users.sendUserData(defaultUserData.copy(controllerType = ControllerType.PM))
        pm.failures.add(first.name)

        assertFailsWith<IllegalStateException> {
            repository.batchControlComponent(listOf(first), true).toList()
        }
    }

    @Test
    fun pmBatchPreservesTheExistingIndependentIfwState() = runTest {
        users.sendUserData(defaultUserData.copy(controllerType = ControllerType.PM))
        pm.states[first.name] = false
        ifw.states[first.name] = false

        val result = repository.batchControlComponent(listOf(first), true).toList().single()

        assertFalse(result.pmBlocked)
        assertTrue(result.ifwBlocked)
        assertEquals(emptyList(), ifw.enabled)
    }

    @Test
    fun ifwProvidersOnlyUsePmAndEachComponentIsEmittedOnce() = runTest {
        users.sendUserData(defaultUserData)
        val provider = first.copy(name = "com.tracker.Provider", type = PROVIDER)

        val result = repository.batchControlComponent(listOf(first, provider, first), false).toList()

        assertEquals(listOf(provider), pm.disabled)
        assertEquals(listOf(first), ifw.disabled)
        assertEquals(2, result.size)
        assertEquals(2, result.map { it.name }.toSet().size)
        assertTrue(result.first { it.type == PROVIDER }.pmBlocked)
        assertTrue(result.first { it.type == SERVICE }.ifwBlocked)
    }

    @Test
    fun enablingIfwUsesFreshPmStateInsteadOfStaleDatabaseFlags() = runTest {
        users.sendUserData(defaultUserData)
        pm.states[first.name] = false
        ifw.states[first.name] = false

        val result = repository.batchControlComponent(listOf(first), true).toList().single()

        assertEquals(listOf(first), pm.enabled)
        assertFalse(result.pmBlocked)
        assertFalse(result.ifwBlocked)
    }

    @Test
    fun cancellationPersistsAlreadyChangedComponentsBeforeReturning() = runTest {
        users.sendUserData(defaultUserData.copy(controllerType = ControllerType.PM))
        val changed = CompletableDeferred<Unit>()
        val hold = CompletableDeferred<Unit>()
        pm.afterDisable = {
            changed.complete(Unit)
            hold.await()
        }
        val job = launch { repository.batchControlComponent(listOf(first, second), false).collect() }
        changed.await()

        job.cancelAndJoin()

        val saved = argumentCaptor<List<AppComponentEntity>>()
        verify(dao).upsertComponentList(saved.capture())
        assertEquals(listOf(true, false), saved.firstValue.map { it.pmBlocked })
    }

    @Test
    fun failedComponentIsReconciledAndDoesNotReportCompletion() = runTest {
        users.sendUserData(defaultUserData.copy(controllerType = ControllerType.PM))
        pm.failures.add(second.name)
        val emitted = mutableListOf<ComponentInfo>()

        assertFailsWith<IllegalStateException> {
            repository.batchControlComponent(listOf(first, second), false).toList(emitted)
        }

        val saved = argumentCaptor<List<AppComponentEntity>>()
        verify(dao).upsertComponentList(saved.capture())
        assertEquals(listOf(true, false), saved.firstValue.map { it.pmBlocked })
        assertEquals(emptyList(), emitted)
    }

    private class StatefulController : IController {
        val states = mutableMapOf<String, Boolean>()
        val failures = mutableSetOf<String>()
        val disabled = mutableListOf<ComponentInfo>()
        val enabled = mutableListOf<ComponentInfo>()
        var afterDisable: suspend () -> Unit = {}
        override suspend fun switchComponent(component: ComponentInfo, state: ComponentState): Boolean = if (state == ComponentState.DISABLED) disable(component) else enable(component)
        override suspend fun enable(component: ComponentInfo): Boolean {
            enabled.add(component)
            if (component.name in failures) return false
            states[component.name] = true
            return true
        }
        override suspend fun disable(component: ComponentInfo): Boolean {
            disabled.add(component)
            if (component.name in failures) return false
            states[component.name] = false
            afterDisable()
            return true
        }
        override suspend fun checkComponentEnableState(packageName: String, componentName: String) = states[componentName] ?: true
    }
}
