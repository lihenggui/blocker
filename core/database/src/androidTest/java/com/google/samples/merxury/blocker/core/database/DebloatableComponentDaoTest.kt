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

package com.google.samples.merxury.blocker.core.database

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.merxury.blocker.core.database.debloater.DebloatableComponentDao
import com.merxury.blocker.core.database.debloater.DebloatableComponentEntity
import com.merxury.blocker.core.database.debloater.DebloaterDatabase
import com.merxury.blocker.core.model.ComponentType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DebloatableComponentDaoTest {
    private lateinit var debloatableComponentDao: DebloatableComponentDao
    private lateinit var db: DebloaterDatabase

    @Before
    fun createDb() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(
            context,
            DebloaterDatabase::class.java,
        ).build()
        debloatableComponentDao = db.DebloatableComponentDao()
    }

    @Test
    fun givenDebloatableComponents_whenUpsertAll_thenRetrieveSameItems() = runTest {
        val debloatableComponents = listOf(
            DebloatableComponentEntity(
                packageName = "com.example.app1",
                componentName = "com.example.app1.ShareActivity",
                simpleName = "ShareActivity",
                displayName = "Share via App1",
                ifwBlocked = false,
                pmBlocked = false,
                type = ComponentType.ACTIVITY,
                exported = true,
            ),
            DebloatableComponentEntity(
                packageName = "com.example.app2",
                componentName = "com.example.app2.SendActivity",
                simpleName = "SendActivity",
                displayName = "Send with App2",
                ifwBlocked = true,
                pmBlocked = false,
                type = ComponentType.ACTIVITY,
                exported = true,
            ),
        )
        debloatableComponentDao.upsertAll(debloatableComponents)
        val savedEntities = debloatableComponentDao.getAll().first()

        assertEquals(debloatableComponents, savedEntities)
    }

    @Test
    fun givenExistingEntity_whenUpsertWithUpdatedData_thenEntityIsUpdated() = runTest {
        val initialEntity = DebloatableComponentEntity(
            packageName = "com.example.app",
            componentName = "com.example.app.ShareActivity",
            simpleName = "ShareActivity",
            displayName = "Share via App",
            ifwBlocked = false,
            pmBlocked = false,
            type = ComponentType.ACTIVITY,
            exported = true,
        )
        debloatableComponentDao.upsertAll(listOf(initialEntity))

        val updatedEntity = initialEntity.copy(
            displayName = "Updated Share Name",
            ifwBlocked = true,
            pmBlocked = true,
        )
        debloatableComponentDao.upsertAll(listOf(updatedEntity))

        val savedEntities = debloatableComponentDao.getAll().first()
        assertEquals(1, savedEntities.size)
        assertEquals(updatedEntity, savedEntities.first())
    }

    @Test
    fun givenEmptyDatabase_whenGetAll_thenReturnEmptyList() = runTest {
        val savedEntities = debloatableComponentDao.getAll().first()
        assertTrue(savedEntities.isEmpty())
    }
}
