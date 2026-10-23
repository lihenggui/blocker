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

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.merxury.blocker.core.model.data.Operation
import com.merxury.blocker.core.model.data.OperationSource
import kotlin.time.Instant

@Entity(
    tableName = "operation",
    indices = [Index(value = ["timestamp"])],
)
data class OperationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Instant,
    val source: OperationSource,
    val ref: String? = null,
    @ColumnInfo(name = "undone_by_operation_id") val undoneByOperationId: Long? = null,
)

data class OperationWithEntryCount(
    @ColumnInfo(name = "id") val id: Long,
    @ColumnInfo(name = "timestamp") val timestamp: Instant,
    @ColumnInfo(name = "source") val source: OperationSource,
    @ColumnInfo(name = "ref") val ref: String?,
    @ColumnInfo(name = "undone_by_operation_id") val undoneByOperationId: Long?,
    @ColumnInfo(name = "entry_count") val entryCount: Int,
)

fun OperationWithEntryCount.asExternalModel() = Operation(
    id = id,
    timestamp = timestamp,
    source = source,
    ref = ref,
    undoneByOperationId = undoneByOperationId,
    entryCount = entryCount,
)
