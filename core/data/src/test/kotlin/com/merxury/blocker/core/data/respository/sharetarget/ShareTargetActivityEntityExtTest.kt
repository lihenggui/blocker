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

package com.merxury.blocker.core.data.respository.sharetarget

import com.merxury.blocker.core.database.sharetarget.ShareTargetActivityEntity
import com.merxury.blocker.core.model.ComponentType
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ShareTargetActivityEntityExtTest {

    @Test
    fun givenShareTargetEntity_whenToComponentInfo_thenMapsAllFieldsCorrectly() {
        val entity = ShareTargetActivityEntity(
            packageName = "com.example.app",
            componentName = "com.example.app.ShareActivity",
            simpleName = "ShareActivity",
            displayName = "Share via App",
            ifwBlocked = true,
            pmBlocked = false,
            exported = true,
        )

        val componentInfo = entity.toComponentInfo()

        assertEquals("com.example.app", componentInfo.packageName)
        assertEquals("com.example.app.ShareActivity", componentInfo.name)
        assertEquals("ShareActivity", componentInfo.simpleName)
        assertEquals(ComponentType.ACTIVITY, componentInfo.type)
        assertTrue(componentInfo.exported)
        assertTrue(componentInfo.ifwBlocked)
        assertFalse(componentInfo.pmBlocked)
    }

    @Test
    fun givenUnblockedEntity_whenToComponentInfo_thenBlockedStatesAreFalse() {
        val entity = ShareTargetActivityEntity(
            packageName = "com.example.app",
            componentName = "com.example.app.ShareActivity",
            simpleName = "ShareActivity",
            displayName = "Share via App",
            ifwBlocked = false,
            pmBlocked = false,
            exported = true,
        )

        val componentInfo = entity.toComponentInfo()

        assertFalse(componentInfo.ifwBlocked)
        assertFalse(componentInfo.pmBlocked)
    }

    @Test
    fun givenBlockedEntity_whenToComponentInfo_thenBlockedStatesAreTrue() {
        val entity = ShareTargetActivityEntity(
            packageName = "com.example.app",
            componentName = "com.example.app.ShareActivity",
            simpleName = "ShareActivity",
            displayName = "Share via App",
            ifwBlocked = true,
            pmBlocked = true,
            exported = true,
        )

        val componentInfo = entity.toComponentInfo()

        assertTrue(componentInfo.ifwBlocked)
        assertTrue(componentInfo.pmBlocked)
    }

    @Test
    fun givenNonExportedEntity_whenToComponentInfo_thenExportedIsFalse() {
        val entity = ShareTargetActivityEntity(
            packageName = "com.example.app",
            componentName = "com.example.app.ShareActivity",
            simpleName = "ShareActivity",
            displayName = "Share via App",
            ifwBlocked = false,
            pmBlocked = false,
            exported = false,
        )

        val componentInfo = entity.toComponentInfo()

        assertFalse(componentInfo.exported)
    }

    @Test
    fun givenEntity_whenToComponentInfo_thenTypeIsAlwaysActivity() {
        val entity = ShareTargetActivityEntity(
            packageName = "com.example.app",
            componentName = "com.example.app.ShareActivity",
            simpleName = "ShareActivity",
            displayName = "Share via App",
            ifwBlocked = false,
            pmBlocked = false,
            exported = true,
        )

        val componentInfo = entity.toComponentInfo()

        assertEquals(ComponentType.ACTIVITY, componentInfo.type)
    }
}
