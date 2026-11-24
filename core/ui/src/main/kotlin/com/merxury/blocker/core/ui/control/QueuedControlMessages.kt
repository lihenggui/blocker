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

package com.merxury.blocker.core.ui.control

import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.res.stringResource
import com.merxury.blocker.core.model.component.ComponentId
import com.merxury.blocker.core.ui.R.string

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

/**
 * [this] queue with [queued] at its end. A message about a component replaces one about it that
 * waits, so repeated taps queue one message each; the message shown, the first, stays, and so do
 * batch summaries.
 */
fun List<QueuedControlMessage>.enqueue(queued: QueuedControlMessage): List<QueuedControlMessage> {
    val waiting = drop(1).filterNot { queued.component != null && it.component == queued.component }
    return take(1) + waiting + queued
}

/**
 * Shows the first of [messages] in a snackbar for its whole duration, with an action to grant
 * access when missing access caused it, then reports it as shown, so each is told once.
 *
 * @param onShow given the id of the message once it is no longer shown
 */
@Composable
fun ControlMessageSnackbar(
    messages: List<QueuedControlMessage>,
    snackbarHostState: SnackbarHostState,
    onShow: (Long) -> Unit,
    onGrantAccess: () -> Unit,
) {
    val grantLabel = stringResource(string.core_ui_grant_access)
    val currentGrant by rememberUpdatedState(onGrantAccess)
    val currentShow by rememberUpdatedState(onShow)
    val queued = messages.firstOrNull()
    val text = queued?.message?.text()
    LaunchedEffect(queued?.id) {
        if (queued == null || text == null) return@LaunchedEffect
        val result = snackbarHostState.showSnackbar(
            message = text,
            actionLabel = grantLabel.takeIf { queued.message.needsAccess },
            duration = SnackbarDuration.Long,
            withDismissAction = true,
        )
        if (result == SnackbarResult.ActionPerformed) currentGrant()
        currentShow(queued.id)
    }
}
