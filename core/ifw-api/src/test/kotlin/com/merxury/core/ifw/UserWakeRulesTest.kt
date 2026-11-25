// Copyright 2026 Blocker
// SPDX-License-Identifier: Apache-2.0

package com.merxury.core.ifw

import com.google.common.truth.Truth.assertThat
import com.merxury.core.ifw.model.IfwComponentType
import com.merxury.core.ifw.model.IfwFilter
import com.merxury.core.ifw.model.IfwIntentFilter
import com.merxury.core.ifw.model.IfwRule
import com.merxury.core.ifw.model.SenderType
import com.merxury.core.ifw.model.StringMatcher
import com.merxury.core.ifw.xml.IfwXmlDeserializer
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], manifest = Config.NONE)
class UserWakeRulesTest {
    private val service = UserWakeRule(UserWakeRule.Type.SERVICE, "peer.pkg", "target.pkg", "target.pkg.Remote", "sdk.WAKE")
    private val receiver = UserWakeRule(UserWakeRule.Type.BROADCAST, "target.pkg", "target.pkg", "target.pkg.Receiver", "sdk.EVENT")
    private val action = receiver.copy(sender = null, component = null)

    @Test fun givenService_whenRendering_thenComponentAndPeerMatchWithoutAnActionCondition() {
        assertThat(parse(service)).isEqualTo(
            IfwRule(
                IfwComponentType.SERVICE,
                log = false,
                filters = listOf(
                    IfwFilter.ComponentFilter("target.pkg/target.pkg.Remote"),
                    IfwFilter.SenderPackage("peer.pkg"),
                ),
            ),
        )
    }

    @Test fun givenReceiverWithAndWithoutAction_whenRendering_thenComponentAndOwnUidMatch() {
        val component = IfwFilter.ComponentFilter("target.pkg/target.pkg.Receiver")
        val own = listOf(IfwFilter.Sender(SenderType.USER_ID), IfwFilter.Not(IfwFilter.Sender(SenderType.SYSTEM)))
        assertThat(parse(receiver)).isEqualTo(
            IfwRule(IfwComponentType.BROADCAST, log = false, filters = listOf(component, IfwFilter.Action(StringMatcher.Equals("sdk.EVENT"))) + own),
        )
        assertThat(parse(receiver.copy(action = null))).isEqualTo(
            IfwRule(IfwComponentType.BROADCAST, log = false, filters = listOf(component) + own),
        )
    }

    @Test fun givenBroadcastAction_whenRendering_thenIntentFilterIndexesItAndComponentPackageLimitsIt() {
        val target = IfwFilter.ComponentPackage(StringMatcher.Equals("target.pkg"))
        assertThat(parse(action)).isEqualTo(
            IfwRule(IfwComponentType.BROADCAST, log = false, intentFilters = listOf(IfwIntentFilter(actions = listOf("sdk.EVENT"))), filters = listOf(target)),
        )
        assertThat(parse(action.copy(sender = "peer.pkg")).filters).containsExactly(target, IfwFilter.SenderPackage("peer.pkg")).inOrder()
    }

    @Test fun givenRepeatedAndShuffledRules_whenRendering_thenXmlIsStableAndRulesAreUnique() {
        val original = listOf(service, receiver, action)
        val xml = requireNotNull(renderUserWakeRules(original))
        assertThat(renderUserWakeRules(original.reversed() + service.copy(action = "other.ACTION") + receiver)).isEqualTo(xml)
        assertThat(IfwXmlDeserializer().deserialize(xml).rules).containsExactly(parse(action), parse(receiver), parse(service)).inOrder()
        assertThat(renderUserWakeRules(emptyList())).isNull()
    }

    @Test fun givenNamedAndActionRules_whenRemovingPackage_thenBothShapesAreRemovedForOnlyThatTarget() {
        val other = action.copy(target = "other.pkg", sender = "target.pkg")
        val xml = requireNotNull(renderUserWakeRules(listOf(action, service, receiver, other)))
        assertThat(withoutWakePackage(xml, "target.pkg")).isEqualTo(renderUserWakeRules(listOf(other)))
        assertThat(withoutWakePackage(xml, "peer.pkg")).isEqualTo(xml)
        assertThat(withoutWakePackage(requireNotNull(renderUserWakeRules(listOf(action))), "target.pkg")).isNull()
    }

    @Test fun givenBothInstallations_whenNamingUserFile_thenEachNameIsReserved() {
        val production = wakeRuleFileName("com.merxury.blocker", WakeRuleFileKind.USER)
        val debug = wakeRuleFileName("com.merxury.blocker.debug", WakeRuleFileKind.USER)
        assertThat(production).isEqualTo("blocker-user-wake")
        assertThat(debug).isEqualTo("blocker-user-wake-com.merxury.blocker.debug")
        assertThat(isManagedWakeName(production)).isTrue()
        assertThat(isManagedWakeName(debug)).isTrue()
    }

    @Test fun givenInvalidRule_whenConstructing_thenItIsRejected() {
        assertThrows(IllegalArgumentException::class.java) { service.copy(target = "../bad") }
        assertThrows(IllegalArgumentException::class.java) { service.copy(sender = "invalid") }
        assertThrows(IllegalArgumentException::class.java) { service.copy(component = null) }
        assertThrows(IllegalArgumentException::class.java) { action.copy(action = null) }
    }

    private fun parse(rule: UserWakeRule): IfwRule = IfwXmlDeserializer().deserialize(requireNotNull(renderUserWakeRules(listOf(rule)))).rules.single()
}
