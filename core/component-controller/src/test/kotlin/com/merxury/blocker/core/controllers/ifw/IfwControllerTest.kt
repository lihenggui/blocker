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

package com.merxury.blocker.core.controllers.ifw

import android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_DISABLED
import android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_ENABLED
import com.merxury.blocker.core.model.ComponentType
import com.merxury.blocker.core.model.data.ComponentInfo
import com.merxury.blocker.core.testing.controller.FakeIntentFirewall
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
class IfwControllerTest {

    private lateinit var fakeIfw: FakeIntentFirewall
    private lateinit var controller: IfwController

    private val testPackage = "com.example.test"

    @Before
    fun setUp() {
        fakeIfw = FakeIntentFirewall()
        controller = IfwController(fakeIfw)
    }

    @Test
    fun disable_callsIntentFirewallAdd() = runTest {
        val component = componentInfo(".MyReceiver", ComponentType.RECEIVER)

        val result = controller.disable(component)

        assertTrue(result)
        assertFalse(fakeIfw.getComponentEnableState(testPackage, ".MyReceiver"))
    }

    @Test
    fun enable_callsIntentFirewallRemove() = runTest {
        val component = componentInfo(".MyReceiver", ComponentType.RECEIVER)
        controller.disable(component)
        assertFalse(fakeIfw.getComponentEnableState(testPackage, ".MyReceiver"))

        val result = controller.enable(component)

        assertTrue(result)
        assertTrue(fakeIfw.getComponentEnableState(testPackage, ".MyReceiver"))
    }

    @Test
    fun switchComponent_disabled_callsAdd() = runTest {
        val component = componentInfo(".MyService", ComponentType.SERVICE)

        val result = controller.switchComponent(component, COMPONENT_ENABLED_STATE_DISABLED)

        assertTrue(result)
        assertFalse(fakeIfw.getComponentEnableState(testPackage, ".MyService"))
    }

    @Test
    fun switchComponent_enabled_callsRemove() = runTest {
        val component = componentInfo(".MyService", ComponentType.SERVICE)
        controller.disable(component)
        assertFalse(fakeIfw.getComponentEnableState(testPackage, ".MyService"))

        val result = controller.switchComponent(component, COMPONENT_ENABLED_STATE_ENABLED)

        assertTrue(result)
        assertTrue(fakeIfw.getComponentEnableState(testPackage, ".MyService"))
    }

    @Test
    fun switchComponent_unknownState_returnsFalse() = runTest {
        val component = componentInfo(".MyActivity", ComponentType.ACTIVITY)

        val result = controller.switchComponent(component, 999)

        assertFalse(result)
    }

    @Test
    fun batchDisable_returnsCount() = runTest {
        val components = listOf(
            componentInfo(".Receiver1", ComponentType.RECEIVER),
            componentInfo(".Receiver2", ComponentType.RECEIVER),
            componentInfo(".Receiver3", ComponentType.RECEIVER),
        )
        val callbackComponents = mutableListOf<ComponentInfo>()

        val count = controller.batchDisable(components) { callbackComponents.add(it) }

        assertEquals(3, count)
        assertEquals(3, callbackComponents.size)
        components.forEach { comp ->
            assertFalse(fakeIfw.getComponentEnableState(testPackage, comp.name))
        }
    }

    @Test
    fun batchDisable_emptyList_returnsZero() = runTest {
        val count = controller.batchDisable(emptyList()) { }

        assertEquals(0, count)
    }

    @Test
    fun batchEnable_afterBatchDisable_unblocks() = runTest {
        val components = listOf(
            componentInfo(".Service1", ComponentType.SERVICE),
            componentInfo(".Service2", ComponentType.SERVICE),
        )
        controller.batchDisable(components) { }
        components.forEach { comp ->
            assertFalse(fakeIfw.getComponentEnableState(testPackage, comp.name))
        }

        val callbackComponents = mutableListOf<ComponentInfo>()
        val count = controller.batchEnable(components) { callbackComponents.add(it) }

        assertEquals(2, count)
        assertEquals(2, callbackComponents.size)
        components.forEach { comp ->
            assertTrue(fakeIfw.getComponentEnableState(testPackage, comp.name))
        }
    }

    @Test
    fun batchEnable_emptyList_returnsZero() = runTest {
        val count = controller.batchEnable(emptyList()) { }

        assertEquals(0, count)
    }

    @Test
    fun checkComponentEnableState_delegatesToIntentFirewall() = runTest {
        assertTrue(controller.checkComponentEnableState(testPackage, ".MyReceiver"))

        controller.disable(componentInfo(".MyReceiver", ComponentType.RECEIVER))

        assertFalse(controller.checkComponentEnableState(testPackage, ".MyReceiver"))
    }

    private fun componentInfo(name: String, type: ComponentType) = ComponentInfo(
        packageName = testPackage,
        name = name,
        type = type,
    )
}
