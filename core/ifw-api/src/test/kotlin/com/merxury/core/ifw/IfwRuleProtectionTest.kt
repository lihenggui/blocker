// Copyright 2026 Blocker
// SPDX-License-Identifier: Apache-2.0

package com.merxury.core.ifw

import com.google.common.truth.Truth.assertThat
import com.merxury.blocker.core.model.ifw.IfwFailure
import com.merxury.blocker.core.model.ifw.IfwResult
import com.merxury.blocker.core.testing.controller.FakeIfwFileSystem
import com.merxury.core.ifw.model.IfwFilter
import com.merxury.core.ifw.model.StringMatcher
import com.merxury.core.ifw.xml.IfwXmlDeserializer
import com.merxury.core.ifw.xml.IfwXmlSerializer
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

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
    fun givenIntentSelector_whenEditingAndRestoring_thenBothWritesExcludeOwnAndUnresolvedTargets() = runTest {
        val raw = """<rules><broadcast block="true">
            <intent-filter><action name="android.intent.action.BOOT_COMPLETED"/></intent-filter>
            </broadcast></rules>"""
        val rule = xml.deserialize(raw)
        assertThat(store.update("com.example") { rule }).isInstanceOf(IfwResult.Success::class.java)
        assertThat(store.restoreRuleFile("custom", raw)).isEqualTo(IfwResult.Success(true))

        val expected = IfwFilter.And(
            listOf(
                IfwFilter.ComponentPackage(StringMatcher.IsNull(false)),
                IfwFilter.Not(IfwFilter.ComponentPackage(StringMatcher.Equals(own))),
            ),
        )
        listOf("com.example", "custom").forEach { name ->
            val saved = xml.deserialize(requireNotNull(fs.fileContent(name))).rules.single()
            assertThat(saved.intentFilters).hasSize(1)
            assertThat(saved.filters).containsExactly(expected)
        }
    }

    @Test
    fun givenOwnComponentInRawWakeRules_whenWriting_thenTheManagedFileIsNeverCreated() {
        val directory = java.nio.file.Files.createTempDirectory("wake-rules").toFile()
        try {
            val result = runCatching { writeWakeRuleFile(own, WakeRuleFileKind.MANAGED, ownComponentBlock, directory) }

            assertThat(result.exceptionOrNull()).isInstanceOf(IllegalStateException::class.java)
            val file = File(directory, "${wakeRuleFileName(own, WakeRuleFileKind.MANAGED)}.xml")
            assertThat(file.exists()).isFalse()
        } finally {
            directory.deleteRecursively()
        }
    }

    private val ownComponentBlock = """<rules><activity block="true">
        <component-filter name="$own/.MainActivity" />
        </activity></rules>"""
}
