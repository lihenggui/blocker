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

package com.merxury.blocker.core.model.control

import com.google.common.truth.Truth.assertThat
import com.merxury.blocker.core.model.component.ComponentId
import com.merxury.blocker.core.model.component.ComponentKey
import com.merxury.blocker.core.model.component.ControlLayer.INTENT_FIREWALL
import com.merxury.blocker.core.model.component.ControlLayer.PACKAGE_MANAGER
import com.merxury.blocker.core.model.component.ControlState
import com.merxury.blocker.core.model.component.UserId
import com.merxury.blocker.core.model.data.ControllerType
import org.junit.Assert.assertThrows
import org.junit.Test

class ControlRequestTest {

    @Test
    fun whenMapEachControllerType_thenLayersItBlocksWith() {
        assertThat(ControllerType.entries.associateWith { it.blockLayers }).containsExactly(
            ControllerType.PM,
            setOf(PACKAGE_MANAGER),
            ControllerType.SHIZUKU,
            setOf(PACKAGE_MANAGER),
            ControllerType.IFW,
            setOf(INTENT_FIREWALL),
            ControllerType.IFW_PLUS_PM,
            setOf(PACKAGE_MANAGER, INTENT_FIREWALL),
        )
    }

    @Test
    fun givenBlockWithoutLayers_whenCreate_thenRejected() {
        assertThrows(IllegalArgumentException::class.java) { ControlRequest(UserId.SYSTEM, emptyList(), ControlGoal.BLOCK, emptySet()) }
    }

    @Test
    fun givenUnblockWithoutLayers_whenCreate_thenRejected() {
        assertThrows(IllegalArgumentException::class.java) { ControlRequest(UserId.SYSTEM, emptyList(), ControlGoal.UNBLOCK, emptySet()) }
    }

    @Test
    fun givenResultNotMatchingFailures_whenCreateOutcome_thenRejected() {
        val key = ComponentKey(ComponentId("com.example", "com.example.Main"), UserId.SYSTEM)
        assertThrows(IllegalArgumentException::class.java) {
            ComponentOutcome(key, ControlState.UNKNOWN, ControlResult.ACHIEVED, mapOf(PACKAGE_MANAGER to ControlFailure.StateNotConfirmed))
        }
        assertThrows(IllegalArgumentException::class.java) { ComponentOutcome(key, ControlState.UNKNOWN, ControlResult.FAILED, emptyMap()) }
    }
}
