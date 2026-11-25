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

package com.merxury.blocker.core.privileged.root

/**
 * Whether Blocker remembers a root grant between process starts.
 *
 * An app cannot ask the root manager whether su will answer without a prompt. Root managers keep
 * an answer per app and give a stored "allow" without asking. A successful su start remembers
 * the grant. The installed-app database upgrade can also carry a grant forward from a root mode.
 */
interface RootGrantMemory {
    /** Whether a grant has been remembered. False when that cannot be read. */
    suspend fun isRemembered(): Boolean

    /** Records whether su gave a root process. A failed write is logged, not thrown. */
    suspend fun remember(granted: Boolean)
}
