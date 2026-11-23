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

package com.merxury.blocker.core.controllers.wake

import com.merxury.blocker.core.model.privileged.CallResult
import java.io.IOException

/**
 * The value of a wake write, or an [IOException] naming [action] and the failure. A write that
 * cannot be made stops the run that asked for it, which reports the message as its status.
 */
internal fun <R> CallResult<R>.orThrow(action: String): R = when (this) {
    is CallResult.Success -> value
    is CallResult.Failure -> throw IOException("Cannot $action: $reason")
}
