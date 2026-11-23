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

package com.merxury.blocker.core.model.privileged

/** The outcome of one call through the privileged channel. */
sealed interface CallResult<out R> {
    data class Success<R>(val value: R) : CallResult<R>

    data class Failure(val reason: CallFailure) : CallResult<Nothing>
}

/**
 * Why a privileged call did not complete. A failed call may still have changed part of what it
 * was asked to change.
 */
sealed interface CallFailure {
    /** No root, Shizuku not running or not granted, or the privileged process died. */
    data object ChannelUnavailable : CallFailure

    /** The system refused: the identity the channel runs as may not do this. */
    data class NotPermitted(val message: String?) : CallFailure

    /** The package, component or user is not known to the system. */
    data class TargetNotFound(val message: String?) : CallFailure

    /** The system failed in another way. */
    data class SystemError(val message: String?) : CallFailure
}
