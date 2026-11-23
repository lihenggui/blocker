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

/**
 * The identity system calls run as when they go through the privileged channel. What the
 * channel may do follows from it.
 */
enum class PrivilegedIdentity {
    /** uid 0, from su or Shizuku started with root. */
    ROOT,

    /** uid 2000, from Shizuku started through adb. */
    SHELL,
    ;

    /**
     * Whether the PackageManager setting of a single component can be changed. The shell may
     * change whole apps but not components.
     */
    val canChangeComponents: Boolean get() = this == ROOT

    /** Whether files under `/data/system`, such as Intent Firewall rules, can be written. */
    val canWriteSystemFiles: Boolean get() = this == ROOT
}

/** Whether the privileged channel can be used right now. */
sealed interface ChannelState {
    /** Calls run as [identity]. */
    data class Ready(val identity: PrivilegedIdentity) : ChannelState

    /** No call can run; [reason] says why. */
    data class Unavailable(val reason: UnavailableReason) : ChannelState
}

/** Why the privileged channel cannot be used. */
enum class UnavailableReason {
    /** Nothing has tried to acquire the channel yet. */
    NOT_CHECKED,

    /** Shizuku or Sui is not running. */
    NOT_RUNNING,

    /** The user refused the su or Shizuku request, or root is not installed. */
    DENIED,

    /** The running Shizuku is too old. */
    UNSUPPORTED_VERSION,

    /** The permission request was not answered in time. */
    TIMED_OUT,

    /** Acquiring failed for another reason, or the channel died while in use. */
    FAILED,
}
