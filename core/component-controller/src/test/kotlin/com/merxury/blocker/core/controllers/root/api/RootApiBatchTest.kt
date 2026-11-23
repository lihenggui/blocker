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

package com.merxury.blocker.core.controllers.root.api

import android.os.Build
import be.mygod.librootkotlinx.ParcelableBoolean
import com.merxury.blocker.core.exception.RootUnavailableException
import com.merxury.blocker.core.model.ComponentState
import com.merxury.blocker.core.model.ComponentType.SERVICE
import com.merxury.blocker.core.model.data.ComponentInfo
import com.merxury.blocker.core.root.RootCommandExecutor
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [26, 33, 35])
class RootApiBatchTest {
    @Test
    fun givenDifferentApiLevels_whenDisablingComponents_thenUsesNativeOrLegacyBatch() = runTest {
        val executor = mock<RootCommandExecutor>()
        whenever(executor.execute(any<SetComponentsEnabledSettingCommand>())).thenReturn(ParcelableBoolean(true))
        whenever(executor.execute(any<SetComponentEnabledSettingCommand>())).thenReturn(ParcelableBoolean(true))
        val controller = RootApiController(mock(), executor, mock())
        val components = List(20) { ComponentInfo("com.example", "com.tracker.Service$it", SERVICE) }
        val processed = mutableListOf<ComponentInfo>()

        assertEquals(20, controller.batchDisable(components) { processed.add(it) })

        if (Build.VERSION.SDK_INT >= 33) {
            verify(executor).execute(any<SetComponentsEnabledSettingCommand>())
        } else {
            verify(executor, times(20)).execute(any<SetComponentEnabledSettingCommand>())
        }
        assertEquals(components, processed)
    }

    @Test
    @Config(sdk = [33])
    fun givenStaleComponentInBatch_whenBatchCallThrows_thenDisablesTheOthersOneByOne() = runTest {
        val executor = mock<RootCommandExecutor>()
        val components = List(3) { ComponentInfo("com.example", "com.tracker.Service$it", SERVICE) }
        val stale = components[1]
        whenever(executor.execute(any<SetComponentsEnabledSettingCommand>())).thenThrow(IllegalArgumentException("Unknown component"))
        whenever(executor.execute(any<SetComponentEnabledSettingCommand>())).thenReturn(ParcelableBoolean(true))
        whenever(executor.execute(eq(SetComponentEnabledSettingCommand(stale.packageName, stale.name, ComponentState.DISABLED.pmValue))))
            .thenThrow(IllegalArgumentException("Unknown component"))
        val controller = RootApiController(mock(), executor, mock())
        val processed = mutableListOf<ComponentInfo>()

        assertEquals(2, controller.batchDisable(components) { processed.add(it) })

        verify(executor, times(3)).execute(any<SetComponentEnabledSettingCommand>())
        assertEquals(components - stale, processed)
    }

    @Test
    @Config(sdk = [26, 33])
    fun givenRootDenied_whenDisablingComponents_thenFailsWithoutTryingEachComponent() = runTest {
        val executor = mock<RootCommandExecutor>()
        whenever(executor.execute(any<SetComponentsEnabledSettingCommand>())).thenThrow(RootUnavailableException())
        whenever(executor.execute(any<SetComponentEnabledSettingCommand>())).thenThrow(RootUnavailableException())
        val controller = RootApiController(mock(), executor, mock())
        val components = List(3) { ComponentInfo("com.example", "com.tracker.Service$it", SERVICE) }
        val processed = mutableListOf<ComponentInfo>()

        assertFailsWith<RootUnavailableException> {
            controller.batchDisable(components) { processed.add(it) }
        }

        // One attempt: the batch call on API 33+, the first component on older systems.
        verify(executor, times(if (Build.VERSION.SDK_INT >= 33) 0 else 1)).execute(any<SetComponentEnabledSettingCommand>())
        assertEquals(emptyList(), processed)
    }
}
