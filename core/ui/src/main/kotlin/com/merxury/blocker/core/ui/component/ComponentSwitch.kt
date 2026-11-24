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

package com.merxury.blocker.core.ui.component

import com.merxury.blocker.core.model.component.Availability
import com.merxury.blocker.core.model.component.ComponentStatus
import com.merxury.blocker.core.model.component.ControlState
import com.merxury.blocker.core.model.component.IfwBlock
import com.merxury.blocker.core.model.component.PmSetting
import com.merxury.blocker.core.model.component.availability

/**
 * Whether the switch of [this] component shows on: off when anything known keeps it from running,
 * a PackageManager setting, an Intent Firewall rule or the app shipping it disabled, and on
 * otherwise. A conditional rule does not turn it off. A part of the state that could not be read
 * counts as its default: firewall rules that cannot be read, as without access or as the shell,
 * count as none, so the PackageManager setting alone decides; a setting that cannot be read
 * counts as the app's own default.
 */
val ComponentStatus.isSwitchOn: Boolean
    get() {
        val pm = state.pm.takeUnless { it == PmSetting.UNKNOWN } ?: PmSetting.DEFAULT
        val ifw = state.ifw.takeUnless { it == IfwBlock.UNKNOWN } ?: IfwBlock.NOT_BLOCKED
        return declaration.availability(ControlState(pm, ifw)) !is Availability.Blocked
    }
