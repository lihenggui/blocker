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

import com.merxury.blocker.core.model.component.UserId

/**
 * A user on the device as the system describes it.
 *
 * @property name the name shown in system settings, or null when it cannot be read
 * @property parent the user a profile belongs to; null for a full user, and null for a profile
 * whose owner cannot be read
 * @property running whether the user is started; a stopped user's apps can still be read and
 * changed with root, and the change applies when the user next starts
 * @property unlocked whether credential-encrypted storage is unlocked, or null when unknown
 */
data class DeviceUser(
    val id: UserId,
    val name: String?,
    val kind: UserKind,
    val parent: UserId?,
    val running: Boolean,
    val unlocked: Boolean? = null,
)

enum class UserKind {
    /** The first user, id 0. */
    OWNER,

    /** Another full user with its own home screen. */
    SECONDARY,

    /** A managed profile of a full user. */
    WORK_PROFILE,

    /** Any other profile, such as a cloned-apps profile or a private space. */
    OTHER_PROFILE,
}
