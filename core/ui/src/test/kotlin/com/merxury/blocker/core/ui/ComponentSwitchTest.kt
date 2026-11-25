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

package com.merxury.blocker.core.ui

import com.google.common.truth.Truth.assertWithMessage
import com.merxury.blocker.core.model.ComponentType
import com.merxury.blocker.core.model.component.ComponentDeclaration
import com.merxury.blocker.core.model.component.ComponentId
import com.merxury.blocker.core.model.component.ComponentStatus
import com.merxury.blocker.core.model.component.ControlState
import com.merxury.blocker.core.model.component.IfwBlock
import com.merxury.blocker.core.model.component.IfwBlock.BLOCKED
import com.merxury.blocker.core.model.component.IfwBlock.CONDITIONAL
import com.merxury.blocker.core.model.component.IfwBlock.NOT_BLOCKED
import com.merxury.blocker.core.model.component.PmSetting
import com.merxury.blocker.core.model.component.PmSetting.DEFAULT
import com.merxury.blocker.core.model.component.PmSetting.DISABLED
import com.merxury.blocker.core.model.component.PmSetting.ENABLED
import org.junit.Test

/** What a component's switch shows for every state the cache can hold. */
class ComponentSwitchTest {
    private data class Case(
        val type: ComponentType,
        val enabledInManifest: Boolean,
        val pm: PmSetting,
        val ifw: IfwBlock,
        val on: Boolean,
    )

    private val receiver = ComponentType.RECEIVER
    private val provider = ComponentType.PROVIDER
    private val unknownPm = PmSetting.UNKNOWN
    private val unknownIfw = IfwBlock.UNKNOWN

    private val cases = listOf(
        // Everything known: off when any layer or the manifest keeps it from running.
        Case(receiver, enabledInManifest = true, DEFAULT, NOT_BLOCKED, on = true),
        Case(receiver, enabledInManifest = true, ENABLED, NOT_BLOCKED, on = true),
        Case(receiver, enabledInManifest = true, DISABLED, NOT_BLOCKED, on = false),
        Case(receiver, enabledInManifest = true, DEFAULT, BLOCKED, on = false),
        Case(receiver, enabledInManifest = true, ENABLED, BLOCKED, on = false),
        Case(receiver, enabledInManifest = true, DISABLED, BLOCKED, on = false),
        Case(receiver, enabledInManifest = false, DEFAULT, NOT_BLOCKED, on = false),
        Case(receiver, enabledInManifest = false, ENABLED, NOT_BLOCKED, on = true),
        Case(receiver, enabledInManifest = false, ENABLED, BLOCKED, on = false),
        // A conditional rule does not turn the switch off.
        Case(receiver, enabledInManifest = true, DEFAULT, CONDITIONAL, on = true),
        Case(receiver, enabledInManifest = true, DISABLED, CONDITIONAL, on = false),
        // Firewall rules that cannot be read count as none: the PackageManager setting decides.
        Case(receiver, enabledInManifest = true, DEFAULT, unknownIfw, on = true),
        Case(receiver, enabledInManifest = true, ENABLED, unknownIfw, on = true),
        Case(receiver, enabledInManifest = true, DISABLED, unknownIfw, on = false),
        Case(receiver, enabledInManifest = false, DEFAULT, unknownIfw, on = false),
        // A setting that cannot be read counts as the app's default.
        Case(receiver, enabledInManifest = true, unknownPm, NOT_BLOCKED, on = true),
        Case(receiver, enabledInManifest = false, unknownPm, NOT_BLOCKED, on = false),
        Case(receiver, enabledInManifest = true, unknownPm, BLOCKED, on = false),
        // Nothing known: the manifest decides.
        Case(receiver, enabledInManifest = true, unknownPm, unknownIfw, on = true),
        Case(receiver, enabledInManifest = false, unknownPm, unknownIfw, on = false),
        // The firewall does not filter providers, so a rule naming one changes nothing.
        Case(provider, enabledInManifest = true, DEFAULT, BLOCKED, on = true),
        Case(provider, enabledInManifest = true, DISABLED, NOT_BLOCKED, on = false),
        Case(provider, enabledInManifest = true, unknownPm, unknownIfw, on = true),
    )

    @Test
    fun givenEveryKnownAndUnknownState_whenShown_thenTheSwitchIsOnExactlyWhenNothingKnownBlocks() {
        cases.forEach { case ->
            val status = ComponentStatus(
                declaration = ComponentDeclaration(ComponentId("com.a", "com.a.Component"), case.type, exported = false, enabledInManifest = case.enabledInManifest),
                state = ControlState(case.pm, case.ifw),
            )
            assertWithMessage(case.toString()).that(status.isSwitchOn).isEqualTo(case.on)
        }
    }
}
