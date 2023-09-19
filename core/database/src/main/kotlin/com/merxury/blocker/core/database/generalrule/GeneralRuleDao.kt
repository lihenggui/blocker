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

package com.merxury.blocker.core.database.generalrule

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface GeneralRuleDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(generalRule: GeneralRuleEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(generalRules: List<GeneralRuleEntity>)

    @Delete
    suspend fun delete(generalRule: GeneralRuleEntity)

    @Update
    suspend fun update(generalRule: GeneralRuleEntity)

    @Query("SELECT * FROM general_rules")
    fun getGeneralRuleEntities(): Flow<List<GeneralRuleEntity>>

    @Query("SELECT * FROM general_rules WHERE name LIKE '%' || :keyword || '%'")
    fun searchGeneralRule(keyword: String): Flow<List<GeneralRuleEntity>>

    @Query("SELECT * FROM general_rules WHERE id = :id")
    fun getGeneralRuleEntity(id: Int): Flow<GeneralRuleEntity?>

    /**
     * Deletes rows in the db matching the specified [ids]
     */
    @Query(
        value = """
            DELETE FROM general_rules
            WHERE id in (:ids)
        """,
    )
    suspend fun deleteGeneralRules(ids: List<Int>)

    @Upsert
    suspend fun upsertGeneralRules(entities: List<GeneralRuleEntity>)

    @Upsert
    suspend fun upsertGeneralRule(entity: GeneralRuleEntity)

    @Query("DELETE FROM general_rules")
    suspend fun deleteAll()
}
