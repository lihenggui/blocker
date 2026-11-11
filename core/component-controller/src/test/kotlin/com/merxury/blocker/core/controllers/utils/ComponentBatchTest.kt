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

package com.merxury.blocker.core.controllers.utils

import android.content.ComponentName
import android.content.pm.IPackageManager
import android.content.pm.PackageManager
import android.os.Build
import com.merxury.blocker.core.model.ComponentType.SERVICE
import com.merxury.blocker.core.model.data.ComponentInfo
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33, 35])
class ComponentBatchTest {
    @Test
    fun systemBatchUsesVersionedSignatureAndPreservesFlags() {
        val pm = mock<IPackageManager>()
        val components = List(3) { ComponentName("com.example", "com.example.Service$it") }

        pm.setComponentsEnabled(components, PackageManager.COMPONENT_ENABLED_STATE_DISABLED, 10, "com.blocker")

        val settings = argumentCaptor<List<PackageManager.ComponentEnabledSetting>>()
        if (Build.VERSION.SDK_INT >= 34) {
            verify(pm).setComponentEnabledSettings(settings.capture(), eq(10), eq("com.blocker"))
        } else {
            verify(pm).setComponentEnabledSettings(settings.capture(), eq(10))
        }
        assertEquals(components, settings.firstValue.map { it.componentName })
        assertTrue(settings.firstValue.all { it.enabledFlags == 0 && it.enabledState == PackageManager.COMPONENT_ENABLED_STATE_DISABLED })
    }

    @Test
    fun batchesAreDeduplicatedBoundedAndSeparatedByApplication() = runTest {
        val firstPackage = List(450) { ComponentInfo("com.first", "com.tracker.Service$it", SERVICE) }
        val secondPackage = firstPackage.first().copy(packageName = "com.second")
        val batches = mutableListOf<List<ComponentInfo>>()
        val processed = mutableListOf<ComponentInfo>()

        val count = controlComponentBatches(
            firstPackage + firstPackage.first() + secondPackage,
            applyBatch = {
                batches.add(it)
                true
            },
            onApplied = { processed.add(it) },
        )

        assertEquals(451, count)
        assertEquals(listOf(200, 200, 50, 1), batches.map { it.size })
        assertTrue(batches.all { batch -> batch.map { it.packageName }.distinct().size == 1 })
        assertEquals(firstPackage + secondPackage, processed)
    }
}
