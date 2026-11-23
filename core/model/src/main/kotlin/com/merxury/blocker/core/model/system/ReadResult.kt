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

package com.merxury.blocker.core.model.system

import com.merxury.blocker.core.model.component.AppKey
import com.merxury.blocker.core.model.component.UserId

/** The outcome of reading from the system: the value, or the reason there is none. */
sealed interface ReadResult<out T> {
    data class Success<T>(val value: T) : ReadResult<T>

    data class Failure(val reason: ReadFailure) : ReadResult<Nothing>
}

/** Why the system could not be read. None of these means "nothing is there". */
sealed interface ReadFailure {
    /** The app is not installed for that user. */
    data class PackageNotFound(val app: AppKey) : ReadFailure

    /** The user's apps cannot be seen from here; another user needs root or Shizuku. */
    data class UserNotReachable(val user: UserId) : ReadFailure

    /** The app is installed but its component list could not be obtained. */
    data class ManifestUnreadable(val packageName: String) : ReadFailure

    /** The system, or the privileged service the read goes through, did not answer. */
    data object ChannelUnavailable : ReadFailure
}

/** The value, or null on failure. */
fun <T> ReadResult<T>.getOrNull(): T? = (this as? ReadResult.Success)?.value
