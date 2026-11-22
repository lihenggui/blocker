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
import com.merxury.blocker.core.utils.RootAvailabilityChecker
import com.merxury.core.ifw.model.IfwComponentType
import com.merxury.core.ifw.model.IfwFilter
import com.merxury.core.ifw.model.IfwIntentFilter
import com.merxury.core.ifw.model.IfwRule
import com.merxury.core.ifw.model.IfwRules
import com.merxury.core.ifw.model.SenderType
import com.merxury.core.ifw.model.StringMatcher
import com.merxury.core.ifw.xml.IfwXmlDeserializer
import com.merxury.core.ifw.xml.IfwXmlSerializer
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.IOException
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TestTimeSource
import kotlin.time.TimeSource

private const val INVALID_XML = "<not-valid>>>"

@RunWith(RobolectricTestRunner::class)
class IntentFirewallNewModelTest {

    private val testPackage = "com.example"
    private val testReceiver = "com.example.BootReceiver"
    private val testService = "com.example.DaemonService"
    private val testActivity = "com.example.MainActivity"
    private val testProvider = "com.example.DataProvider"

    private val blockedReceiverRules = componentFilterRules(IfwComponentType.BROADCAST, testReceiver)
    private val blockedReceiverXml = IfwXmlSerializer().serialize(blockedReceiverRules)
    private val blockedServiceXml = IfwXmlSerializer().serialize(componentFilterRules(IfwComponentType.SERVICE, testService))

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
    fun givenNoRoot_whenGetRules_thenThrowsRootUnavailableException() = runTest {
        rootChecker.rootAvailable = false
        assertFailsWith<RootUnavailableException> {
            intentFirewall.getRules(testPackage)
        }
    }

    @Test
    fun givenRootUnavailable_whenRootGranted_thenReadsTheRealFile() = runTest {
        fileSystem.writeRules(testPackage, blockedReceiverXml)
        rootChecker.rootAvailable = false
        assertTrue(intentFirewall.getComponentEnableState(testPackage, testReceiver))
        assertFailsWith<RootUnavailableException> {
            intentFirewall.getRules(testPackage)
        }

        rootChecker.rootAvailable = true

        assertEquals(blockedReceiverRules, intentFirewall.getRules(testPackage))
        assertFalse(intentFirewall.getComponentEnableState(testPackage, testReceiver))
    }

    @Test
    fun givenRootUnavailable_whenCheckingManyComponentsWithinRetryInterval_thenRootIsProbedOnce() = runTest {
        val checker = CountingRootChecker(rootAvailable = false)
        val timeSource = TestTimeSource()
        val firewall = firewallWith(checker, timeSource)

        repeat(20) {
            assertTrue(firewall.getComponentEnableState("com.app$it", "com.app$it.Component"))
            timeSource += 50.milliseconds
        }

        assertEquals(1, checker.probes)
    }

    @Test
    fun givenRootGranted_whenRetryIntervalPasses_thenSamePackageShowsTheRealState() = runTest {
        fileSystem.writeRules(testPackage, blockedReceiverXml)
        val checker = CountingRootChecker(rootAvailable = false)
        val timeSource = TestTimeSource()
        val firewall = firewallWith(checker, timeSource)
        assertTrue(firewall.getComponentEnableState(testPackage, testReceiver))

        checker.rootAvailable = true
        timeSource += 1.seconds
        assertTrue(firewall.getComponentEnableState(testPackage, testReceiver))
        timeSource += 1.seconds

        assertFalse(firewall.getComponentEnableState(testPackage, testReceiver))
        assertEquals(2, checker.probes)
    }

    @Test
    fun givenRootUnavailable_whenCacheReset_thenNextCheckProbesRootAgain() = runTest {
        fileSystem.writeRules(testPackage, blockedReceiverXml)
        val checker = CountingRootChecker(rootAvailable = false)
        val firewall = firewallWith(checker, TestTimeSource())
        assertTrue(firewall.getComponentEnableState(testPackage, testReceiver))

        checker.rootAvailable = true
        firewall.resetCache()

        assertFalse(firewall.getComponentEnableState(testPackage, testReceiver))
        assertEquals(2, checker.probes)
    }

