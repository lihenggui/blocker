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
import com.merxury.blocker.core.model.ComponentType
import com.merxury.blocker.core.testing.controller.FakeComponentTypeResolver
import com.merxury.blocker.core.testing.controller.FakeIfwFileSystem
import com.merxury.blocker.core.testing.controller.FakeRootAvailabilityChecker
import com.merxury.core.ifw.xml.IfwXmlDeserializer
import com.merxury.core.ifw.xml.IfwXmlSerializer
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.IOException
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

@RunWith(RobolectricTestRunner::class)
class IfwBatchCommitRegressionTest {
    @Test
    fun failedWrite_doesNotReportComponentsAsApplied() = runTest {
        val storage = FakeIfwFileSystem()
        val fileSystem = object : IfwFileSystem by storage {
            override suspend fun writeRules(packageName: String, content: String): Unit = throw IOException("write failed")
        }
        val resolver = FakeComponentTypeResolver()
        resolver.setComponentType("com.example", "com.example.Receiver", ComponentType.RECEIVER)
        val firewall = IntentFirewall(
            FakeRootAvailabilityChecker(rootAvailable = true),
            resolver,
            fileSystem,
            IfwXmlSerializer(),
            IfwXmlDeserializer(),
        )
        val processed = mutableListOf<ComponentName>()

        assertFailsWith<IOException> {
            firewall.addAllComponentFilters(
                listOf(ComponentName("com.example", "com.example.Receiver")),
            ) { processed.add(it) }
        }

        assertEquals(emptyList(), processed)
    }
}
