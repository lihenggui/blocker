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
import com.merxury.blocker.core.database.app.InstalledAppDao
import com.merxury.blocker.core.database.app.InstalledAppDatabase
import com.merxury.blocker.core.database.app.InstalledAppEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals

class InstalledAppDaoTest {
    private lateinit var installedAppDao: InstalledAppDao
    private lateinit var db: InstalledAppDatabase

    @Before
    fun createDb() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(
            context,
            InstalledAppDatabase::class.java,
        ).build()
        installedAppDao = db.installedAppDao()
    }

    @Test
    fun installedAppDao_get_the_same_items() = runTest {
        val installedAppEntities = listOf(
            InstalledAppEntity("com.merxury.blocker.test1"),
            InstalledAppEntity("com.merxury.blocker.test2"),
            InstalledAppEntity("com.merxury.blocker.test3"),
        )
        installedAppDao.upsertInstalledApps(installedAppEntities)
        val savedInstalledApps = installedAppDao.getInstalledApps().first()
        assertEquals(
            installedAppEntities,
            savedInstalledApps,
        )
    }

    @Test
    fun installedAppDao_get_the_same_items_by_package_name() = runTest {
        val installedAppEntities = listOf(
            InstalledAppEntity("com.merxury.blocker.test1"),
            InstalledAppEntity("com.merxury.blocker.test2"),
            InstalledAppEntity("com.merxury.blocker.test3"),
        )
        installedAppDao.upsertInstalledApps(installedAppEntities)
        val savedInstalledApps = installedAppDao.getInstalledApp("com.merxury.blocker.test2")
            .first()
        assertEquals(
            installedAppEntities[1],
            savedInstalledApps,
        )
    }

    @Test
    fun installedAppDao_deletes_items_by_package_name() = runTest {
        val installedAppEntities = listOf(
            InstalledAppEntity("com.merxury.blocker.test1"),
            InstalledAppEntity("com.merxury.blocker.test2"),
            InstalledAppEntity("com.merxury.blocker.test3"),
        )
        installedAppDao.upsertInstalledApps(installedAppEntities)
        val (toDelete, toKeep) = installedAppEntities
            .partition { it.packageName == "com.merxury.blocker.test2" }
        installedAppDao.delete(toDelete[0])
        val savedInstalledApps = installedAppDao.getInstalledApps().first()
        assertEquals(
            toKeep,
            savedInstalledApps,
        )
    }

    @Test
    fun installedAppDao_deletes_items_by_package_names() = runTest {
        val installedAppEntities = listOf(
            InstalledAppEntity("com.merxury.blocker.test1"),
            InstalledAppEntity("com.merxury.blocker.test2"),
            InstalledAppEntity("com.merxury.blocker.test3"),
        )
        installedAppDao.upsertInstalledApps(installedAppEntities)
        val (toDelete, toKeep) = installedAppEntities
            .partition { it.packageName == "com.merxury.blocker.test2" || it.packageName == "com.merxury.blocker.test3" }
        installedAppDao.deleteApps(toDelete)
        val savedInstalledApps = installedAppDao.getInstalledApps().first()
        assertEquals(
            toKeep,
            savedInstalledApps,
        )
    }

    @Test
    fun installedAppDao_deletes_all_items() = runTest {
        val installedAppEntities = listOf(
            InstalledAppEntity("com.merxury.blocker.test1"),
            InstalledAppEntity("com.merxury.blocker.test2"),
            InstalledAppEntity("com.merxury.blocker.test3"),
        )
        installedAppDao.upsertInstalledApps(installedAppEntities)
        installedAppDao.deleteAll()
        val savedInstalledApps = installedAppDao.getInstalledApps().first()
        assertEquals(
            emptyList(),
            savedInstalledApps,
        )
    }
}