    @Test
    fun givenRootUnavailable_whenRootGrantedAndComponentAdded_thenExistingRulesAreKept() = runTest {
        fileSystem.writeRules(testPackage, blockedReceiverXml)
        rootChecker.rootAvailable = false
        assertTrue(intentFirewall.getComponentEnableState(testPackage, testReceiver))

        rootChecker.rootAvailable = true
        assertTrue(intentFirewall.addComponentFilter(testPackage, testService))

        intentFirewall.resetCache()
        assertFalse(intentFirewall.getComponentEnableState(testPackage, testReceiver))
        assertFalse(intentFirewall.getComponentEnableState(testPackage, testService))
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
    fun givenInvalidXml_whenGetRules_thenThrowsIOException() = runTest {
        fileSystem.writeRules(testPackage, INVALID_XML)
        assertFailsWith<IOException> {
            intentFirewall.getRules(testPackage)
        }
    }

    @Test
    fun givenInvalidXml_whenFileIsFixed_thenNextReadSeesTheFix() = runTest {
        fileSystem.writeRules(testPackage, INVALID_XML)
        assertTrue(intentFirewall.getComponentEnableState(testPackage, testReceiver))

        fileSystem.writeRules(testPackage, blockedReceiverXml)

        assertFalse(intentFirewall.getComponentEnableState(testPackage, testReceiver))
    }

    // ── Unreadable files are never overwritten ──

    @Test
    fun givenTruncatedFile_whenAddComponentFilter_thenReturnsFalseAndKeepsFile() = runTest {
        val truncated = blockedServiceXml.substringBefore("</service>")
        fileSystem.writeRules(testPackage, truncated)

        assertFalse(intentFirewall.addComponentFilter(testPackage, testReceiver))

        assertEquals(truncated, fileSystem.readRules(testPackage))
    }

    @Test
    fun givenUnreadableFile_whenAddComponentFilter_thenReturnsFalseAndKeepsFile() = runTest {
        fileSystem.writeRules(testPackage, blockedServiceXml)
        fileSystem.unreadablePackages += testPackage

        assertFalse(intentFirewall.addComponentFilter(testPackage, testReceiver))

        fileSystem.unreadablePackages.clear()
        assertEquals(blockedServiceXml, fileSystem.readRules(testPackage))
    }

    @Test
    fun givenUnreadableFile_whenRemoveComponentFilter_thenReturnsFalseAndKeepsFile() = runTest {
        fileSystem.writeRules(testPackage, blockedServiceXml)
        fileSystem.unreadablePackages += testPackage

        assertFalse(intentFirewall.removeComponentFilter(testPackage, testService))

        fileSystem.unreadablePackages.clear()
        assertEquals(blockedServiceXml, fileSystem.readRules(testPackage))
    }

    @Test
    fun givenUnreadableFile_whenItBecomesReadable_thenAddKeepsExistingRules() = runTest {
        fileSystem.writeRules(testPackage, blockedServiceXml)
        fileSystem.unreadablePackages += testPackage
        assertFalse(intentFirewall.addComponentFilter(testPackage, testReceiver))

        fileSystem.unreadablePackages.clear()

        assertTrue(intentFirewall.addComponentFilter(testPackage, testReceiver))
        assertFalse(intentFirewall.getComponentEnableState(testPackage, testService))
        assertFalse(intentFirewall.getComponentEnableState(testPackage, testReceiver))
    }

    @Test
    fun givenOneUnreadablePackage_whenAddAllComponentFilters_thenOnlyThatPackageIsSkipped() = runTest {
        val otherPackage = "com.other"
        val otherReceiver = "com.other.Receiver"
        componentTypeResolver.setComponentType(otherPackage, otherReceiver, ComponentType.RECEIVER)
        fileSystem.writeRules(testPackage, blockedServiceXml)
        fileSystem.unreadablePackages += testPackage
        val reported = mutableListOf<ComponentName>()

        intentFirewall.addAllComponentFilters(
            listOf(ComponentName(testPackage, testReceiver), ComponentName(otherPackage, otherReceiver)),
        ) { reported += it }

        assertEquals(listOf(ComponentName(otherPackage, otherReceiver)), reported)
        assertFalse(intentFirewall.getComponentEnableState(otherPackage, otherReceiver))
        fileSystem.unreadablePackages.clear()
        assertEquals(blockedServiceXml, fileSystem.readRules(testPackage))
    }

    @Test
    fun givenUnreadableFile_whenRemoveAllComponentFilters_thenKeepsFileAndReportsNothing() = runTest {
        fileSystem.writeRules(testPackage, blockedServiceXml)
        fileSystem.unreadablePackages += testPackage
        val reported = mutableListOf<ComponentName>()

        intentFirewall.removeAllComponentFilters(listOf(ComponentName(testPackage, testService))) {
            reported += it
        }

        assertTrue(reported.isEmpty())
        fileSystem.unreadablePackages.clear()
        assertEquals(blockedServiceXml, fileSystem.readRules(testPackage))
    }

    @Test
    fun givenOneUnreadablePackage_whenGetAllRules_thenOnlyThatPackageIsOmitted() = runTest {
        fileSystem.writeRules(testPackage, INVALID_XML)
        fileSystem.writeRules("com.other", blockedReceiverXml)

        assertEquals(setOf("com.other"), intentFirewall.getAllRules().keys)
    }

    // ── Concurrency ──

    @Test
    fun givenConcurrentAddsAndRemoves_whenRunOnSeveralThreads_thenNoRuleIsLost() = runTest {
        val firewall = IntentFirewall(
            rootChecker = rootChecker,
            componentTypeResolver = componentTypeResolver,
            fileSystem = YieldingIfwFileSystem(fileSystem),
            serializer = IfwXmlSerializer(),
            deserializer = IfwXmlDeserializer(),
        )
        val receivers = List(100) { "com.example.Receiver$it" }
        receivers.forEach { componentTypeResolver.setComponentType(testPackage, it, ComponentType.RECEIVER) }
        val toRemove = receivers.take(50)
        val toAdd = receivers.drop(50)
        toRemove.forEach { firewall.addComponentFilter(testPackage, it) }

        val results = withContext(Dispatchers.Default) {
            val adds = toAdd.map { async { firewall.addComponentFilter(testPackage, it) } }
            val removes = toRemove.map { async { firewall.removeComponentFilter(testPackage, it) } }
            (adds + removes).awaitAll()
        }

        assertTrue(results.all { it })
        firewall.resetCache()
        assertEquals(
            toAdd.map { "$testPackage/$it" }.toSet(),
            firewall.getRules(testPackage).componentFiltersFor(IfwComponentType.BROADCAST),
        )
    }

    @Test
    fun givenConcurrentBatchAndSingleToggles_whenRunOnSeveralThreads_thenNoRuleIsLost() = runTest {
        val firewall = IntentFirewall(
            rootChecker = rootChecker,
            componentTypeResolver = componentTypeResolver,
            fileSystem = YieldingIfwFileSystem(fileSystem),
            serializer = IfwXmlSerializer(),
            deserializer = IfwXmlDeserializer(),
        )
        val receivers = List(100) { "com.example.Receiver$it" }
        receivers.forEach { componentTypeResolver.setComponentType(testPackage, it, ComponentType.RECEIVER) }
        val (initiallyBlocked, initiallyAllowed) = receivers.chunked(50)
        firewall.addAllComponentFilters(initiallyBlocked.map { ComponentName(testPackage, it) })
        val (batchRemove, singleRemove) = initiallyBlocked.chunked(25)
        val (batchAdd, singleAdd) = initiallyAllowed.chunked(25)
        val reported = ConcurrentLinkedQueue<ComponentName>()

        val singleResults = withContext(Dispatchers.Default) {
            val batches = listOf(
                async { firewall.removeAllComponentFilters(batchRemove.map { ComponentName(testPackage, it) }) { reported += it } },
                async { firewall.addAllComponentFilters(batchAdd.map { ComponentName(testPackage, it) }) { reported += it } },
            )
            val singles = singleAdd.map { async { firewall.addComponentFilter(testPackage, it) } } +
                singleRemove.map { async { firewall.removeComponentFilter(testPackage, it) } }
            batches.awaitAll()
            singles.awaitAll()
        }

        assertTrue(singleResults.all { it })
        assertEquals((batchRemove + batchAdd).map { ComponentName(testPackage, it) }.toSet(), reported.toSet())
        firewall.resetCache()
        assertEquals(
            initiallyAllowed.map { "$testPackage/$it" }.toSet(),
            firewall.getRules(testPackage).componentFiltersFor(IfwComponentType.BROADCAST),
        )
    }

    @Test
    fun givenUnreadablePackage_whenBatchChangesSeveralPackages_thenSkipsItWithoutWritingAndReportsOthers() = runTest {
        val otherPackage = "com.other"
        val otherReceiver = "com.other.Receiver"
        componentTypeResolver.setComponentType(otherPackage, otherReceiver, ComponentType.RECEIVER)
        val writes = mutableListOf<String>()
        val firewall = IntentFirewall(
            rootChecker = rootChecker,
            componentTypeResolver = componentTypeResolver,
            fileSystem = object : IfwFileSystem by fileSystem {
                override suspend fun writeRules(packageName: String, content: String) {
                    writes += packageName
                    fileSystem.writeRules(packageName, content)
                }

                override suspend fun deleteRules(packageName: String): Boolean {
                    writes += packageName
                    return fileSystem.deleteRules(packageName)
                }
            },
            serializer = IfwXmlSerializer(),
            deserializer = IfwXmlDeserializer(),
        )
        fileSystem.writeRules(testPackage, blockedServiceXml)
        fileSystem.unreadablePackages += testPackage
        val components = listOf(
            ComponentName(testPackage, testReceiver),
            ComponentName(testPackage, testService),
            ComponentName(otherPackage, otherReceiver),
        )
        val added = mutableListOf<ComponentName>()
        val removed = mutableListOf<ComponentName>()

        firewall.addAllComponentFilters(components) { added += it }
        firewall.removeAllComponentFilters(components) { removed += it }

        assertEquals(listOf(ComponentName(otherPackage, otherReceiver)), added)
        assertEquals(listOf(ComponentName(otherPackage, otherReceiver)), removed)
        assertEquals(listOf(otherPackage, otherPackage), writes)
        fileSystem.unreadablePackages.clear()
        assertEquals(blockedServiceXml, fileSystem.readRules(testPackage))
    }

    @Test
    fun givenRuleWithIntentFilter_whenRemovingItsLastComponentFilter_thenKeepsTheIntentFilter() = runTest {
        val rule = IfwRule(
            componentType = IfwComponentType.BROADCAST,
            intentFilters = listOf(IfwIntentFilter(actions = listOf("com.example.ACTION"))),
            filters = listOf(IfwFilter.ComponentFilter("$testPackage/$testReceiver")),
        )
        val neverBlocked = "com.example.OtherReceiver"
        componentTypeResolver.setComponentType(testPackage, neverBlocked, ComponentType.RECEIVER)
        intentFirewall.saveRules(testPackage, IfwRules(listOf(rule)))

        assertTrue(intentFirewall.removeComponentFilter(testPackage, testReceiver))
        // A component that was never blocked leaves the remaining intent-only rule alone.
        intentFirewall.removeAllComponentFilters(listOf(ComponentName(testPackage, neverBlocked)))

        intentFirewall.resetCache()
        assertEquals(IfwRules(listOf(rule.copy(filters = emptyList()))), intentFirewall.getRules(testPackage))
    }

    @Test
    fun givenFilterInSecondRuleOfItsType_whenRemoving_thenComponentIsEnabled() = runTest {
        val otherReceiver = "com.example.OtherReceiver"
        componentTypeResolver.setComponentType(testPackage, otherReceiver, ComponentType.RECEIVER)
        val firstRule = IfwRule(
            componentType = IfwComponentType.BROADCAST,
            log = false,
            filters = listOf(IfwFilter.ComponentFilter("$testPackage/$otherReceiver")),
        )
        val secondRule = IfwRule(
            componentType = IfwComponentType.BROADCAST,
            filters = listOf(
                IfwFilter.ComponentFilter("$testPackage/$testReceiver"),
                IfwFilter.ComponentFilter("$testPackage/com.example.ThirdReceiver"),
            ),
        )
        intentFirewall.saveRules(testPackage, IfwRules(listOf(firstRule, secondRule)))

        assertTrue(intentFirewall.removeComponentFilter(testPackage, testReceiver))
        val reported = mutableListOf<ComponentName>()
        intentFirewall.removeAllComponentFilters(listOf(ComponentName(testPackage, otherReceiver))) { reported += it }

        intentFirewall.resetCache()
        assertTrue(intentFirewall.getComponentEnableState(testPackage, testReceiver))
        assertTrue(intentFirewall.getComponentEnableState(testPackage, otherReceiver))
        assertEquals(listOf(ComponentName(testPackage, otherReceiver)), reported)
        assertEquals(
            IfwRules(listOf(secondRule.copy(filters = listOf(IfwFilter.ComponentFilter("$testPackage/com.example.ThirdReceiver"))))),
            intentFirewall.getRules(testPackage),
        )
    }

    @Test
    fun givenFilterInTwoRulesOfItsType_whenRemoving_thenRemovesItFromBothAndKeepsTheRest() = runTest {
        val target = IfwFilter.ComponentFilter("$testPackage/$testReceiver")
        val unrelated = IfwFilter.ComponentFilter("$testPackage/com.example.OtherReceiver")
        val condition = IfwFilter.Not(IfwFilter.Sender(SenderType.SYSTEM))
        val withUnrelatedFilter = IfwRule(
            componentType = IfwComponentType.BROADCAST,
            block = true,
            log = false,
            filters = listOf(unrelated, target),
        )
        val withConditionAndIntent = IfwRule(
            componentType = IfwComponentType.BROADCAST,
            block = false,
            log = true,
            intentFilters = listOf(IfwIntentFilter(actions = listOf("com.example.ACTION"))),
            filters = listOf(target, condition),
        )
        val otherType = IfwRule(componentType = IfwComponentType.SERVICE, filters = listOf(IfwFilter.ComponentFilter("$testPackage/$testService")))
        intentFirewall.saveRules(testPackage, IfwRules(listOf(withUnrelatedFilter, withConditionAndIntent, otherType)))

        assertTrue(intentFirewall.removeComponentFilter(testPackage, testReceiver))

        intentFirewall.resetCache()
        assertTrue(intentFirewall.getComponentEnableState(testPackage, testReceiver))
        assertEquals(
            IfwRules(
                listOf(
                    withUnrelatedFilter.copy(filters = listOf(unrelated)),
                    withConditionAndIntent.copy(filters = listOf(condition)),
                    otherType,
                ),
            ),
            intentFirewall.getRules(testPackage),
        )
    }

    @Test
    fun givenCacheResetDuringRead_whenReadFinishes_thenStaleRulesAreNotCached() = runTest {
        fileSystem.writeRules(testPackage, blockedReceiverXml)
        val readStarted = CompletableDeferred<Unit>()
        val releaseRead = CompletableDeferred<Unit>()
        val gatedFileSystem = object : IfwFileSystem by fileSystem {
            override suspend fun readRules(packageName: String): String? {
                val content = fileSystem.readRules(packageName)
                readStarted.complete(Unit)
                releaseRead.await()
                return content
            }
        }
        val firewall = IntentFirewall(
            rootChecker = rootChecker,
            componentTypeResolver = componentTypeResolver,
            fileSystem = gatedFileSystem,
            serializer = IfwXmlSerializer(),
            deserializer = IfwXmlDeserializer(),
        )
        val staleRead = async { firewall.getComponentEnableState(testPackage, testReceiver) }
        readStarted.await()

        // Another component deletes the file directly and resets the cache, like a firewall reset.
        fileSystem.deleteRules(testPackage)
        firewall.resetCache()
        releaseRead.complete(Unit)

        assertFalse(staleRead.await())
        assertTrue(firewall.getComponentEnableState(testPackage, testReceiver))
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

    private fun componentFilterRules(type: IfwComponentType, componentName: String) = IfwRules(
        listOf(IfwRule(componentType = type, filters = listOf(IfwFilter.ComponentFilter("$testPackage/$componentName")))),
    )

    private fun firewallWith(rootChecker: RootAvailabilityChecker, timeSource: TimeSource) = IntentFirewall(
        rootChecker = rootChecker,
        componentTypeResolver = componentTypeResolver,
        fileSystem = fileSystem,
        serializer = IfwXmlSerializer(),
        deserializer = IfwXmlDeserializer(),
        timeSource = timeSource,
    )
}

private class CountingRootChecker(var rootAvailable: Boolean) : RootAvailabilityChecker {
    var probes = 0

    override suspend fun isRootAvailable(): Boolean {
        probes++
        return rootAvailable
    }
}

/** Suspends around each file access so concurrent read-modify-write sequences interleave. */
private class YieldingIfwFileSystem(private val delegate: IfwFileSystem) : IfwFileSystem by delegate {
    override suspend fun readRules(packageName: String): String? = delegate.readRules(packageName).also { yield() }

    override suspend fun writeRules(packageName: String, content: String) {
        yield()
        delegate.writeRules(packageName, content)
    }
}
