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

package com.merxury.core.ifw

import android.content.ComponentName
import com.merxury.blocker.core.exception.RootUnavailableException
import com.merxury.blocker.core.model.ComponentType
import com.merxury.blocker.core.testing.controller.FakeComponentTypeResolver
import com.merxury.blocker.core.testing.controller.FakeIfwFileSystem
import com.merxury.blocker.core.testing.controller.FakeRootAvailabilityChecker
import com.merxury.core.ifw.xml.IfwXmlDeserializer
import com.merxury.core.ifw.xml.IfwXmlSerializer
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
class IntentFirewallTest {

    private val testPackage = "com.example"
    private val testReceiver = "com.example.BootReceiver"
    private val testService = "com.example.DaemonService"
    private val testActivity = "com.example.MainActivity"
    private val testProvider = "com.example.DataProvider"

    private lateinit var rootChecker: FakeRootAvailabilityChecker
    private lateinit var componentTypeResolver: FakeComponentTypeResolver
    private lateinit var fileSystem: FakeIfwFileSystem
    private lateinit var intentFirewall: IntentFirewall

    @Before
    fun setUp() {
        rootChecker = FakeRootAvailabilityChecker(rootAvailable = true)
        componentTypeResolver = FakeComponentTypeResolver()
        fileSystem = FakeIfwFileSystem()
        intentFirewall = IntentFirewall(
            rootChecker = rootChecker,
            componentTypeResolver = componentTypeResolver,
            fileSystem = fileSystem,
            serializer = IfwXmlSerializer(),
            deserializer = IfwXmlDeserializer(),
        )

        componentTypeResolver.setComponentType(testPackage, testReceiver, ComponentType.RECEIVER)
        componentTypeResolver.setComponentType(testPackage, testService, ComponentType.SERVICE)
        componentTypeResolver.setComponentType(testPackage, testActivity, ComponentType.ACTIVITY)
        componentTypeResolver.setComponentType(testPackage, testProvider, ComponentType.PROVIDER)
    }

    @Test
    fun givenReceiverComponent_whenAddRule_thenComponentIsBlocked() = runTest {
        val result = intentFirewall.addComponentFilter(testPackage, testReceiver)
        assertTrue(result)
        assertFalse(intentFirewall.getComponentEnableState(testPackage, testReceiver))
    }

    @Test
    fun givenServiceComponent_whenAddRule_thenComponentIsBlocked() = runTest {
        val result = intentFirewall.addComponentFilter(testPackage, testService)
        assertTrue(result)
        assertFalse(intentFirewall.getComponentEnableState(testPackage, testService))
    }

    @Test
    fun givenActivityComponent_whenAddRule_thenComponentIsBlocked() = runTest {
        val result = intentFirewall.addComponentFilter(testPackage, testActivity)
        assertTrue(result)
        assertFalse(intentFirewall.getComponentEnableState(testPackage, testActivity))
    }

    @Test
    fun givenProviderComponent_whenAddRule_thenReturnsFalse() = runTest {
        val result = intentFirewall.addComponentFilter(testPackage, testProvider)
        assertFalse(result)
    }

    @Test
    fun givenNoRootAccess_whenAddRule_thenThrowsRootUnavailableException() = runTest {
        rootChecker.rootAvailable = false
        assertFailsWith<RootUnavailableException> {
            intentFirewall.addComponentFilter(testPackage, testReceiver)
        }
    }

    @Test
    fun givenBlockedComponent_whenRemoveRule_thenComponentIsEnabled() = runTest {
        intentFirewall.addComponentFilter(testPackage, testReceiver)
        assertFalse(intentFirewall.getComponentEnableState(testPackage, testReceiver))

        intentFirewall.removeComponentFilter(testPackage, testReceiver)
        assertTrue(intentFirewall.getComponentEnableState(testPackage, testReceiver))
    }

    @Test
    fun givenNoRootAccess_whenRemoveRule_thenThrowsRootUnavailableException() = runTest {
        rootChecker.rootAvailable = false
        assertFailsWith<RootUnavailableException> {
            intentFirewall.removeComponentFilter(testPackage, testReceiver)
        }
    }

