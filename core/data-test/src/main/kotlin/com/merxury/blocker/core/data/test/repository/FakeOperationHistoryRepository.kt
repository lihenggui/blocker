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

package com.merxury.blocker.core.data.test.repository

import com.merxury.blocker.core.data.history.OperationHistoryRepository
import com.merxury.blocker.core.model.history.Operation
import com.merxury.blocker.core.model.history.OperationEntry
import com.merxury.blocker.core.model.history.UndoResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Instant

@Singleton
class FakeOperationHistoryRepository @Inject constructor() : OperationHistoryRepository {
    val operations = MutableStateFlow<List<Operation>>(emptyList())
    val entries = mutableMapOf<Long, List<OperationEntry>>()
    var undoResult = UndoResult(restored = 0, alreadyAtTarget = 0, skipped = 0, failed = 0)

    override fun observeOperations() = operations.asStateFlow()
    override suspend fun getEntries(operationId: Long) = entries[operationId].orEmpty()
    override suspend fun undoOperation(id: Long) = undoResult
    override suspend fun undoSince(since: Instant) = undoResult
}
