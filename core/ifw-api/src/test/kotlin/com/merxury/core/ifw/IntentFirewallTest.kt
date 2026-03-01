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
import kotlinx.coroutines.test.runTest
import nl.adaptivity.xmlutil.serialization.XML
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
    private lateinit var xmlParser: XML
    private lateinit var intentFirewall: IntentFirewall

    @Before
    fun setUp() {
        rootChecker = FakeRootAvailabilityChecker(rootAvailable = true)
        componentTypeResolver = FakeComponentTypeResolver()
        fileSystem = FakeIfwFileSystem()
        xmlParser = XML { indentString = "   " }
        intentFirewall = IntentFirewall(
            xmlParser = xmlParser,
            rootChecker = rootChecker,
            componentTypeResolver = componentTypeResolver,
            fileSystem = fileSystem,
        )

        componentTypeResolver.setComponentType(testPackage, testReceiver, ComponentType.RECEIVER)
        componentTypeResolver.setComponentType(testPackage, testService, ComponentType.SERVICE)
        componentTypeResolver.setComponentType(testPackage, testActivity, ComponentType.ACTIVITY)
        componentTypeResolver.setComponentType(testPackage, testProvider, ComponentType.PROVIDER)
    }

    @Test
    fun givenReceiverComponent_whenAddRule_thenComponentIsBlocked() = runTest {
        val result = intentFirewall.add(testPackage, testReceiver)
        assertTrue(result)
        assertFalse(intentFirewall.getComponentEnableState(testPackage, testReceiver))
    }

    @Test
    fun givenServiceComponent_whenAddRule_thenComponentIsBlocked() = runTest {
        val result = intentFirewall.add(testPackage, testService)
        assertTrue(result)
        assertFalse(intentFirewall.getComponentEnableState(testPackage, testService))
    }

    @Test
    fun givenActivityComponent_whenAddRule_thenComponentIsBlocked() = runTest {
        val result = intentFirewall.add(testPackage, testActivity)
        assertTrue(result)
        assertFalse(intentFirewall.getComponentEnableState(testPackage, testActivity))
    }

    @Test
    fun givenProviderComponent_whenAddRule_thenReturnsFalse() = runTest {
        val result = intentFirewall.add(testPackage, testProvider)
        assertFalse(result)
    }

    @Test
    fun givenNoRootAccess_whenAddRule_thenThrowsRootUnavailableException() = runTest {
        rootChecker.rootAvailable = false
        assertFailsWith<RootUnavailableException> {
            intentFirewall.add(testPackage, testReceiver)
        }
    }

    @Test
    fun givenBlockedComponent_whenRemoveRule_thenComponentIsEnabled() = runTest {
        intentFirewall.add(testPackage, testReceiver)
        assertFalse(intentFirewall.getComponentEnableState(testPackage, testReceiver))

        intentFirewall.remove(testPackage, testReceiver)
        assertTrue(intentFirewall.getComponentEnableState(testPackage, testReceiver))
    }

    @Test
    fun givenNoRootAccess_whenRemoveRule_thenThrowsRootUnavailableException() = runTest {
        rootChecker.rootAvailable = false
        assertFailsWith<RootUnavailableException> {
            intentFirewall.remove(testPackage, testReceiver)
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
        intentFirewall.addAll(components) { callbackComponents.add(it) }

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
        intentFirewall.addAll(components) { callbackComponents.add(it) }

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
            intentFirewall.addAll(components)
        }
    }

    @Test
    fun givenBlockedComponents_whenRemoveAll_thenAllComponentsAreEnabled() = runTest {
        val components = listOf(
            ComponentName(testPackage, testReceiver),
            ComponentName(testPackage, testService),
        )
        intentFirewall.addAll(components)

        assertFalse(intentFirewall.getComponentEnableState(testPackage, testReceiver))
        assertFalse(intentFirewall.getComponentEnableState(testPackage, testService))

        val callbackComponents = mutableListOf<ComponentName>()
        intentFirewall.removeAll(components) { callbackComponents.add(it) }

        assertEquals(2, callbackComponents.size)
        assertTrue(intentFirewall.getComponentEnableState(testPackage, testReceiver))
        assertTrue(intentFirewall.getComponentEnableState(testPackage, testService))
    }

    @Test
    fun givenBlockedComponents_whenClear_thenAllRulesAreRemoved() = runTest {
        intentFirewall.add(testPackage, testReceiver)
        intentFirewall.add(testPackage, testService)
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
    fun givenEmptyRule_whenSave_thenFileIsDeleted() = runTest {
        // First write a rule so a file exists
        intentFirewall.add(testPackage, testActivity)
        assertTrue(fileSystem.fileExists(testPackage))

        // Now save an empty rule, which should delete the file
        intentFirewall.save(testPackage, Rules())
        assertFalse(fileSystem.fileExists(testPackage))
    }

    @Test
    fun givenNonEmptyRule_whenSave_thenXmlIsWrittenToFileSystem() = runTest {
        val rule = Rules(
            activity = Component.Activity(
                componentFilter = mutableSetOf(
                    ComponentFilter("$testPackage/$testActivity"),
                ),
            ),
        )
        intentFirewall.save(testPackage, rule)
        assertTrue(fileSystem.fileExists(testPackage))
    }

    @Test
    fun givenCachedRules_whenResetCache_thenRulesAreReloadedFromFileSystem() = runTest {
        // Add a rule (this caches the rule internally)
        intentFirewall.add(testPackage, testReceiver)
        assertFalse(intentFirewall.getComponentEnableState(testPackage, testReceiver))

        // Reset cache so next call must re-read from fileSystem
        intentFirewall.resetCache()

        // The rule was written to fileSystem by save(), so reloading should find it
        assertFalse(intentFirewall.getComponentEnableState(testPackage, testReceiver))
    }

    @Test
    fun givenInvalidXmlInFileSystem_whenLoad_thenReturnsEmptyRule() = runTest {
        // Put invalid XML content into the file system
        fileSystem.writeRules(testPackage, "<not-valid-xml>>>")

        // Should return enabled (empty rule fallback)
        assertTrue(intentFirewall.getComponentEnableState(testPackage, testReceiver))
    }

    @Test
    fun givenNoRootAccess_whenLoad_thenReturnsEmptyRule() = runTest {
        rootChecker.rootAvailable = false
        // Without root, load returns empty rule, so component appears enabled
        assertTrue(intentFirewall.getComponentEnableState(testPackage, testReceiver))
    }
}