    @Test
    fun givenNoRules_whenGetComponentEnableState_thenReturnsTrue() = runTest {
        assertTrue(intentFirewall.getComponentEnableState(testPackage, testReceiver))
    }

    @Test
    fun givenMultipleComponents_whenAddAll_thenAllComponentsAreBlocked() = runTest {
        val components = listOf(
            ComponentName(testPackage, testReceiver),
            ComponentName(testPackage, testService),
            ComponentName(testPackage, testActivity),
        )
        val callbackComponents = mutableListOf<ComponentName>()
        intentFirewall.addAllComponentFilters(components) { callbackComponents.add(it) }

        assertEquals(3, callbackComponents.size)
        assertFalse(intentFirewall.getComponentEnableState(testPackage, testReceiver))
        assertFalse(intentFirewall.getComponentEnableState(testPackage, testService))
        assertFalse(intentFirewall.getComponentEnableState(testPackage, testActivity))
    }

    @Test
    fun givenProviderInList_whenAddAll_thenProviderIsSkipped() = runTest {
        val components = listOf(
            ComponentName(testPackage, testReceiver),
            ComponentName(testPackage, testProvider),
        )
        val callbackComponents = mutableListOf<ComponentName>()
        intentFirewall.addAllComponentFilters(components) { callbackComponents.add(it) }

        assertEquals(1, callbackComponents.size)
        assertEquals(ComponentName(testPackage, testReceiver), callbackComponents.first())
        assertFalse(intentFirewall.getComponentEnableState(testPackage, testReceiver))
        assertTrue(intentFirewall.getComponentEnableState(testPackage, testProvider))
    }

    @Test
    fun givenNoRootAccess_whenAddAll_thenThrowsRootUnavailableException() = runTest {
        rootChecker.rootAvailable = false
        val components = listOf(ComponentName(testPackage, testReceiver))
        assertFailsWith<RootUnavailableException> {
            intentFirewall.addAllComponentFilters(components)
        }
    }

    @Test
    fun givenBlockedComponents_whenRemoveAll_thenAllComponentsAreEnabled() = runTest {
        val components = listOf(
            ComponentName(testPackage, testReceiver),
            ComponentName(testPackage, testService),
        )
        intentFirewall.addAllComponentFilters(components)

        assertFalse(intentFirewall.getComponentEnableState(testPackage, testReceiver))
        assertFalse(intentFirewall.getComponentEnableState(testPackage, testService))

        val callbackComponents = mutableListOf<ComponentName>()
        intentFirewall.removeAllComponentFilters(components) { callbackComponents.add(it) }

        assertEquals(2, callbackComponents.size)
        assertTrue(intentFirewall.getComponentEnableState(testPackage, testReceiver))
        assertTrue(intentFirewall.getComponentEnableState(testPackage, testService))
    }

    @Test
    fun givenBlockedComponents_whenClear_thenAllRulesAreRemoved() = runTest {
        intentFirewall.addComponentFilter(testPackage, testReceiver)
        intentFirewall.addComponentFilter(testPackage, testService)
        assertFalse(intentFirewall.getComponentEnableState(testPackage, testReceiver))
        assertFalse(intentFirewall.getComponentEnableState(testPackage, testService))

        intentFirewall.clear(testPackage)

        assertTrue(intentFirewall.getComponentEnableState(testPackage, testReceiver))
        assertTrue(intentFirewall.getComponentEnableState(testPackage, testService))
    }

    @Test
    fun givenNoRootAccess_whenClear_thenThrowsRootUnavailableException() = runTest {
        rootChecker.rootAvailable = false
        assertFailsWith<RootUnavailableException> {
            intentFirewall.clear(testPackage)
        }
    }

    @Test
    fun givenNoRootAccess_whenLoad_thenReturnsEmptyRule() = runTest {
        rootChecker.rootAvailable = false
        assertTrue(intentFirewall.getComponentEnableState(testPackage, testReceiver))
    }
}
