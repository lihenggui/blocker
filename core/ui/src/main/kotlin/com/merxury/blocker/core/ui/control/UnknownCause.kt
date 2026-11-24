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

package com.merxury.blocker.core.ui.control

import com.merxury.blocker.core.model.component.ComponentStatus
import com.merxury.blocker.core.model.component.PmSetting
import com.merxury.blocker.core.ui.R.string

/** Why the state of a component cannot be told. */
enum class UnknownCause {
    /** Its PackageManager setting for the user could not be read. */
    PM_UNREADABLE,

    /** The current mode cannot read Intent Firewall rules. */
    NO_FIREWALL_ACCESS,

    /** The app's Intent Firewall rule file could not be read, for example because it is damaged. */
    RULES_UNREADABLE,
}

/**
 * Why [this] component's state is unknown, for a component whose availability is unknown.
 *
 * @param firewallReadable whether the current mode can read Intent Firewall rules at all
 */
fun ComponentStatus.unknownCause(firewallReadable: Boolean): UnknownCause = when {
    state.pm == PmSetting.UNKNOWN -> UnknownCause.PM_UNREADABLE
    !firewallReadable -> UnknownCause.NO_FIREWALL_ACCESS
    else -> UnknownCause.RULES_UNREADABLE
}

/** What the user is told about [this] cause and what to do about it. */
fun UnknownCause.explanation(): Int = when (this) {
    UnknownCause.PM_UNREADABLE -> string.core_ui_unknown_pm
    UnknownCause.NO_FIREWALL_ACCESS -> string.core_ui_unknown_no_firewall_access
    UnknownCause.RULES_UNREADABLE -> string.core_ui_unknown_rules_unreadable
}
