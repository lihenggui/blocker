/*
 * Copyright 2024 Blocker
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

package com.merxury.blocker.core.data

import com.merxury.blocker.core.model.ComponentType.RECEIVER
import com.merxury.blocker.core.model.data.ComponentInfo
import com.merxury.blocker.core.ui.data.ComponentInfoUiData
import com.merxury.blocker.core.ui.data.toComponentInfoUiData
import org.junit.Test

class ComponentInfoUiDataTest {
    @Test
    fun givenComponentInfo_whenDoMapping_thenItCanBeConvertedToComponentInfoUiData() {
        // Given
        val componentInfo = ComponentInfo(
            name = "name",
            simpleName = "simpleName",
            packageName = "packageName",
            type = RECEIVER,
            pmBlocked = true,
            exported = true,
            isRunning = true,
            ifwBlocked = true,
            description = "description",
        )

        // When
        val componentInfoUiData = componentInfo.toComponentInfoUiData(
            switchUiState = true,
            dirty = true,
        )

        // Then
        assert(componentInfoUiData.name == "name")
        assert(componentInfoUiData.simpleName == "simpleName")
        assert(componentInfoUiData.packageName == "packageName")
        assert(componentInfoUiData.type == RECEIVER)
        assert(componentInfoUiData.pmBlocked)
        assert(componentInfoUiData.switchUiState)
        assert(componentInfoUiData.dirty)
        assert(componentInfoUiData.exported)
        assert(componentInfoUiData.isRunning)
        assert(componentInfoUiData.ifwBlocked)
        assert(componentInfoUiData.description == "description")
    }

    @Test
    fun givenComponentInfoUiData_whenDoMapping_thenItCanBeConvertedToComponentInfo() {
        // Given
        val componentInfoUiData = ComponentInfoUiData(
            name = "name",
            simpleName = "simpleName",
            packageName = "packageName",
            type = RECEIVER,
            pmBlocked = true,
            switchUiState = true,
            dirty = true,
            exported = true,
            isRunning = true,
            ifwBlocked = true,
            description = "description",
        )

        // When
        val componentInfo = componentInfoUiData.toComponentInfo()

        // Then
        assert(componentInfo.name == "name")
        assert(componentInfo.simpleName == "simpleName")
        assert(componentInfo.packageName == "packageName")
        assert(componentInfo.type == RECEIVER)
        assert(componentInfo.pmBlocked)
        assert(componentInfo.exported)
        assert(componentInfo.isRunning)
        assert(componentInfo.ifwBlocked)
        assert(componentInfo.description == "description")
    }
}
