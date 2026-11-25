/*
 * Copyright 2025 Blocker
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

package com.merxury.blocker.core.privileged.shizuku

sealed interface RegisterShizukuResult {
    /** Permission is granted. [uid] is the Shizuku server's uid, or -1 if it could not be read. */
    data class Granted(val uid: Int) : RegisterShizukuResult

    /** The user denied the permission in the dialog or earlier with "deny and don't ask again". */
    data object Denied : RegisterShizukuResult

    /** No Shizuku binder arrived in time, so neither Shizuku nor Sui is running. */
    data object NotRunning : RegisterShizukuResult

    /** The running Shizuku server is older than v11, which is not supported. */
    data object UnsupportedVersion : RegisterShizukuResult

    /** The permission dialog was not answered in time. */
    data object TimedOut : RegisterShizukuResult

    /** A Shizuku call threw. */
    data class Failed(val cause: Throwable) : RegisterShizukuResult
}
