// Copyright 2026 Blocker
// SPDX-License-Identifier: Apache-2.0

package com.merxury.core.ifw

import android.os.PersistableBundle
import com.google.common.truth.Truth.assertThat
import com.merxury.blocker.core.model.ifw.IfwFailure
import com.merxury.blocker.core.model.ifw.IfwResult
import com.merxury.blocker.core.testing.controller.FakeIfwFileSystem
import com.merxury.core.ifw.model.IfwIntentFilter
import com.merxury.core.ifw.model.IfwRules
import com.merxury.core.ifw.xml.IfwXmlDeserializer
import com.merxury.core.ifw.xml.IfwXmlSerializer
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], manifest = Config.NONE)
class IfwRuleProtectionTest {
    private val own = "com.merxury.blocker.debug"
    private val fs = FakeIfwFileSystem()
    private val xml = IfwXmlDeserializer()
    private val store = IntentFirewallStore(fs, IfwXmlSerializer(), xml, own)

    @Test
    fun givenDifferentFileName_whenEditorAddsAnOwnComponentBlock_thenTheStoreRefusesIt() = runTest {
        val rule = xml.deserialize(ownComponentBlock)
        val result = store.update("com.other") { rule }

        assertThat(result).isEqualTo(IfwResult.Failure(IfwFailure.ProtectedComponent))
        assertThat(fs.fileContent("com.other")).isNull()
    }

    @Test
    fun givenPrefixedXmlRuleSelectingBlocker_whenRestoring_thenFileIsRefused() = runTest {
        val content = """<rules xmlns:r="urn:rule"><r:activity block="true">
            <r:component-filter name="$own/.MainActivity"/>
            </r:activity></rules>"""
        val result = store.restoreRuleFile("custom", content)

        assertThat(result).isEqualTo(IfwResult.Failure(IfwFailure.ProtectedComponent))
        assertThat(fs.fileContent("custom")).isNull()
    }

    @Test
    fun givenIntentSelector_whenSavingInEditor_thenTheOriginalRuleIsWritten() = runTest {
        val raw = """<rules><broadcast block="true">
            <intent-filter><action name="android.intent.action.BOOT_COMPLETED"/></intent-filter>
            </broadcast></rules>"""
        val rule = xml.deserialize(raw)

        assertThat(store.update("com.example") { rule }).isEqualTo(IfwResult.Success(rule))
        assertThat(fs.fileContent("com.example")).isEqualTo(IfwXmlSerializer().serialize(rule))
    }

    @Test
    fun givenBroadBlockAndLogOnlySelfSelector_whenRestoring_thenXmlIsWrittenVerbatim() = runTest {
        val raw = """
            <?xml version="1.0" encoding="utf-8"?>
            <rules>
              <!-- Keep this comment and the original formatting. -->
              <broadcast block="true">
                <intent-filter><action name="android.intent.action.BOOT_COMPLETED" /></intent-filter>
                <action />
              </broadcast>
              <activity block="false" log="true"><component-filter name="$own/.MainActivity" /></activity>
              <service block="true"><component-filter name="$own.other/.Service" /></service>
            </rules>
        """.trimIndent().replace("\n", "\r\n") + "\r\n"

        assertThat(store.restoreRuleFile("custom", raw)).isEqualTo(IfwResult.Success(true))
        assertThat(fs.fileContent("custom")).isEqualTo(raw)
    }

    @Test
    fun givenExistingOwnComponentBlock_whenRemovingIt_thenTheSafeRulesAreSaved() = runTest {
        fs.putFile("custom", ownComponentBlock)
        val safeRules = xml.deserialize(
            """<rules><service block="true">
            <component-filter name="com.other/.Service" />
            </service></rules>""",
        )

        assertThat(store.update("custom") { safeRules }).isEqualTo(IfwResult.Success(safeRules))
        assertThat(store.rules("custom")).isEqualTo(IfwResult.Success(safeRules))
    }

    @Test
    fun givenSeveralExistingOwnComponentBlocks_whenRemovingOne_thenTheOtherRemains() = runTest {
        val rules = xml.deserialize("""<rules>$ownActivityRule$ownServiceRule</rules>""")
        fs.putFile("custom", IfwXmlSerializer().serialize(rules))
        val remaining = rules.copy(rules = rules.rules.drop(1))

        assertThat(store.update("custom") { it.copy(rules = it.rules.drop(1)) }).isEqualTo(IfwResult.Success(remaining))
        assertThat(store.rules("custom")).isEqualTo(IfwResult.Success(remaining))
    }

    @Test
    fun givenAnExistingOwnBlockAndAnotherRule_whenEditingTheOtherRule_thenBothRulesAreSaved() = runTest {
        val otherRule = """<service block="true"><component-filter name="com.other/.Service" /></service>"""
        fs.putFile("custom", "<rules>$ownActivityRule$otherRule</rules>")
        val expected = xml.deserialize("<rules>$ownActivityRule${otherRule.replace("true", "false")}</rules>")

        val result = store.update("custom") { it.copy(rules = listOf(it.rules.first(), it.rules.last().copy(block = false))) }

        assertThat(result).isEqualTo(IfwResult.Success(expected))
        assertThat(store.rules("custom")).isEqualTo(IfwResult.Success(expected))
    }

    @Test
    fun givenAnExistingOwnBlock_whenAddingAnother_thenTheFileIsKept() = runTest {
        fs.putFile("custom", ownComponentBlock)
        val added = xml.deserialize("<rules>$ownServiceRule</rules>").rules

        val result = store.update("custom") { it.copy(rules = it.rules + added) }

        assertThat(result).isEqualTo(IfwResult.Failure(IfwFailure.ProtectedComponent))
        assertThat(fs.fileContent("custom")).isEqualTo(ownComponentBlock)
    }

    @Test
    fun givenAnExistingOwnBlockWithIntentExtras_whenSavingTheSameRuleWithAnotherRule_thenBothAreSaved() = runTest {
        val extras = PersistableBundle().apply { putString("key", "value") }
        val original = xml.deserialize(ownComponentBlock).rules.single()
        val ownWithExtras = original.copy(intentFilters = listOf(IfwIntentFilter(actions = listOf("com.example.OPEN"), extras = extras)))
        val serializer = IfwXmlSerializer()
        val content = serializer.serialize(IfwRules(listOf(ownWithExtras)))
        fs.putFile("custom", content)
        val edited = IfwRules(listOf(ownWithExtras, original.copy(block = false)))

        assertThat(store.update("custom") { edited }).isEqualTo(IfwResult.Success(edited))
        assertThat(fs.fileContent("custom")).isEqualTo(serializer.serialize(edited))
    }

    private val ownActivityRule = """<activity block="true">
        <component-filter name="$own/.MainActivity" />
        </activity>"""
    private val ownServiceRule = """<service block="true"><component-filter name="$own/.SyncService" /></service>"""
    private val ownComponentBlock = "<rules>$ownActivityRule</rules>"
}
