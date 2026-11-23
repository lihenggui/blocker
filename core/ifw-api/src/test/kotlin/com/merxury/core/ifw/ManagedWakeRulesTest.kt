// Copyright 2026 Blocker
// SPDX-License-Identifier: Apache-2.0

package com.merxury.core.ifw

import com.merxury.core.ifw.model.IfwFilter
import com.merxury.core.ifw.model.SenderType
import com.merxury.core.ifw.model.StringMatcher
import com.merxury.core.ifw.xml.IfwXmlDeserializer
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], manifest = Config.NONE)
class ManagedWakeRulesTest {
    @get:Rule val folder = TemporaryFolder()

    @Test fun givenSharedManagedFile_whenResettingOneTarget_thenOtherPackagesKeepTheirRules() {
        val first = WakeServiceFilter("a.pkg", "a.pkg", "a.pkg.Wake", "")
        val second = WakeServiceFilter("b.pkg", "b.pkg", "b.pkg.Wake", "")
        val both = requireNotNull(renderWakeRules(listOf(first, second)))
        assertEquals(renderWakeRules(listOf(second)), withoutWakePackage(both, first.target))
        assertEquals(both, withoutWakePackage(both, "missing.pkg"))
        assertNull(withoutWakePackage(requireNotNull(renderWakeRules(listOf(first))), first.target))
    }

    @Test fun givenSameUidPath_whenRenderingRules_thenActionPrecedesSenderAndSystemIsExcluded() {
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

    @Test fun givenPeerPaths_whenRenderingRules_thenSenderComponentAndActionMustMatch() {
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

    @Test fun givenEachKindAndOwner_whenNamingFile_thenProductionNamesKeptAndOtherOwnersSeparate() {
        assertEquals("blocker-managed-wake", wakeRuleFileName("com.merxury.blocker", WakeRuleFileKind.MANAGED))
        assertEquals("blocker-wake-capability-probe", wakeRuleFileName("com.merxury.blocker", WakeRuleFileKind.PROBE))
        assertEquals("blocker-managed-wake-com.merxury.blocker.debug", wakeRuleFileName("com.merxury.blocker.debug", WakeRuleFileKind.MANAGED))
        WakeRuleFileKind.entries.forEach { assertTrue(isManagedWakeName(wakeRuleFileName("com.other", it))) }
        listOf("", "blocker", "../a.b", "a/b.c", "a..b").forEach { owner ->
            assertFailsWith<IllegalArgumentException>(owner) { wakeRuleFileName(owner, WakeRuleFileKind.MANAGED) }
        }
    }

    @Test fun givenFileHoldsSameRules_whenWrittenAgain_thenUntouched() {
        val directory = folder.newFolder()
        val xml = requireNotNull(renderWakeRules(listOf(WakeServiceFilter("a.pkg", "a.pkg", "a.pkg.Wake", ""))))
        val file = File(directory, "blocker-managed-wake.xml").apply {
            writeText(xml)
            setLastModified(1_000)
        }

        writeWakeRuleFile("com.merxury.blocker", WakeRuleFileKind.MANAGED, xml, directory)

        assertEquals(1_000, file.lastModified())
        assertEquals(xml, file.readText())
    }

    @Test fun givenFile_whenDeletedTwice_thenGoneAndSecondDeleteChangesNothing() {
        val directory = folder.newFolder()
        val probe = File(directory, "blocker-wake-capability-probe-com.other.xml").apply { writeText("<rules />") }
        val managed = File(directory, "blocker-managed-wake-com.other.xml").apply { writeText("<rules />") }

        writeWakeRuleFile("com.other", WakeRuleFileKind.PROBE, null, directory)
        writeWakeRuleFile("com.other", WakeRuleFileKind.PROBE, null, directory)

        assertFalse(probe.exists())
        assertTrue(managed.exists())
    }
}
