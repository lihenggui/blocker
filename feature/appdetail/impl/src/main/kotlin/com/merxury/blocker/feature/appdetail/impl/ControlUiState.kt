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

package com.merxury.blocker.feature.appdetail.impl

import com.merxury.blocker.core.model.component.ComponentId
import com.merxury.blocker.core.model.control.ControlGoal
import com.merxury.blocker.core.ui.control.ControlMessage

/**
 * The changes of components in flight and what they ended in.
 *
 * @property inFlight components whose change has not ended, including those a batch of another
 * screen changes; their switches ignore taps
 * @property batch the progress of the batch running for the app, or null
 * @property batchElsewhere whether a batch of another screen, such as the search, changes
 * components of the app
 * @property messages outcomes to tell, oldest first; see [ComponentControlViewModel.onMessageShown]
 */
data class ControlUiState(
    val inFlight: Set<ComponentId> = emptySet(),
    val batch: BatchProgress? = null,
    val batchElsewhere: Boolean = false,
    val messages: List<QueuedControlMessage> = emptyList(),
) {
    /** Whether a batch can be started: not while one runs for the app, from any screen. */
    val batchActionsEnabled: Boolean get() = batch == null && !batchElsewhere
}

/**
 * [done] of [total] components of a batch have been changed or failed.
 *
 * @property done null while the batch is a single request, which reports only once it ends
 */
data class BatchProgress(
    val goal: ControlGoal,
    val done: Int?,
    val total: Int,
)

/**
 * A message waiting to be told. Two equal messages, such as the same switch failing twice, are
 * queued apart by their [id].
 *
 * @property component the component the message is about, or null for a batch summary
 */
data class QueuedControlMessage(
    val id: Long,
    val message: ControlMessage,
    val component: ComponentId? = null,
)
