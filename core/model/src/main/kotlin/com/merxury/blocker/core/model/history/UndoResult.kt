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

/**
 * Counts of component entries by undo outcome.
 *
 * @property restored the number restored to their target state
 * @property alreadyAtTarget the number already in their target state
 * @property skipped the number where no restoration was attempted
 * @property failed the number where restoration was attempted and failed
 */
data class UndoResult(
    val restored: Int,
    val alreadyAtTarget: Int,
    val skipped: Int,
    val failed: Int,
)
