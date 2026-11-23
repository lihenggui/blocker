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

package com.merxury.blocker.core.privileged

import android.content.pm.PackageManager.NameNotFoundException
import android.os.DeadObjectException
import be.mygod.librootkotlinx.NoShellException
import com.merxury.blocker.core.exception.RootUnavailableException
import com.merxury.blocker.core.model.privileged.CallFailure
import com.merxury.blocker.core.model.privileged.PrivilegedIdentity
import com.merxury.blocker.core.model.privileged.UnavailableReason

/** A transport found its channel gone while running a call. */
internal class ChannelLostException(
    val reason: UnavailableReason,
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause)

/**
 * The one mapping from what a privileged call threw to a [CallFailure], used by both transports
 * and on both sides of the root process. Never pass a `CancellationException`; it must propagate.
 */
internal fun Throwable.toCallFailure(): CallFailure = when {
    this is PrivilegedProcessUnavailableException -> CallFailure.ChannelUnavailable

    this is SecurityException -> CallFailure.NotPermitted(message)

    // The framework rejects unknown packages, components and users with IllegalArgumentException.
    this is NameNotFoundException || this is IllegalArgumentException -> CallFailure.TargetNotFound(message)

    lostReason() != null -> CallFailure.ChannelUnavailable

    else -> CallFailure.SystemError(toString())
}

// Shizuku wraps a dead binder in a RuntimeException, librootkotlinx in a RemoteException.
private fun Throwable.causes() = generateSequence(this) { it.cause }.take(MAX_CAUSES)

/** Why the channel counts as gone after this was thrown, or null when it is not a channel loss. */
internal fun Throwable.lostReason(): UnavailableReason? = causes().firstNotNullOfOrNull {
    when (it) {
        is ChannelLostException -> it.reason
        is NoShellException, is RootUnavailableException -> UnavailableReason.DENIED
        is DeadObjectException -> UnavailableReason.FAILED
        else -> null
    }
}

private const val MAX_CAUSES = 8

private const val ROOT_UID = 0
private const val SYSTEM_UID = 1000
private const val SHELL_UID = 2000

/** The identity a privileged process with [uid] has, or null for any other uid. */
internal fun identityOfUid(uid: Int): PrivilegedIdentity? = when (uid) {
    ROOT_UID -> PrivilegedIdentity.ROOT
    SYSTEM_UID -> PrivilegedIdentity.SYSTEM
    SHELL_UID -> PrivilegedIdentity.SHELL
    else -> null
}
