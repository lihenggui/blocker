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

package com.merxury.blocker.core.controllers.combined

import com.merxury.blocker.core.controllers.IController
import com.merxury.blocker.core.model.ComponentState
import com.merxury.blocker.core.model.ComponentType
import com.merxury.blocker.core.model.data.ComponentInfo
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertEquals

class CombinedBatchRegressionTest {
    @Test
    fun batchDisable_delegatesOneBatchToEachController() = runTest {
        val ifw = RecordingController()
        val pm = RecordingController()
        val components = List(20) {
            ComponentInfo("com.example", "com.example.Receiver$it", ComponentType.RECEIVER)
        }
        val processed = mutableListOf<ComponentInfo>()

        assertEquals(20, CombinedController(ifw, pm).batchDisable(components) { processed.add(it) })

        assertEquals(listOf(components), ifw.disabledBatches)
        assertEquals(listOf(components), pm.disabledBatches)
        assertEquals(components, processed)
    }

    private class RecordingController : IController {
        val disabledBatches = mutableListOf<List<ComponentInfo>>()
        override suspend fun switchComponent(component: ComponentInfo, state: ComponentState) = true
        override suspend fun enable(component: ComponentInfo) = true
        override suspend fun disable(component: ComponentInfo) = true
        override suspend fun checkComponentEnableState(packageName: String, componentName: String) = true
        override suspend fun batchDisable(
            componentList: List<ComponentInfo>,
            action: suspend (ComponentInfo) -> Unit,
        ): Int {
            disabledBatches.add(componentList)
            return super.batchDisable(componentList, action)
        }
    }
}
