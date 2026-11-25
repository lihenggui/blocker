// Copyright 2026 Blocker
// SPDX-License-Identifier: Apache-2.0

package com.merxury.core.ifw

import com.google.common.truth.Truth.assertThat
import com.merxury.core.ifw.model.IfwComponentType
import com.merxury.core.ifw.model.IfwFilter
import com.merxury.core.ifw.model.IfwRule
import com.merxury.core.ifw.model.SenderType
import com.merxury.core.ifw.xml.IfwXmlDeserializer
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], manifest = Config.NONE)
class UserWakeRulesTest {
    private val service = UserWakeRule("peer.pkg", "target.pkg", "target.pkg.Remote")
    private val ownService = service.copy(sender = "target.pkg")

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

    @Test fun givenOwnService_whenRendering_thenComponentAndOwnUidMatch() {
        val component = IfwFilter.ComponentFilter("target.pkg/target.pkg.Remote")
        val own = listOf(IfwFilter.Sender(SenderType.USER_ID), IfwFilter.Not(IfwFilter.Sender(SenderType.SYSTEM)))
        assertThat(parse(ownService)).isEqualTo(
            IfwRule(IfwComponentType.SERVICE, log = false, filters = listOf(component) + own),
        )
    }

    @Test fun givenRepeatedAndShuffledRules_whenRendering_thenXmlIsStableAndRulesAreUnique() {
        val original = listOf(service, ownService)
        val xml = requireNotNull(renderUserWakeRules(original))
        assertThat(renderUserWakeRules(original.reversed() + service + ownService)).isEqualTo(xml)
        assertThat(IfwXmlDeserializer().deserialize(xml).rules).containsExactly(parse(service), parse(ownService)).inOrder()
        assertThat(renderUserWakeRules(emptyList())).isNull()
    }

    @Test fun givenRulesForDifferentTargets_whenRemovingPackage_thenOnlyRulesForThatTargetAreRemoved() {
        val other = UserWakeRule("target.pkg", "other.pkg", "other.pkg.Remote")
        val xml = requireNotNull(renderUserWakeRules(listOf(service, ownService, other)))
        assertThat(withoutWakePackage(xml, "target.pkg")).isEqualTo(renderUserWakeRules(listOf(other)))
        assertThat(withoutWakePackage(xml, "peer.pkg")).isEqualTo(xml)
        assertThat(withoutWakePackage(requireNotNull(renderUserWakeRules(listOf(service))), "target.pkg")).isNull()
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
        assertThrows(IllegalArgumentException::class.java) { service.copy(component = ".Remote") }
    }

    private fun parse(rule: UserWakeRule): IfwRule = IfwXmlDeserializer().deserialize(requireNotNull(renderUserWakeRules(listOf(rule)))).rules.single()
}
