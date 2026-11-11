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

package com.merxury.core.ifw

import android.content.ComponentName
import android.content.pm.ActivityInfo
import android.content.pm.PackageInfo
import android.content.pm.ServiceInfo
import com.merxury.blocker.core.model.ComponentType
import com.merxury.blocker.core.testing.controller.FakeIfwFileSystem
import com.merxury.blocker.core.testing.controller.FakePackageInfoDataSource
import com.merxury.blocker.core.testing.controller.FakeRootAvailabilityChecker
import com.merxury.blocker.core.utils.PackageInfoDataSource
import com.merxury.core.ifw.model.IfwComponentType
import com.merxury.core.ifw.model.IfwFilter
import com.merxury.core.ifw.model.IfwIntentFilter
import com.merxury.core.ifw.model.IfwRule
import com.merxury.core.ifw.model.IfwRules
import com.merxury.core.ifw.xml.IfwXmlDeserializer
import com.merxury.core.ifw.xml.IfwXmlSerializer
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class IfwBatchPerformanceTest {
    @Test
    fun givenIntentSelectorsAndRuleFlags_whenRemovingComponentFilters_thenPreservesIndependentRules() = runTest {
        val storage = FakeIfwFileSystem()
        val component = ComponentName("com.example", "com.example.Receiver")
        val resolver = object : ComponentTypeResolver {
            override suspend fun getComponentType(packageName: String, componentName: String) = ComponentType.RECEIVER
        }
        val firewall = IntentFirewall(FakeRootAvailabilityChecker(true), resolver, storage, IfwXmlSerializer(), IfwXmlDeserializer())
        val rule = IfwRule(
            componentType = IfwComponentType.BROADCAST,
            block = true,
            log = false,
            intentFilters = listOf(IfwIntentFilter(actions = listOf("com.example.ACTION"))),
            filters = listOf(IfwFilter.ComponentFilter(component.flattenToString())),
        )
        firewall.saveRules("com.example", IfwRules(listOf(rule)))

        firewall.removeAllComponentFilters(listOf(component))

        assertEquals(IfwRules(listOf(rule.copy(filters = emptyList()))), firewall.getRules("com.example"))
    }

    @Test
    fun givenHundredComponents_whenApplyingIfwBatch_thenResolvesAndWritesOnceBeforeReportingSuccess() = runTest {
        val storage = FakeIfwFileSystem()
        var writes = 0
        var resolutions = 0
        val fileSystem = object : IfwFileSystem by storage {
            override suspend fun writeRules(packageName: String, content: String) {
                writes++
                storage.writeRules(packageName, content)
            }
        }
        val resolver = object : ComponentTypeResolver {
            override suspend fun getComponentType(packageName: String, componentName: String): Nothing = error("Use one package snapshot")
            override suspend fun getComponentTypes(packageName: String, componentNames: List<String>): Map<String, ComponentType> {
                resolutions++
                return componentNames.associateWith { ComponentType.RECEIVER }
            }
        }
        val firewall = IntentFirewall(FakeRootAvailabilityChecker(true), resolver, fileSystem, IfwXmlSerializer(), IfwXmlDeserializer())
        val components = List(100) { ComponentName("com.example", "com.example.Receiver$it") }
        var callbacks = 0

        firewall.addAllComponentFilters(components) {
            assertTrue(storage.fileExists(it.packageName))
            callbacks++
        }

        assertEquals(100, callbacks)
        assertEquals(1, resolutions)
        assertEquals(1, writes)
        assertEquals(100, firewall.getRules("com.example").componentFiltersFor(com.merxury.core.ifw.model.IfwComponentType.BROADCAST).size)

        firewall.addAllComponentFilters(components)
        assertEquals(1, writes, "Already-applied filters must not rewrite the same file")
    }

    @Test
    fun givenAllComponentTypes_whenResolvingTypes_thenReadsPackageInfoOnce() = runTest {
        var packageReads = 0
        val source = object : PackageInfoDataSource by FakePackageInfoDataSource() {
            override suspend fun getApplicationComponents(packageName: String): PackageInfo {
                packageReads++
                return PackageInfo().apply {
                    receivers = arrayOf(ActivityInfo().apply { name = "com.example.Receiver" })
                    services = arrayOf(ServiceInfo().apply { name = "com.example.Service" })
                }
            }
        }
        val names = listOf("com.example.Receiver", "com.example.Service")

        val result = PmComponentTypeResolver(source, kotlinx.coroutines.Dispatchers.Unconfined).getComponentTypes("com.example", names)

        assertEquals(1, packageReads)
        assertEquals(mapOf(names[0] to ComponentType.RECEIVER, names[1] to ComponentType.SERVICE), result)
    }
}
