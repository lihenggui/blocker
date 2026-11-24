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

package com.merxury.blocker.core.database.generalrule

import androidx.room.Room
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import kotlin.test.assertEquals

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], manifest = Config.NONE)
class GeneralRuleFallbackTest {
    @Test
    fun givenEmptyCache_whenInitializingFallback_thenPersistsRules() = runTest {
        val database = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), GeneralRuleDatabase::class.java).build()
        try {
            val dao = database.generalRuleDao()
            val fallbackRule = GeneralRuleEntity(7, "Fallback rule", searchKeyword = listOf("sdk"))

            assertEquals(listOf(fallbackRule), dao.getOrInitializeGeneralRules(listOf(fallbackRule)))

            assertEquals(listOf(fallbackRule), dao.getGeneralRuleEntities().first())
        } finally {
            database.close()
        }
    }

    @Test
    fun givenPopulatedCache_whenInitializingFallback_thenKeepsCurrentMetadataAndDoesNotRestoreDeletedRules() = runTest {
        val database = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), GeneralRuleDatabase::class.java).build()
        try {
            val dao = database.generalRuleDao()
            val currentRule = GeneralRuleEntity(1, "Current rule", searchKeyword = listOf("current.sdk"))
            val deletedRule = GeneralRuleEntity(2, "Deleted rule")
            dao.upsertGeneralRules(listOf(currentRule, deletedRule))
            dao.delete(deletedRule)

            val rules = dao.getOrInitializeGeneralRules(listOf(currentRule.copy(name = "Outdated rule"), deletedRule))

            assertEquals(listOf(currentRule), rules)
            assertEquals(listOf(currentRule), dao.getGeneralRuleEntities().first())
        } finally {
            database.close()
        }
    }

    @Test
    fun givenDuplicateFallbackRuleIds_whenInitializingEmptyCache_thenKeepsFirstOccurrence() = runTest {
        val database = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), GeneralRuleDatabase::class.java).build()
        try {
            val dao = database.generalRuleDao()
            val firstRule = GeneralRuleEntity(1, "First rule")
            val duplicateRule = firstRule.copy(name = "Duplicate rule")
            val secondRule = GeneralRuleEntity(2, "Second rule")

            val rules = dao.getOrInitializeGeneralRules(listOf(firstRule, duplicateRule, secondRule))

            assertEquals(listOf(firstRule, secondRule), rules)
            assertEquals(listOf(firstRule, secondRule), dao.getGeneralRuleEntities().first())
        } finally {
            database.close()
        }
    }
}
