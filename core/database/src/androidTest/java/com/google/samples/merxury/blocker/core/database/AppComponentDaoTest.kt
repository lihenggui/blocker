/*
 * Copyright 2023 Blocker
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
import com.merxury.blocker.core.database.app.AppComponentDao
import com.merxury.blocker.core.database.app.AppComponentEntity
import com.merxury.blocker.core.database.app.InstalledAppDatabase
import com.merxury.blocker.core.model.ComponentType.ACTIVITY
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals

class AppComponentDaoTest {
    private lateinit var appComponentDao: AppComponentDao
    private lateinit var db: InstalledAppDatabase

    @Before
    fun createDb() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(
            context,
            InstalledAppDatabase::class.java,
        ).build()
        appComponentDao = db.appComponentDao()
    }

    @Test
    fun appComponentDao_get_the_same_items() = runTest {
        val packageName = "com.merxury.blocker.test"
        val appComponentEntities = listOf(
            testAppComponentEntity(componentName = ".component1"),
            testAppComponentEntity(componentName = ".component2"),
            testAppComponentEntity(componentName = ".component3"),
        )
        appComponentDao.upsertComponentList(appComponentEntities)
        val savedAppComponents = appComponentDao.getByPackageName(packageName).first()
        assertEquals(
            appComponentEntities,
            savedAppComponents,
        )
    }

    @Test
    fun appComponentDao_get_the_same_items_by_package_name_and_component_name() = runTest {
        val packageName = "com.merxury.blocker.test"
        val componentName = ".component1"
        val appComponentEntities = listOf(
            testAppComponentEntity(componentName = componentName),
            testAppComponentEntity(componentName = ".component2"),
            testAppComponentEntity(componentName = ".component3"),
        )
        appComponentDao.upsertComponentList(appComponentEntities)
        val savedAppComponents = appComponentDao.getByPackageNameAndComponentName(
            packageName = packageName,
            componentName = componentName,
        ).first()
        assertEquals(
            appComponentEntities.first(),
            savedAppComponents,
        )
    }

    @Test
    fun appComponentDao_deletes_items_by_package_name() = runTest {
        val packageName = "com.merxury.blocker.test"
        val appComponentEntities = listOf(
            testAppComponentEntity(componentName = ".component1"),
            testAppComponentEntity(componentName = ".component2"),
            testAppComponentEntity(componentName = ".component3"),
        )
        appComponentDao.upsertComponentList(appComponentEntities)
        appComponentDao.deleteByPackageName(packageName)
        val savedAppComponents = appComponentDao.getByPackageName(packageName).first()
        assertEquals(
            emptyList(),
            savedAppComponents,
        )
    }
}

private fun testAppComponentEntity(
    packageName: String = "com.merxury.blocker.test",
    componentName: String,
) = AppComponentEntity(
    packageName = packageName,
    componentName = componentName,
    ifwBlocked = false,
    pmBlocked = false,
    type = ACTIVITY,
    exported = false,
)
