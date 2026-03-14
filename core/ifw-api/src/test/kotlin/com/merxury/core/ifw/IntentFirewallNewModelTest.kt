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

import com.merxury.blocker.core.model.ComponentType
import com.merxury.blocker.core.testing.controller.FakeComponentTypeResolver
import com.merxury.blocker.core.testing.controller.FakeIfwFileSystem
import com.merxury.blocker.core.testing.controller.FakeRootAvailabilityChecker
import com.merxury.core.ifw.model.IfwComponentType
import com.merxury.core.ifw.model.IfwFilter
import com.merxury.core.ifw.model.IfwRule
import com.merxury.core.ifw.model.IfwRules
import com.merxury.core.ifw.model.SenderType
import com.merxury.core.ifw.model.StringMatcher
import com.merxury.core.ifw.xml.IfwXmlDeserializer
import com.merxury.core.ifw.xml.IfwXmlSerializer
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
class IntentFirewallNewModelTest {

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

    // ── getRules / saveRules ──

    @Test
    fun givenNoFile_whenGetRules_thenReturnsEmpty() = runTest {
        val rules = intentFirewall.getRules(testPackage)
        assertTrue(rules.isEmpty())
    }

    @Test
    fun givenNoRoot_whenGetRules_thenReturnsEmpty() = runTest {
        rootChecker.rootAvailable = false
        val rules = intentFirewall.getRules(testPackage)
        assertTrue(rules.isEmpty())
    }

    @Test
    fun givenSavedRules_whenGetRulesAfterCacheReset_thenRoundTripsCorrectly() = runTest {
        val original = IfwRules(
            listOf(
                IfwRule(
                    componentType = IfwComponentType.BROADCAST,
                    filters = listOf(
                        IfwFilter.And(
                            listOf(
                                IfwFilter.Action(StringMatcher.Equals("android.intent.action.BOOT_COMPLETED")),
                                IfwFilter.Not(IfwFilter.Sender(SenderType.SYSTEM)),
                            ),
                        ),
                    ),
                ),
            ),
        )
        intentFirewall.saveRules(testPackage, original)
        intentFirewall.resetCache()
        val loaded = intentFirewall.getRules(testPackage)
        assertEquals(original, loaded)
    }

    @Test
    fun givenEmptyRules_whenSaveRules_thenDeletesFile() = runTest {
        intentFirewall.saveRules(
            testPackage,
            IfwRules(
                listOf(
                    IfwRule(
                        componentType = IfwComponentType.ACTIVITY,
                        filters = listOf(IfwFilter.ComponentFilter("a/b")),
                    ),
                ),
            ),
        )
        assertTrue(fileSystem.fileExists(testPackage))
        intentFirewall.saveRules(testPackage, IfwRules.empty())
        assertFalse(fileSystem.fileExists(testPackage))
    }

    @Test
    fun givenComponentFilterOnlyXml_whenGetRules_thenParsesCorrectly() = runTest {
        val xml = """
            <rules>
               <activity block="true" log="true">
                  <component-filter name="com.example/com.example.Main" />
               </activity>
               <broadcast block="true" log="true">
                  <component-filter name="com.example/com.example.Receiver" />
               </broadcast>
               <service block="true" log="true">
                  <component-filter name="com.example/com.example.Service" />
               </service>
            </rules>
        """.trimIndent()
        fileSystem.writeRules(testPackage, xml)

        val rules = intentFirewall.getRules(testPackage)
        assertEquals(3, rules.rules.size)
        assertEquals(
            setOf("com.example/com.example.Main"),
            rules.componentFiltersFor(IfwComponentType.ACTIVITY),
        )
    }

    @Test
    fun givenInvalidXml_whenGetRules_thenReturnsEmpty() = runTest {
        fileSystem.writeRules(testPackage, "<not-valid>>>")
        val rules = intentFirewall.getRules(testPackage)
        assertTrue(rules.isEmpty())
    }

    @Test
    fun givenCachedRules_whenResetCache_thenReReadsFromFile() = runTest {
        intentFirewall.saveRules(
            testPackage,
            IfwRules(
                listOf(
                    IfwRule(
                        componentType = IfwComponentType.ACTIVITY,
                        filters = listOf(IfwFilter.ComponentFilter("a/b")),
                    ),
                ),
            ),
        )
        fileSystem.writeRules(testPackage, "<rules></rules>")
        intentFirewall.resetCache()
        val loaded = intentFirewall.getRules(testPackage)
        assertTrue(loaded.isEmpty())
    }

    // ── addComponentFilter / removeComponentFilter ──

    @Test
    fun givenReceiverComponent_whenAddComponentFilter_thenComponentIsBlocked() = runTest {
        val result = intentFirewall.addComponentFilter(testPackage, testReceiver)
        assertTrue(result)
        assertFalse(intentFirewall.getComponentEnableState(testPackage, testReceiver))
    }

    @Test
    fun givenServiceComponent_whenAddComponentFilter_thenComponentIsBlocked() = runTest {
        val result = intentFirewall.addComponentFilter(testPackage, testService)
        assertTrue(result)
        assertFalse(intentFirewall.getComponentEnableState(testPackage, testService))
    }

    @Test
    fun givenActivityComponent_whenAddComponentFilter_thenComponentIsBlocked() = runTest {
        val result = intentFirewall.addComponentFilter(testPackage, testActivity)
        assertTrue(result)
        assertFalse(intentFirewall.getComponentEnableState(testPackage, testActivity))
    }

    @Test
    fun givenProviderComponent_whenAddComponentFilter_thenReturnsFalse() = runTest {
        val result = intentFirewall.addComponentFilter(testPackage, testProvider)
        assertFalse(result)
    }

    @Test
    fun givenBlockedComponent_whenRemoveComponentFilter_thenComponentIsEnabled() = runTest {
        intentFirewall.addComponentFilter(testPackage, testReceiver)
        assertFalse(intentFirewall.getComponentEnableState(testPackage, testReceiver))
        intentFirewall.removeComponentFilter(testPackage, testReceiver)
        assertTrue(intentFirewall.getComponentEnableState(testPackage, testReceiver))
    }

    @Test
    fun givenMultipleComponents_whenAddComponentFilter_thenAllBlocked() = runTest {
        intentFirewall.addComponentFilter(testPackage, testReceiver)
        intentFirewall.addComponentFilter(testPackage, testService)
        intentFirewall.addComponentFilter(testPackage, testActivity)
        assertFalse(intentFirewall.getComponentEnableState(testPackage, testReceiver))
        assertFalse(intentFirewall.getComponentEnableState(testPackage, testService))
        assertFalse(intentFirewall.getComponentEnableState(testPackage, testActivity))
    }

    @Test
    fun givenNoRules_whenGetComponentEnableState_thenReturnsTrue() = runTest {
        assertTrue(intentFirewall.getComponentEnableState(testPackage, testReceiver))
    }
}
