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
import com.merxury.blocker.core.database.generalrule.GeneralRuleDao
import com.merxury.blocker.core.database.generalrule.GeneralRuleDatabase
import com.merxury.blocker.core.database.generalrule.GeneralRuleEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals

class GeneralRuleDaoTest {
    private lateinit var generalRuleDao: GeneralRuleDao
    private lateinit var db: GeneralRuleDatabase

    @Before
    fun createDb() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(
            context,
            GeneralRuleDatabase::class.java,
        ).build()
        generalRuleDao = db.generalRuleDao()
    }

    @Test
    fun generalRuleDao_get_the_same_items() = runTest {
        val generalRuleEntities = listOf(
            GeneralRuleEntity(id = 0, name = "0"),
            GeneralRuleEntity(id = 1, name = "1"),
            GeneralRuleEntity(id = 2, name = "2"),
        )
        generalRuleDao.upsertGeneralRules(generalRuleEntities)
        val savedGeneralRules = generalRuleDao.getGeneralRuleEntities().first()

        assertEquals(
            generalRuleEntities,
            savedGeneralRules,
        )
    }

    @Test
    fun generalRuleDao_deletes_items_by_ids() = runTest {
        val generalRuleEntities = listOf(
            GeneralRuleEntity(id = 0, name = "0"),
            GeneralRuleEntity(id = 1, name = "1"),
            GeneralRuleEntity(id = 2, name = "2"),
            GeneralRuleEntity(id = 3, name = "3"),
            GeneralRuleEntity(id = 4, name = "4"),
            GeneralRuleEntity(id = 5, name = "5"),
        )
        generalRuleDao.upsertGeneralRules(generalRuleEntities)

        val (toDelete, toKeep) = generalRuleEntities.partition { it.id % 2 == 0 }
        generalRuleDao.deleteGeneralRules(toDelete.map { it.id })
        assertEquals(
            toKeep.map(GeneralRuleEntity::id)
                .toSet(),
            generalRuleDao.getGeneralRuleEntities().first()
                .map(GeneralRuleEntity::id)
                .toSet(),
        )
    }
}
