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

package com.merxury.blocker.core.model.ifw

/** The outcome of reading or changing Intent Firewall rules. */
sealed interface IfwResult<out T> {
    data class Success<T>(val value: T) : IfwResult<T>

    data class Failure(val reason: IfwFailure) : IfwResult<Nothing>
}

/** Why Intent Firewall rules could not be read or changed. */
sealed interface IfwFailure {
    /**
     * The rule directory cannot be reached: no privileged channel is available, or its identity,
     * such as Shizuku running as the shell, cannot write system files.
     */
    data object NoAccess : IfwFailure

    /**
     * The package's rule file exists but cannot be read or parsed, for example because it holds
     * an element Blocker does not know. Such a file is never overwritten.
     */
    data class Unreadable(val packageName: String) : IfwFailure

    /**
     * Writing or deleting the package's rule file failed. A file is replaced atomically, so it
     * holds its old content or, when only the answer was lost, the new one; never a mix.
     */
    data class WriteFailed(
        val packageName: String,
        val message: String?,
    ) : IfwFailure
}
