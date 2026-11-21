// Copyright 2026 Blocker
// SPDX-License-Identifier: Apache-2.0

package com.merxury.core.ifw

import com.merxury.core.ifw.model.IfwFilter
import com.merxury.core.ifw.model.SenderType
import com.merxury.core.ifw.model.StringMatcher
import com.merxury.core.ifw.xml.IfwXmlDeserializer
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], manifest = Config.NONE)
class ManagedWakeRulesTest {
    @Test fun sameUidRulesExcludeSystemAndCheckTheActionFirst() {
        val path = WakeServiceFilter("same.app", "same.app", "same.app.Service", "sdk.WAKE")
        val xml = requireNotNull(renderWakeRules(listOf(path)))
        val filters = IfwXmlDeserializer().deserialize(xml).rules.single().filters
        assertEquals(
            listOf(
                IfwFilter.ComponentFilter("same.app/same.app.Service"),
                IfwFilter.Action(StringMatcher.Equals("sdk.WAKE")),
                IfwFilter.Sender(SenderType.USER_ID),
                IfwFilter.Not(IfwFilter.Sender(SenderType.SYSTEM)),
            ),
            filters,
        )
    }

    @Test fun managedFileContainsOnlyExactSenderComponentAndAction() {
        val path = WakeServiceFilter("sender.app", "target.app", "target.app.Remote", "sdk.WAKE")
        val xml = requireNotNull(renderWakeRules(listOf(path, path, path.copy(action = ""))))
        val rules = IfwXmlDeserializer().deserialize(xml).rules
        assertEquals(2, rules.size)
        for (rule in rules) {
            assertTrue(rule.block && !rule.log && rule.intentFilters.isEmpty())
            assertEquals(3, rule.filters.size)
            assertTrue(IfwFilter.ComponentFilter("target.app/target.app.Remote") in rule.filters)
            assertTrue(IfwFilter.SenderPackage("sender.app") in rule.filters)
        }
        assertTrue(rules.any { IfwFilter.Action(StringMatcher.IsNull(true)) in it.filters })
        assertTrue(rules.any { IfwFilter.Action(StringMatcher.Equals("sdk.WAKE")) in it.filters })
        assertNull(renderWakeRules(emptyList()))
    }
}
