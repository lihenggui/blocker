/*
 * Copyright 2025 Blocker
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

package com.merxury.blocker.core.controllers.combined

import com.merxury.blocker.core.controllers.IController
import com.merxury.blocker.core.model.ComponentState
import com.merxury.blocker.core.model.ComponentType
import com.merxury.blocker.core.model.data.ComponentInfo
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CombinedControllerTest {

    private lateinit var ifwController: FakeIController
    private lateinit var pmController: FakeIController
    private lateinit var combinedController: CombinedController

    private val testComponent = ComponentInfo(
        packageName = "com.example.test",
        name = ".MyReceiver",
        type = ComponentType.RECEIVER,
    )

    @Before
    fun setUp() {
        ifwController = FakeIController()
        pmController = FakeIController()
        combinedController = CombinedController(ifwController, pmController)
    }

    @Test
    fun enable_bothSucceed_returnsTrue() = runTest {
        val result = combinedController.enable(testComponent)
        assertTrue(result)
    }

    @Test
    fun enable_ifwFails_returnsFalse() = runTest {
        ifwController.shouldFail = true
        val result = combinedController.enable(testComponent)
        assertFalse(result)
    }

    @Test
    fun enable_pmFails_returnsFalse() = runTest {
        pmController.shouldFail = true
        val result = combinedController.enable(testComponent)
        assertFalse(result)
    }

    @Test
    fun disable_bothSucceed_returnsTrue() = runTest {
        val result = combinedController.disable(testComponent)
        assertTrue(result)
    }

    @Test
    fun disable_ifwFails_returnsFalse() = runTest {
        ifwController.shouldFail = true
        val result = combinedController.disable(testComponent)
        assertFalse(result)
    }

    @Test
    fun switchComponent_delegatesToBothControllers() = runTest {
        val result = combinedController.switchComponent(testComponent, ComponentState.DISABLED)
        assertTrue(result)
        assertEquals(1, ifwController.switchComponentCalls.size)
        assertEquals(1, pmController.switchComponentCalls.size)
    }

    @Test
    fun batchDisable_allSucceed_returnsCountAndCallsAction() = runTest {
        val components = listOf(
            testComponent,
            testComponent.copy(name = ".Service1"),
            testComponent.copy(name = ".Activity1"),
        )
        val callbackComponents = mutableListOf<ComponentInfo>()

        val count = combinedController.batchDisable(components) { callbackComponents.add(it) }

        assertEquals(3, count)
        assertEquals(3, callbackComponents.size)
    }

    @Test
    fun batchDisable_partialFailure_returnsSuccessCount() = runTest {
        val failComponent = testComponent.copy(name = ".FailComponent")
        ifwController.failForComponents.add(failComponent.name)

        val components = listOf(testComponent, failComponent)
        val callbackComponents = mutableListOf<ComponentInfo>()

        val count = combinedController.batchDisable(components) { callbackComponents.add(it) }

        assertEquals(1, count)
        assertEquals(1, callbackComponents.size)
        assertEquals(testComponent, callbackComponents.first())
    }

    @Test
    fun batchEnable_emptyList_returnsZero() = runTest {
        val count = combinedController.batchEnable(emptyList()) { }
        assertEquals(0, count)
    }

    @Test
    fun batchDisable_emptyList_returnsZero() = runTest {
        val count = combinedController.batchDisable(emptyList()) { }
        assertEquals(0, count)
    }

    @Test
    fun checkComponentEnableState_ifwEnabledPmDisabled_returnsTrue() = runTest {
        ifwController.enabledComponents.add("com.example.test/.MyReceiver")
        pmController.enabledComponents.clear()

        assertTrue(combinedController.checkComponentEnableState("com.example.test", ".MyReceiver"))
    }

    @Test
    fun checkComponentEnableState_bothDisabled_returnsFalse() = runTest {
        ifwController.enabledComponents.clear()
        pmController.enabledComponents.clear()

        assertFalse(combinedController.checkComponentEnableState("com.example.test", ".MyReceiver"))
    }

    @Test
    fun checkComponentEnableState_bothEnabled_returnsTrue() = runTest {
        ifwController.enabledComponents.add("com.example.test/.MyReceiver")
        pmController.enabledComponents.add("com.example.test/.MyReceiver")

        assertTrue(combinedController.checkComponentEnableState("com.example.test", ".MyReceiver"))
    }

    @Test
    fun init_onlyInitializesPmController() = runTest {
        combinedController.init()
        assertTrue(pmController.initCalled)
        assertFalse(ifwController.initCalled)
    }
}

/**
 * Minimal fake for testing CombinedController logic without external dependencies.
 */
private class FakeIController : IController {
    var shouldFail = false
    var initCalled = false
    val switchComponentCalls = mutableListOf<Pair<ComponentInfo, ComponentState>>()
    val failForComponents = mutableSetOf<String>()
    val enabledComponents = mutableSetOf<String>()

    override suspend fun init() {
        initCalled = true
    }

    override suspend fun switchComponent(component: ComponentInfo, state: ComponentState): Boolean {
        switchComponentCalls.add(component to state)
        return !shouldFail && component.name !in failForComponents
    }

    override suspend fun enable(component: ComponentInfo): Boolean = switchComponent(component, ComponentState.ENABLED)

    override suspend fun disable(component: ComponentInfo): Boolean = switchComponent(component, ComponentState.DISABLED)

    override suspend fun checkComponentEnableState(
        packageName: String,
        componentName: String,
    ): Boolean = "$packageName/$componentName" in enabledComponents
}
