// Copyright 2026 Blocker
// SPDX-License-Identifier: Apache-2.0

package com.merxury.blocker.core.rule.work

import com.google.common.truth.Truth.assertThat
import com.merxury.blocker.core.rule.backup.IfwRuleMerge
import com.merxury.core.ifw.model.IfwComponentType
import com.merxury.core.ifw.model.IfwFilter
import com.merxury.core.ifw.model.IfwRule
import com.merxury.core.ifw.model.IfwRules
import com.merxury.core.ifw.model.SenderType
import com.merxury.core.ifw.model.StringMatcher
import com.merxury.core.ifw.xml.IfwXmlDeserializer
import com.merxury.core.ifw.xml.IfwXmlSerializer
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Pins how an IFW backup file, `ifw/<package>.xml`, which ExportIfwRulesWorker copies verbatim
 * from /data/system/ifw, is parsed, and what ImportIfwRulesWorker merges from it. The import used
 * to extract bare component names and block them, dropping conditions and `block="false"`; it now
 * merges the whole rules.
 */
@RunWith(RobolectricTestRunner::class)
class IfwBackupImportFormatTest {
    private val deserializer = IfwXmlDeserializer()
    private val merge = IfwRuleMerge(IfwXmlSerializer(), canExtend = { true })

    private fun resource(name: String): String = checkNotNull(javaClass.classLoader?.getResource("ifw-backup/$name")) {
        "Missing test resource $name"
    }.readText()

    private fun parse(xml: String): IfwRules = deserializer.deserialize(xml)

    @Test
    fun givenBlockerWrittenFile_whenDeserializing_thenFiltersAreGroupedByComponentType() {
        val rules = parse(resource("blocker_written.xml"))

        assertThat(rules.componentFiltersFor(IfwComponentType.ACTIVITY)).containsExactly(
            "com.example.app/com.example.app.MainActivity",
            "com.example.app/.ui.SettingsActivity",
        ).inOrder()
        assertThat(rules.componentFiltersFor(IfwComponentType.BROADCAST)).containsExactly(
            "com.example.app/com.example.app.BootReceiver",
        )
        assertThat(rules.componentFiltersFor(IfwComponentType.SERVICE)).containsExactly(
            "com.example.app/com.example.app.SyncService",
        )
    }

    @Test
    fun givenBlockerWrittenFile_whenMergingIntoNoRules_thenTheSameRulesResult() {
        val backup = parse(resource("blocker_written.xml"))

        val merged = merge.merge(IfwRules.empty(), backup)

        assertThat(merged.rules).isEqualTo(backup)
        assertThat(merged.added).isEqualTo(3)
        assertThat(merged.present).isEqualTo(0)
    }

    @Test
    fun givenConditionalFile_whenDeserializing_thenConditionsAreKeptInTheModel() {
        val rules = parse(resource("conditional.xml"))

        assertThat(rules.rules).hasSize(4)
        val viewActivity = rules.rules[0]
        assertThat(viewActivity.intentFilters.single().actions)
            .containsExactly("android.intent.action.VIEW")
        val bootBroadcast = rules.rules[1]
        assertThat(bootBroadcast.log).isTrue()
        assertThat(bootBroadcast.filters.single()).isEqualTo(
            IfwFilter.And(
                listOf(
                    IfwFilter.Action(StringMatcher.Equals("android.intent.action.BOOT_COMPLETED")),
                    IfwFilter.Not(IfwFilter.Sender(SenderType.SYSTEM)),
                ),
            ),
        )
        val guardedBroadcast = rules.rules[2]
        assertThat(guardedBroadcast.filters).containsExactly(
            IfwFilter.SenderPackage("com.example.sender"),
            IfwFilter.ComponentFilter("com.example.app/com.example.app.GuardedReceiver"),
        ).inOrder()
    }

    @Test
    fun givenConditionalFile_whenMergingIntoNoRules_thenEveryRuleIsKeptWithItsConditions() {
        val backup = parse(resource("conditional.xml"))

        val merged = merge.merge(IfwRules.empty(), backup)

        // The intent-filter of the activity rule, the BOOT_COMPLETED rule without a
        // component-filter, the sender-package condition and the nested component-filter all
        // survive; earlier versions blocked bare component names instead.
        assertThat(merged.rules).isEqualTo(backup)
        assertThat(merged.added).isEqualTo(4)
    }

    @Test
    fun givenFileWithoutComponentFilters_whenMerging_thenTheRuleIsAddedAsItIs() {
        val backup = parse(
            """
            <rules>
              <broadcast block="true" log="true">
                <action equals="android.intent.action.BOOT_COMPLETED" />
              </broadcast>
            </rules>
            """.trimIndent(),
        )

        assertThat(merge.merge(IfwRules.empty(), backup).rules).isEqualTo(backup)
    }

    @Test
    fun givenEmptyRules_whenMerging_thenNothingChanges() {
        val current = IfwRules(listOf(IfwRule(IfwComponentType.SERVICE, filters = listOf(IfwFilter.ComponentFilter("com.example.app/com.example.app.Sync")))))

        val merged = merge.merge(current, IfwRules.empty())

        assertThat(merged.rules).isEqualTo(current)
        assertThat(merged.added).isEqualTo(0)
        assertThat(merged.present).isEqualTo(0)
    }

    @Test
    fun givenComponentFilterWithoutSlash_whenMerging_thenTheFilterIsKeptAsWritten() {
        // Earlier versions threw IndexOutOfBoundsException for this file.
        val backup = parse(
            """
            <rules>
              <activity block="true" log="false">
                <component-filter name="com.example.app" />
              </activity>
            </rules>
            """.trimIndent(),
        )

        assertThat(merge.merge(IfwRules.empty(), backup).rules).isEqualTo(backup)
    }

    @Test
    fun givenBlockFalseRule_whenMerging_thenItStaysAnAllowRule() {
        val backup = parse(
            """
            <rules>
              <service block="false" log="false">
                <component-filter name="com.example.app/com.example.app.Allowed" />
              </service>
            </rules>
            """.trimIndent(),
        )

        val merged = merge.merge(IfwRules.empty(), backup).rules

        assertThat(merged).isEqualTo(backup)
        assertThat(merged.rules.single().block).isFalse()
    }
}
