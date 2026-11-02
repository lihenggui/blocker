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

package com.merxury.blocker.core.data.respository.history

import com.merxury.blocker.core.model.data.Operation
import com.merxury.blocker.core.model.data.OperationEntry
import kotlinx.coroutines.flow.Flow
import kotlin.time.Instant

interface OperationHistoryRepository {
    fun observeOperations(): Flow<List<Operation>>
    suspend fun getEntries(operationId: Long): List<OperationEntry>
    suspend fun getEntriesSince(timestamp: Instant): List<OperationEntry>
    suspend fun insertOperation(operation: Operation, entries: List<OperationEntry>): Long
    suspend fun insertEntries(entries: List<OperationEntry>)
    suspend fun markUndone(operationIds: List<Long>, undoOperationId: Long)
    suspend fun deleteOlderThan(timestamp: Instant)
}
