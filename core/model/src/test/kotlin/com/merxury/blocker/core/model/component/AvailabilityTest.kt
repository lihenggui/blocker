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

package com.merxury.blocker.core.model.component

import com.google.common.truth.Truth.assertThat
import com.merxury.blocker.core.model.ComponentType
import com.merxury.blocker.core.model.component.Availability.Blocked
import com.merxury.blocker.core.model.component.Availability.Conditional
import com.merxury.blocker.core.model.component.Availability.Runnable
import com.merxury.blocker.core.model.component.Availability.Unknown
import com.merxury.blocker.core.model.component.BlockReason.IFW_RULE
import com.merxury.blocker.core.model.component.BlockReason.MANIFEST_DEFAULT
import com.merxury.blocker.core.model.component.BlockReason.PM_SETTING
import org.junit.Assert.assertThrows
import org.junit.Test

class AvailabilityTest {

    private fun declaration(
        type: ComponentType = ComponentType.SERVICE,
        enabledInManifest: Boolean = true,
    ) = ComponentDeclaration(
        id = ComponentId("com.example", "com.example.Component"),
        type = type,
        exported = false,
        enabledInManifest = enabledInManifest,
    )

    @Test
    fun givenNothingBlocks_whenAvailability_thenRunnable() {
        val state = ControlState(PmSetting.DEFAULT, IfwBlock.NOT_BLOCKED)

        assertThat(declaration().availability(state)).isEqualTo(Runnable)
        assertThat(declaration().availability(state.copy(pm = PmSetting.ENABLED))).isEqualTo(Runnable)
    }

    @Test
    fun givenPmDisabled_whenAvailability_thenBlockedByPmSetting() {
        val state = ControlState(PmSetting.DISABLED, IfwBlock.NOT_BLOCKED)

        assertThat(declaration().availability(state)).isEqualTo(Blocked(setOf(PM_SETTING)))
    }

    @Test
    fun givenIfwBlocked_whenAvailability_thenBlockedByIfwRule() {
        val state = ControlState(PmSetting.DEFAULT, IfwBlock.BLOCKED)

        assertThat(declaration().availability(state)).isEqualTo(Blocked(setOf(IFW_RULE)))
    }

    @Test
    fun givenBothLayersBlock_whenAvailability_thenBothReasonsReported() {
        val state = ControlState(PmSetting.DISABLED, IfwBlock.BLOCKED)

        assertThat(declaration().availability(state)).isEqualTo(Blocked(setOf(PM_SETTING, IFW_RULE)))
    }

    @Test
    fun givenDisabledInManifestAndDefaultSetting_whenAvailability_thenBlockedByManifestDefault() {
        val state = ControlState(PmSetting.DEFAULT, IfwBlock.NOT_BLOCKED)

        assertThat(declaration(enabledInManifest = false).availability(state))
            .isEqualTo(Blocked(setOf(MANIFEST_DEFAULT)))
    }

    @Test
    fun givenDisabledInManifestButPmEnabled_whenAvailability_thenRunnable() {
        val state = ControlState(PmSetting.ENABLED, IfwBlock.NOT_BLOCKED)

        assertThat(declaration(enabledInManifest = false).availability(state)).isEqualTo(Runnable)
    }

    @Test
    fun givenUnreadFirewallAndNoKnownBlock_whenAvailability_thenUnknown() {
        val state = ControlState(PmSetting.ENABLED, IfwBlock.UNKNOWN)

        assertThat(declaration().availability(state)).isEqualTo(Unknown)
    }

    @Test
    fun givenUnreadPmSettingAndNoKnownBlock_whenAvailability_thenUnknown() {
        val state = ControlState(PmSetting.UNKNOWN, IfwBlock.NOT_BLOCKED)

        assertThat(declaration().availability(state)).isEqualTo(Unknown)
        assertThat(declaration(enabledInManifest = false).availability(state)).isEqualTo(Unknown)
        assertThat(declaration().availability(ControlState.UNKNOWN)).isEqualTo(Unknown)
    }

    @Test
    fun givenKnownBlockAndUnreadOtherLayer_whenAvailability_thenBlocked() {
        assertThat(declaration().availability(ControlState(PmSetting.DISABLED, IfwBlock.UNKNOWN)))
            .isEqualTo(Blocked(setOf(PM_SETTING)))
        assertThat(declaration().availability(ControlState(PmSetting.UNKNOWN, IfwBlock.BLOCKED)))
            .isEqualTo(Blocked(setOf(IFW_RULE)))
    }

    @Test
    fun givenConditionalFirewallRule_whenAvailability_thenConditional() {
        val state = ControlState(PmSetting.DEFAULT, IfwBlock.CONDITIONAL)

        assertThat(declaration().availability(state)).isEqualTo(Conditional)
    }

    @Test
    fun givenConditionalFirewallRuleAndPmDisabled_whenAvailability_thenBlocked() {
        val state = ControlState(PmSetting.DISABLED, IfwBlock.CONDITIONAL)

        assertThat(declaration().availability(state)).isEqualTo(Blocked(setOf(PM_SETTING)))
    }

    @Test
    fun givenConditionalFirewallRuleAndUnreadPmSetting_whenAvailability_thenUnknown() {
        val state = ControlState(PmSetting.UNKNOWN, IfwBlock.CONDITIONAL)

        assertThat(declaration().availability(state)).isEqualTo(Unknown)
    }

    @Test
    fun givenProvider_whenAvailability_thenFirewallStateIgnored() {
        val provider = declaration(type = ComponentType.PROVIDER)

        assertThat(provider.availability(ControlState(PmSetting.DEFAULT, IfwBlock.BLOCKED))).isEqualTo(Runnable)
        assertThat(provider.availability(ControlState(PmSetting.DEFAULT, IfwBlock.UNKNOWN))).isEqualTo(Runnable)
        assertThat(provider.availability(ControlState(PmSetting.DISABLED, IfwBlock.UNKNOWN)))
            .isEqualTo(Blocked(setOf(PM_SETTING)))
    }

    @Test
    fun whenCheckFirewallApplicability_thenOnlyProvidersExcluded() {
        assertThat(ComponentType.ACTIVITY.isFilteredByIntentFirewall).isTrue()
        assertThat(ComponentType.SERVICE.isFilteredByIntentFirewall).isTrue()
        assertThat(ComponentType.RECEIVER.isFilteredByIntentFirewall).isTrue()
        assertThat(ComponentType.PROVIDER.isFilteredByIntentFirewall).isFalse()
    }

    @Test
    fun givenNoReason_whenCreateBlocked_thenRejected() {
        assertThrows(IllegalArgumentException::class.java) { Blocked(emptySet()) }
    }
}
