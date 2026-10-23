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
package com.merxury.blocker.core.model.data

import com.merxury.blocker.core.model.ComponentType
import kotlin.time.Instant

/**
 * A recorded batch of component state changes.
 *
 * @property ref context for tracing the operation back, e.g. the package name or rule id
 * @property undoneByOperationId id of the [OperationSource.UNDO] operation that reverted it
 * @property entryCount number of components whose state changed
 */
data class Operation(
    val id: Long,
    val timestamp: Instant,
    val source: OperationSource,
    val ref: String? = null,
    val undoneByOperationId: Long? = null,
    val entryCount: Int = 0,
)

/**
 * State snapshot of one component before and after an [Operation].
 * PM and IFW are recorded separately so they can be restored independently.
 */
data class OperationEntry(
    val operationId: Long,
    val packageName: String,
    val componentName: String,
    val componentType: ComponentType,
    val beforePmBlocked: Boolean,
    val beforeIfwBlocked: Boolean,
    val afterPmBlocked: Boolean,
    val afterIfwBlocked: Boolean,
)
