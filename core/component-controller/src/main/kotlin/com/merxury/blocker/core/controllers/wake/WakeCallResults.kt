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

import com.merxury.blocker.core.model.privileged.CallFailure
import com.merxury.blocker.core.model.privileged.CallResult
import java.io.IOException

/**
 * The channel refused a wake call as not permitted for its identity or its server: the shell, or
 * Shizuku before 13, which cannot run calls that need its own process.
 */
class WakeCallRefusedException(message: String) : IOException(message)

/**
 * The value of a wake write, or an [IOException] naming [action] and the failure, a
 * [WakeCallRefusedException] when the channel refused it. A write that cannot be made stops the
 * run that asked for it, which reports the message as its status.
 */
internal fun <R> CallResult<R>.orThrow(action: String): R = when (this) {
    is CallResult.Success -> value

    is CallResult.Failure -> when (reason) {
        is CallFailure.NotPermitted -> throw WakeCallRefusedException("Cannot $action: $reason")
        else -> throw IOException("Cannot $action: $reason")
    }
}
