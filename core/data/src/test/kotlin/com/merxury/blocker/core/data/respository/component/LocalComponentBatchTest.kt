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
import com.merxury.blocker.core.database.app.toAppComponentEntity
import com.merxury.blocker.core.exception.RootUnavailableException
import com.merxury.blocker.core.model.ComponentState
import com.merxury.blocker.core.model.ComponentType.PROVIDER
import com.merxury.blocker.core.model.ComponentType.SERVICE
import com.merxury.blocker.core.model.data.ComponentInfo
import com.merxury.blocker.core.model.data.ControllerType
import com.merxury.blocker.core.testing.repository.TestUserDataRepository
import com.merxury.blocker.core.testing.repository.defaultUserData
import com.merxury.blocker.core.testing.util.MainDispatcherRule
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
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
    private val last = ComponentInfo("com.last", "com.tracker.Last", SERVICE)

    @Test
    fun givenAlreadyEnabledComponentAndFailedCommand_whenEnabling_thenDoesNotReportSuccess() = runTest {
        users.sendUserData(defaultUserData.copy(controllerType = ControllerType.PM))
        pm.failures.add(first.name)

        val result = repository.batchControlComponent(listOf(first), true).toList().single()

        assertFalse(result.succeeded)
    }

    @Test
    fun givenIndependentIfwState_whenApplyingPmBatch_thenPreservesIfwState() = runTest {
        users.sendUserData(defaultUserData.copy(controllerType = ControllerType.PM))
        pm.states[first.name] = false
        ifw.states[first.name] = false

        val result = repository.batchControlComponent(listOf(first), true).toList().single()

        assertTrue(result.succeeded)
        assertFalse(result.component.pmBlocked)
        assertTrue(result.component.ifwBlocked)
        assertEquals(emptyList(), ifw.enabled)
    }

    @Test
    fun givenProvidersAndOtherComponents_whenApplyingIfwBatch_thenRoutesProvidersToPmAndEmitsOnce() = runTest {
        users.sendUserData(defaultUserData)
        val provider = first.copy(name = "com.tracker.Provider", type = PROVIDER)

        val results = repository.batchControlComponent(listOf(first, provider, first), false).toList()

        assertEquals(listOf(provider), pm.disabled)
        assertEquals(listOf(first), ifw.disabled)
        assertEquals(2, results.size)
        assertEquals(2, results.map { it.component.name }.toSet().size)
        assertTrue(results.all { it.succeeded })
        assertTrue(results.first { it.component.type == PROVIDER }.component.pmBlocked)
        assertTrue(results.first { it.component.type == SERVICE }.component.ifwBlocked)
    }

    @Test
    fun givenStaleDatabaseFlags_whenEnablingIfw_thenUsesFreshPmState() = runTest {
        users.sendUserData(defaultUserData)
        pm.states[first.name] = false
        ifw.states[first.name] = false

        val result = repository.batchControlComponent(listOf(first), true).toList().single()

        assertEquals(listOf(first), pm.enabled)
        assertTrue(result.succeeded)
        assertFalse(result.component.pmBlocked)
        assertFalse(result.component.ifwBlocked)
    }

    @Test
    fun givenOneByOneChangeInFlight_whenCollectorIsCancelled_thenItFinishesAndNoFurtherCallIsSent() = runTest {
        users.sendUserData(defaultUserData.copy(controllerType = ControllerType.PM))
        val third = first.copy(name = "com.tracker.Third")
        val secondSent = CompletableDeferred<Unit>()
        val land = CompletableDeferred<Unit>()
        var calls = 0
        pm.applyChange = { change ->
            if (++calls == 2) {
                secondSent.complete(Unit)
                // Like a root command: cancelling the caller stops its wait, not the change.
                backgroundScope.async {
                    land.await()
                    change()
                }.await()
            } else {
                change()
            }
        }
        val job = launch { repository.batchControlComponent(listOf(first, second, third, last), false).collect() }
        secondSent.await()

        job.cancel()
        runCurrent()
        land.complete(Unit)
        job.join()

        assertEquals(listOf(first, second), pm.disabled)
        val saved = argumentCaptor<List<AppComponentEntity>>()
        verify(dao).upsertComponentList(saved.capture())
        assertEquals(
            listOf(first.name to true, second.name to true, third.name to false),
            saved.firstValue.map { it.componentName to it.pmBlocked },
        )
    }

    @Test
    fun givenFailedComponent_whenApplyingBatch_thenSavesStateAndReportsOnlyThatComponentAsFailed() = runTest {
        users.sendUserData(defaultUserData.copy(controllerType = ControllerType.PM))
        pm.failures.add(second.name)

        val results = repository.batchControlComponent(listOf(first, second), false).toList()

        val saved = argumentCaptor<List<AppComponentEntity>>()
        verify(dao).upsertComponentList(saved.capture())
        assertEquals(listOf(true, false), saved.firstValue.map { it.pmBlocked })
        assertEquals(listOf(first.name to true, second.name to false), results.map { it.component.name to it.succeeded })
    }

    @Test
    fun givenOneAppThrows_whenApplyingBatchToSeveralApps_thenReportsItAndContinuesWithTheOthers() = runTest {
        users.sendUserData(defaultUserData.copy(controllerType = ControllerType.PM))
        val broken = ComponentInfo("com.uninstalled", "com.tracker.Broken", SERVICE)
        pm.throwingPackages.add(broken.packageName)

        val results = repository.batchControlComponent(listOf(first, broken, last), false).toList()

        assertEquals(
            listOf(first.name to true, broken.name to false, last.name to true),
            results.map { it.component.name to it.succeeded },
        )
        assertEquals(listOf(first, last), pm.disabled)
        // Every app's state is read back and saved, the failed one included.
        verify(dao, times(3)).upsertComponentList(any())
    }

    @Test
    fun givenRootLostAfterOneChange_whenStoredIfwStateDiffers_thenUpdatesOnlyPmStateOfIndexedRowsAndStops() = runTest {
        users.sendUserData(defaultUserData.copy(controllerType = ControllerType.PM))
        val storedFirst = first.copy(ifwBlocked = true).toAppComponentEntity()
        val storing = StoringComponentDao(listOf(storedFirst))
        val repository = LocalComponentRepository(mock(), storing, users, pm, ifw, shizuku, mock(), dispatcher.testDispatcher)
        pm.rootLostAfterChanges = 1

        // The caller's IFW flag comes from a read that reports unreadable rules as not blocked.
        assertFailsWith<RootUnavailableException> {
            repository.batchControlComponent(listOf(first, second, last), false).toList()
        }

        assertEquals(listOf(first), pm.disabled)
        assertEquals(0, storing.upserts)
        assertEquals(mapOf(storing.key(storedFirst) to storedFirst.copy(pmBlocked = true)), storing.rows)
    }

    @Test
    fun givenRootDenied_whenApplyingBatch_thenSavesOnlyPmStateOfIndexedRowsAndStops() = runTest {
        users.sendUserData(defaultUserData.copy(controllerType = ControllerType.PM))
        val storedFirst = first.copy(ifwBlocked = true).toAppComponentEntity()
        val staleSecond = second.copy(pmBlocked = true).toAppComponentEntity()
        val storing = StoringComponentDao(listOf(storedFirst, staleSecond))
        val repository = LocalComponentRepository(mock(), storing, users, pm, ifw, shizuku, mock(), dispatcher.testDispatcher)
        pm.rootLostAfterChanges = 0

        assertFailsWith<RootUnavailableException> {
            repository.batchControlComponent(listOf(first, second, last), false).toList()
        }

        assertEquals(emptyList(), pm.disabled)
        assertEquals(0, storing.upserts)
        assertEquals(
            mapOf(storing.key(storedFirst) to storedFirst, storing.key(staleSecond) to staleSecond.copy(pmBlocked = false)),
            storing.rows,
        )
    }

    /** Keeps rows by primary key, as Room does for the two writes the batch path uses. */
    private class StoringComponentDao(rows: List<AppComponentEntity>) : AppComponentDao by mock() {
        val rows = rows.associateBy { key(it) }.toMutableMap()
        var upserts = 0

        fun key(row: AppComponentEntity) = row.packageName to row.componentName

        override suspend fun upsertComponentList(componentList: List<AppComponentEntity>) {
            upserts++
            componentList.forEach { rows[key(it)] = it }
        }

        override suspend fun updatePmBlocked(packageName: String, componentName: String, pmBlocked: Boolean): Int {
            val row = rows[packageName to componentName] ?: return 0
            rows[packageName to componentName] = row.copy(pmBlocked = pmBlocked)
            return 1
        }
    }

    private class StatefulController : IController {
        val states = mutableMapOf<String, Boolean>()
        val failures = mutableSetOf<String>()
        val disabled = mutableListOf<ComponentInfo>()
        val enabled = mutableListOf<ComponentInfo>()
        val throwingPackages = mutableSetOf<String>()

        /** Changes that succeed before root is lost; later calls throw RootUnavailableException. */
        var rootLostAfterChanges = Int.MAX_VALUE

        /** Applies a change. A test can make it land later, like a command in the root process. */
        var applyChange: suspend (change: () -> Unit) -> Unit = { it() }
        private var changes = 0
        override suspend fun switchComponent(component: ComponentInfo, state: ComponentState): Boolean = if (state == ComponentState.DISABLED) disable(component) else enable(component)
        override suspend fun enable(component: ComponentInfo): Boolean {
            checkAvailable(component)
            enabled.add(component)
            if (component.name in failures) return false
            applyChange { states[component.name] = true }
            changes++
            return true
        }
        override suspend fun disable(component: ComponentInfo): Boolean {
            checkAvailable(component)
            disabled.add(component)
            if (component.name in failures) return false
            applyChange { states[component.name] = false }
            changes++
            return true
        }
        override suspend fun checkComponentEnableState(packageName: String, componentName: String) = states[componentName] ?: true

        private fun checkAvailable(component: ComponentInfo) {
            if (changes >= rootLostAfterChanges) throw RootUnavailableException()
            check(component.packageName !in throwingPackages) { "Package ${component.packageName} is not installed" }
        }
    }
}
