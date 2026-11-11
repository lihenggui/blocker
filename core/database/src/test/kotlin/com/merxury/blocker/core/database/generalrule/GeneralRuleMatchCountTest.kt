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
class GeneralRuleMatchCountTest {
    @Test
    fun batchCountUpdatePreservesRuleMetadataAndDoesNotRecreateDeletedRules() = runTest {
        val database = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), GeneralRuleDatabase::class.java).build()
        try {
            val dao = database.generalRuleDao()
            val first = GeneralRuleEntity(1, "New name", searchKeyword = listOf("new.keyword"), website = "https://example.com")
            val second = GeneralRuleEntity(2, "Unchanged", matchedAppCount = 3)
            dao.upsertGeneralRules(listOf(first, second))

            dao.updateMatchedAppCounts(mapOf(1 to 5, 2 to 3, 99 to 10))

            assertEquals(listOf(first.copy(matchedAppCount = 5), second), dao.getGeneralRuleEntities().first())
        } finally {
            database.close()
        }
    }
}
