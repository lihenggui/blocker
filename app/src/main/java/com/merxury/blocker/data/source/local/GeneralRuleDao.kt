package com.merxury.blocker.data.source.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.merxury.blocker.data.source.GeneralRule

@Dao
interface GeneralRuleDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(generalRule: GeneralRule)

    @Delete
    suspend fun delete(generalRule: GeneralRule)

    @Update
    suspend fun update(generalRule: GeneralRule)

    @Query("SELECT * FROM general_rules")
    suspend fun getAll(): List<GeneralRule>
}