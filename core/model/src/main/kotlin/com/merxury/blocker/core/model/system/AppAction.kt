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

/** What the user can do to a whole app from a list of apps. */
enum class AppAction {
    ENABLE,
    DISABLE,
    FORCE_STOP,
    CLEAR_CACHE,
    CLEAR_DATA,
    UNINSTALL,
}

/** Why an [AppAction] failed, as the user is told. */
sealed interface AppActionFailure {
    /** Blocker has no root or Shizuku access. */
    data object NoAccess : AppActionFailure

    /** Shizuku runs as the shell, which may not do this; root may. */
    data object NeedsRoot : AppActionFailure

    /** The system refused it for the identity Blocker's access runs as. */
    data object NotPermitted : AppActionFailure

    /** The app is not installed for the user. */
    data object NotFound : AppActionFailure

    /** The system failed, with its message when it gave one. */
    data class SystemError(val message: String?) : AppActionFailure
}
