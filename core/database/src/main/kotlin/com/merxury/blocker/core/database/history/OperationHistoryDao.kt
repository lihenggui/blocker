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

package com.merxury.blocker.core.database.history

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.merxury.blocker.core.model.component.IfwBlock
import com.merxury.blocker.core.model.component.PmSetting
import kotlinx.coroutines.flow.Flow
import kotlin.time.Instant

@Dao
abstract class OperationHistoryDao {
    @Query(
        """
        SELECT operation.*, COUNT(operation_entry.operation_id) AS entry_count
        FROM operation
        LEFT JOIN operation_entry ON operation.id = operation_entry.operation_id
        GROUP BY operation.id
        ORDER BY operation.timestamp DESC, operation.id DESC
        """,
    )
    abstract fun observeOperations(): Flow<List<OperationWithEntryCount>>

    @Query("SELECT * FROM operation_entry WHERE operation_id = :operationId ORDER BY user_id, package_name, component_name")
    abstract suspend fun getEntries(operationId: Long): List<OperationEntryEntity>

    @Query(
        """
        SELECT operation_entry.* FROM operation_entry
        INNER JOIN operation ON operation.id = operation_entry.operation_id
        WHERE operation.timestamp >= :timestamp
          AND operation.undone_by_operation_id IS NULL
          AND operation.source != 'UNDO'
        ORDER BY operation.timestamp, operation.id, operation_entry.user_id, operation_entry.package_name, operation_entry.component_name
        """,
    )
    abstract suspend fun getEntriesSince(timestamp: Instant): List<OperationEntryEntity>

    @Transaction
    open suspend fun insertOperation(operation: OperationEntity, entries: List<OperationEntryEntity>): Long {
        val id = insertOperation(operation)
        insertEntries(entries.map { it.copy(operationId = id) })
        return id
    }

    /** Keep the first before-state and the latest after-state for each component and user. */
    @Transaction
    open suspend fun insertEntries(entries: List<OperationEntryEntity>) {
        val rowIds = insertNewEntries(entries)
        entries.forEachIndexed { index, entry ->
            // Room returns -1 for rows skipped on a primary key conflict.
            if (rowIds[index] == -1L) {
                updateAfterState(entry.operationId, entry.userId, entry.packageName, entry.componentName, entry.afterPm, entry.afterIfw)
            }
        }
        entries.map { it.operationId }.distinct().forEach { deleteUnchangedEntries(it) }
    }

    @Transaction
    open suspend fun markUndone(operationIds: List<Long>, undoOperationId: Long) {
        // Leave room for the undo ID within SQLite's 999 bind parameter limit on older Android releases.
        operationIds.chunked(998).forEach { markUndoneBatch(it, undoOperationId) }
    }

    @Query("DELETE FROM operation WHERE timestamp < :timestamp")
    abstract suspend fun deleteOlderThan(timestamp: Instant)

    @Insert
    protected abstract suspend fun insertOperation(operation: OperationEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    protected abstract suspend fun insertNewEntries(entries: List<OperationEntryEntity>): List<Long>

    @Query(
        """
        UPDATE operation_entry SET after_pm = :afterPm, after_ifw = :afterIfw
        WHERE operation_id = :operationId AND user_id = :userId AND package_name = :packageName AND component_name = :componentName
        """,
    )
    protected abstract suspend fun updateAfterState(
        operationId: Long,
        userId: Int,
        packageName: String,
        componentName: String,
        afterPm: PmSetting,
        afterIfw: IfwBlock,
    )

    @Query("DELETE FROM operation_entry WHERE operation_id = :operationId AND before_pm = after_pm AND before_ifw = after_ifw")
    protected abstract suspend fun deleteUnchangedEntries(operationId: Long)

    @Query("UPDATE operation SET undone_by_operation_id = :undoOperationId WHERE id IN (:operationIds)")
    protected abstract suspend fun markUndoneBatch(operationIds: List<Long>, undoOperationId: Long)
}
