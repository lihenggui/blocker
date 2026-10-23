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

package com.merxury.blocker.core.testing.repository

import com.merxury.blocker.core.data.respository.history.OperationHistoryRepository
import com.merxury.blocker.core.model.data.Operation
import com.merxury.blocker.core.model.data.OperationEntry
import com.merxury.blocker.core.model.data.OperationSource.UNDO
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.time.Instant

class TestOperationHistoryRepository : OperationHistoryRepository {
    private val operations = MutableStateFlow<List<Operation>>(emptyList())
    private val entries = mutableListOf<OperationEntry>()
    private var nextId = 1L

    override fun observeOperations() = operations.asStateFlow()

    override suspend fun getEntries(operationId: Long) = entries.filter { it.operationId == operationId }

    override suspend fun getEntriesSince(timestamp: Instant): List<OperationEntry> = operations.value
        .filter { it.timestamp >= timestamp && it.undoneByOperationId == null && it.source != UNDO }
        .sortedWith(compareBy<Operation> { it.timestamp }.thenBy { it.id })
        .flatMap { getEntries(it.id) }

    override suspend fun insertOperation(operation: Operation, entries: List<OperationEntry>): Long {
        val id = nextId++
        operations.value = operations.value + operation.copy(id = id)
        insertEntries(entries.map { it.copy(operationId = id) })
        return id
    }

    override suspend fun insertEntries(entries: List<OperationEntry>) {
        entries.forEach { update ->
            val existing = this.entries.find {
                it.operationId == update.operationId && it.packageName == update.packageName && it.componentName == update.componentName
            }
            val merged = update.copy(
                beforePmBlocked = existing?.beforePmBlocked ?: update.beforePmBlocked,
                beforeIfwBlocked = existing?.beforeIfwBlocked ?: update.beforeIfwBlocked,
            )
            this.entries.remove(existing)
            if (merged.beforePmBlocked != merged.afterPmBlocked || merged.beforeIfwBlocked != merged.afterIfwBlocked) {
                this.entries.add(merged)
            }
        }
        publish()
    }

    override suspend fun markUndone(operationIds: List<Long>, undoOperationId: Long) {
        operations.value = operations.value.map {
            if (it.id in operationIds) it.copy(undoneByOperationId = undoOperationId) else it
        }
    }

    override suspend fun deleteOlderThan(timestamp: Instant) {
        operations.value = operations.value.filter { it.timestamp >= timestamp }
        val retained = operations.value.map { it.id }.toSet()
        entries.removeAll { it.operationId !in retained }
    }

    private fun publish() {
        operations.value = operations.value.map { operation ->
            operation.copy(entryCount = entries.count { it.operationId == operation.id })
        }.sortedWith(compareByDescending<Operation> { it.timestamp }.thenByDescending { it.id })
    }
}
