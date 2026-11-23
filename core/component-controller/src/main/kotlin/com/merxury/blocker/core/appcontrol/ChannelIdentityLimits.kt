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

package com.merxury.blocker.core.appcontrol

import com.merxury.blocker.core.model.component.UserId
import com.merxury.blocker.core.model.privileged.CallFailure
import com.merxury.blocker.core.model.privileged.CallResult
import com.merxury.blocker.core.model.privileged.ChannelState
import com.merxury.blocker.core.model.privileged.PrivilegedIdentity
import com.merxury.blocker.core.privileged.PrivilegedChannel

/*
 * Operations the system does not refuse for an identity but silently does not do, so the
 * refusal has to be reported here, before the call.
 */

/**
 * Why the identity the channel runs as cannot clear an app's cache, or null when it can. The
 * shell holds DELETE_CACHE_FILES but never INTERNAL_DELETE_CACHE_FILES (Android 7 to 16), and the
 * system drops such a request without calling back. Root passes every permission check, and the
 * system uid holds INTERNAL_DELETE_CACHE_FILES through the "android" package; the latter is read
 * from the platform source and not verified on a device.
 */
internal suspend fun PrivilegedChannel.cacheClearingRefusal(): CallFailure? = when (identity()) {
    null -> CallFailure.ChannelUnavailable
    PrivilegedIdentity.SHELL -> CallFailure.NotPermitted("The shell may not clear the cache of other apps")
    PrivilegedIdentity.ROOT, PrivilegedIdentity.SYSTEM -> null
}

/**
 * Why the identity the channel runs as cannot list running processes and services of [user], or
 * null when it can. The activity manager lists other users only to a uid that PackageManager
 * grants INTERACT_ACROSS_USERS_FULL; the shell has it, but uid 0 has no package and so no grant,
 * and the system answers root with the processes and services of user 0 alone. The system uid
 * holds it through the "android" package, so it lists every user like the shell; that is read
 * from the platform source and not verified on a device.
 */
internal suspend fun PrivilegedChannel.runningListRefusal(user: UserId): CallFailure? = when (identity()) {
    null -> CallFailure.ChannelUnavailable
    PrivilegedIdentity.SHELL, PrivilegedIdentity.SYSTEM -> null
    PrivilegedIdentity.ROOT -> if (user == UserId.SYSTEM) null else CallFailure.NotPermitted("Root sees running processes of user 0 only")
}

/** The identity calls run as, connecting first when needed; null when no channel can be had. */
private suspend fun PrivilegedChannel.identity(): PrivilegedIdentity? {
    val current = state.value.takeIf { it is ChannelState.Ready } ?: connect()
    return (current as? ChannelState.Ready)?.identity
}

internal inline fun <R, T> CallResult<R>.map(transform: (R) -> T): CallResult<T> = when (this) {
    is CallResult.Success -> CallResult.Success(transform(value))
    is CallResult.Failure -> this
}
