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

package com.merxury.blocker.core.database.record

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

/**
 * Reads and writes [PmBlockRecordEntity]. A record is keyed by package, class and user, so adding
 * one that is stored already keeps the stored row with its time.
 */
@Dao
abstract class PmBlockRecordDao {
    /** The records of [userId], by package and class name. Emits again whenever they change. */
    @Query("SELECT * FROM pm_block_record WHERE user_id = :userId ORDER BY package_name, class_name")
    abstract fun observeByUser(userId: Int): Flow<List<PmBlockRecordEntity>>

    /** The records of [userId], by package and class name. */
    @Query("SELECT * FROM pm_block_record WHERE user_id = :userId ORDER BY package_name, class_name")
    abstract suspend fun getByUser(userId: Int): List<PmBlockRecordEntity>

    /** The records of [packageName] for [userId], by class name. */
    @Query("SELECT * FROM pm_block_record WHERE package_name = :packageName AND user_id = :userId ORDER BY class_name")
    abstract suspend fun getByPackage(packageName: String, userId: Int): List<PmBlockRecordEntity>

    /** Adds the [records] not stored yet, in one transaction; a stored one keeps its time. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract suspend fun insertOrIgnore(records: List<PmBlockRecordEntity>): List<Long>

    /** Removes the records with the keys of [records], whatever their time, in one transaction. */
    @Transaction
    open suspend fun delete(records: List<PmBlockRecordEntity>) {
        records.forEach { deleteOne(it.packageName, it.className, it.userId) }
    }

    @Transaction
    open suspend fun deleteMatching(records: List<PmBlockRecordEntity>) {
        records.forEach { deleteMatching(it.packageName, it.className, it.userId, it.recordedAt) }
    }

    /** Removes every record of [packageName] for [userId]. */
    @Query("DELETE FROM pm_block_record WHERE package_name = :packageName AND user_id = :userId")
    abstract suspend fun deleteByPackage(packageName: String, userId: Int)

    /** Removes every record of [userId]. */
    @Query("DELETE FROM pm_block_record WHERE user_id = :userId")
    abstract suspend fun deleteByUser(userId: Int)

    /** How many records are stored, for all users. */
    @Query("SELECT COUNT(*) FROM pm_block_record")
    abstract suspend fun count(): Int

    @Query("DELETE FROM pm_block_record WHERE package_name = :packageName AND class_name = :className AND user_id = :userId")
    protected abstract suspend fun deleteOne(packageName: String, className: String, userId: Int)

    @Query("DELETE FROM pm_block_record WHERE package_name = :packageName AND class_name = :className AND user_id = :userId AND recorded_at = :recordedAt")
    protected abstract suspend fun deleteMatching(packageName: String, className: String, userId: Int, recordedAt: Long)
}
