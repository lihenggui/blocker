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

package com.merxury.blocker.feature.applist.impl.component

import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import com.merxury.blocker.core.model.component.AppKey
import com.merxury.blocker.core.model.component.UserId
import com.merxury.blocker.feature.applist.impl.AppAction

/**
 * An action waiting for the user to confirm it, for exactly the app it was asked for: the same
 * package in another user is a different app.
 */
internal data class PendingConfirmation(
    val action: AppAction,
    val app: AppKey,
)

/** Saves a pending confirmation with its app's user, so a restored dialog acts on the same app. */
internal val PendingConfirmationSaver: Saver<PendingConfirmation?, Any> = listSaver(
    save = { pending -> pending?.let { listOf(it.action.name, it.app.packageName, it.app.userId.value) }.orEmpty() },
    restore = { values ->
        if (values.isEmpty()) {
            null
        } else {
            PendingConfirmation(AppAction.valueOf(values[0] as String), AppKey(values[1] as String, UserId(values[2] as Int)))
        }
    },
)
