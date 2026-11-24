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

package com.merxury.blocker.core.ui.profile

import com.merxury.blocker.core.model.system.DeviceUser
import com.merxury.blocker.core.model.system.UserKind

/** What a user or profile is called on screen. Each screen words it in its own context. */
sealed interface ProfileLabel {
    /** The device owner. */
    data object Personal : ProfileLabel

    /** A managed work profile. */
    data object Work : ProfileLabel

    /** Another user or profile, with the name the system gives it. */
    data class Named(val name: String) : ProfileLabel

    /** Another user or profile whose name cannot be read. */
    data class Numbered(val id: Int) : ProfileLabel
}

/** The label for [this] user: its kind for the owner and a work profile, otherwise its name. */
fun DeviceUser.profileLabel(): ProfileLabel = when (kind) {
    UserKind.OWNER -> ProfileLabel.Personal

    UserKind.WORK_PROFILE -> ProfileLabel.Work

    UserKind.SECONDARY, UserKind.OTHER_PROFILE -> name?.takeIf { it.isNotBlank() }?.let(ProfileLabel::Named)
        ?: ProfileLabel.Numbered(id.value)
}
