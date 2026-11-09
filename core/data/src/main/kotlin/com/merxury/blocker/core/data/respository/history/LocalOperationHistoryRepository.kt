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

import com.merxury.blocker.core.database.history.OperationEntity
import com.merxury.blocker.core.database.history.OperationHistoryDao
import com.merxury.blocker.core.database.history.asEntity
import com.merxury.blocker.core.database.history.asExternalModel
import com.merxury.blocker.core.dispatchers.BlockerDispatchers.IO
import com.merxury.blocker.core.dispatchers.Dispatcher
import com.merxury.blocker.core.model.data.Operation
import com.merxury.blocker.core.model.data.OperationEntry
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject
import kotlin.time.Instant

internal class LocalOperationHistoryRepository @Inject constructor(
    private val dao: OperationHistoryDao,
    @Dispatcher(IO) private val ioDispatcher: CoroutineDispatcher,
) : OperationHistoryRepository {
    override fun observeOperations() = dao.observeOperations().map { operations ->
        operations.map { it.asExternalModel() }
    }

    override suspend fun getEntries(operationId: Long) = withContext(ioDispatcher) {
        dao.getEntries(operationId).map { it.asExternalModel() }
    }

    override suspend fun getEntriesSince(timestamp: Instant) = withContext(ioDispatcher) {
        dao.getEntriesSince(timestamp).map { it.asExternalModel() }
    }

    override suspend fun insertOperation(operation: Operation, entries: List<OperationEntry>): Long = withContext(ioDispatcher) {
        dao.insertOperation(
            OperationEntity(
                id = operation.id,
                timestamp = operation.timestamp,
                source = operation.source,
                ref = operation.ref,
                undoneByOperationId = operation.undoneByOperationId,
            ),
            entries.map { it.asEntity() },
        )
    }

    override suspend fun insertEntries(entries: List<OperationEntry>) = withContext(ioDispatcher) {
        dao.insertEntries(entries.map { it.asEntity() })
    }

    override suspend fun markUndone(operationIds: List<Long>, undoOperationId: Long) = withContext(ioDispatcher) {
        dao.markUndone(operationIds, undoOperationId)
    }

    override suspend fun deleteOlderThan(timestamp: Instant) = withContext(ioDispatcher) {
        dao.deleteOlderThan(timestamp)
    }
}
