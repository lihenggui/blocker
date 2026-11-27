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

package com.merxury.blocker.core.model.history

import kotlin.time.Instant

/**
 * A recorded action that groups component state changes.
 *
 * @property id the operation's identifier
 * @property timestamp when the operation occurred
 * @property source the action that caused the operation
 * @property ref the package for app detail or debloater, rule ID for rule detail, calling package
 * for the external provider, or original operation ID for a single undo. Null otherwise.
 * @property undoneByOperationId the ID of the operation that reversed this one, if any
 * @property entryCount the number of component entries in the query result
 */
data class Operation(
    val id: Long,
    val timestamp: Instant,
    val source: OperationSource,
    val ref: String? = null,
    val undoneByOperationId: Long? = null,
    val entryCount: Int = 0,
)
