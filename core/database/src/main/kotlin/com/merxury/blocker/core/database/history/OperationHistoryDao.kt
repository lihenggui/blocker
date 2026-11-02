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
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow
import kotlin.time.Instant

@Dao
abstract class OperationHistoryDao {
    @Transaction
    open suspend fun insertOperation(
        operation: OperationEntity,
        entries: List<OperationEntryEntity>,
    ): Long {
        val id = insertOperation(operation)
        insertEntries(entries.map { it.copy(operationId = id) })
        return id
    }

    @Insert
    protected abstract suspend fun insertOperation(operation: OperationEntity): Long

    /** Keep the first before-state and the most recent after-state across progressive flushes. */
    @Transaction
    open suspend fun insertEntries(entries: List<OperationEntryEntity>) {
        entries.groupBy { it.operationId }.forEach { (operationId, updates) ->
            val existing = getEntries(operationId).associateBy { it.packageName to it.componentName }
            upsertEntries(
                updates.map { entry ->
                    val first = existing[entry.packageName to entry.componentName]
                    entry.copy(
                        beforePm = first?.beforePm ?: entry.beforePm,
                        beforeIfw = first?.beforeIfw ?: entry.beforeIfw,
                    )
                },
            )
            deleteUnchangedEntries(operationId)
        }
    }

    @Upsert
    protected abstract suspend fun upsertEntries(entries: List<OperationEntryEntity>)

    @Query("DELETE FROM operation_entry WHERE operation_id = :operationId AND before_pm = after_pm AND before_ifw = after_ifw")
    protected abstract suspend fun deleteUnchangedEntries(operationId: Long)

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

    @Query("SELECT * FROM operation_entry WHERE operation_id = :operationId ORDER BY package_name, component_name")
    abstract suspend fun getEntries(operationId: Long): List<OperationEntryEntity>

    /** Entries are ordered oldest first so undo can select the earliest before-state. */
    @Query(
        """
        SELECT operation_entry.* FROM operation_entry
        INNER JOIN operation ON operation.id = operation_entry.operation_id
        WHERE operation.timestamp >= :timestamp
        AND operation.undone_by_operation_id IS NULL
        AND operation.source != 'UNDO'
        ORDER BY operation.timestamp ASC, operation.id ASC
        """,
    )
    abstract suspend fun getEntriesSince(timestamp: Instant): List<OperationEntryEntity>

    @Query("UPDATE operation SET undone_by_operation_id = :undoOperationId WHERE id IN (:operationIds)")
    abstract suspend fun markUndone(operationIds: List<Long>, undoOperationId: Long)

    @Query("DELETE FROM operation WHERE timestamp < :timestamp")
    abstract suspend fun deleteOlderThan(timestamp: Instant)
}
