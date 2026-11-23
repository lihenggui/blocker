// Copyright 2026 Blocker
// SPDX-License-Identifier: Apache-2.0

package com.merxury.blocker.core.rule.work

import com.google.common.truth.Truth.assertThat
import com.merxury.blocker.core.model.ComponentType
import com.merxury.blocker.core.model.data.ComponentInfo
import com.merxury.core.ifw.model.IfwComponentType
import com.merxury.core.ifw.model.IfwFilter
import com.merxury.core.ifw.model.IfwRules
import com.merxury.core.ifw.model.SenderType
import com.merxury.core.ifw.model.StringMatcher
import com.merxury.core.ifw.xml.IfwXmlDeserializer
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Pins what ImportIfwRulesWorker extracts from an IFW backup file, `ifw/<package>.xml`, which
 * ExportIfwRulesWorker copies verbatim from /data/system/ifw.
 */
@RunWith(RobolectricTestRunner::class)
class IfwBackupImportFormatTest {
    private val deserializer = IfwXmlDeserializer()

    private fun resource(name: String): String = checkNotNull(javaClass.classLoader?.getResource("ifw-backup/$name")) {
        "Missing test resource $name"
    }.readText()

    private fun component(packageName: String, name: String) = ComponentInfo(
        packageName = packageName,
        name = name,
        type = ComponentType.ACTIVITY,
    )

    @Test
    fun givenBlockerWrittenFile_whenDeserializing_thenFiltersAreGroupedByComponentType() {
        val rules = deserializer.deserialize(resource("blocker_written.xml"))

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
    fun givenBlockerWrittenFile_whenExtractingImportComponents_thenSplitsPackageAndName() {
        val components = extractIfwImportComponents(
            deserializer.deserialize(resource("blocker_written.xml")),
        )

        assertThat(components.activities).containsExactly(
            component("com.example.app", "com.example.app.MainActivity"),
            component("com.example.app", ".ui.SettingsActivity"),
        ).inOrder()
        assertThat(components.broadcasts).containsExactly(
            component("com.example.app", "com.example.app.BootReceiver"),
        )
        assertThat(components.services).containsExactly(
            component("com.example.app", "com.example.app.SyncService"),
        )
    }

    @Test
    fun givenConditionalFile_whenDeserializing_thenConditionsAreKeptInTheModel() {
        val rules = deserializer.deserialize(resource("conditional.xml"))

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
    fun givenConditionalFile_whenExtractingImportComponents_thenOnlyTopLevelComponentFiltersAreReturned() {
        val components = extractIfwImportComponents(
            deserializer.deserialize(resource("conditional.xml")),
        )

        // The activity rule has an intent-filter selector next to its component-filter: the
        // selector is dropped, so the import blocks ViewActivity for every intent.
        assertThat(components.activities).containsExactly(
            component("com.example.app", "com.example.app.ViewActivity"),
        )
        // The sender-package condition is dropped as well. The BOOT_COMPLETED rule has no
        // component-filter, so it contributes nothing.
        assertThat(components.broadcasts).containsExactly(
            component("com.example.app", "com.example.app.GuardedReceiver"),
        )
        // A component-filter nested in <and> is not extracted, a top-level one is.
        assertThat(components.services).containsExactly(
            component("com.example.app", "com.example.app.PlainService"),
        )
    }

    @Test
    fun givenFileWithoutComponentFilters_whenExtractingImportComponents_thenAllListsAreEmpty() {
        val components = extractIfwImportComponents(
            deserializer.deserialize(
                """
                <rules>
                  <broadcast block="true" log="true">
                    <action equals="android.intent.action.BOOT_COMPLETED" />
                  </broadcast>
                </rules>
                """.trimIndent(),
            ),
        )

        assertThat(components.activities).isEmpty()
        assertThat(components.broadcasts).isEmpty()
        assertThat(components.services).isEmpty()
    }

    @Test
    fun givenEmptyRules_whenExtractingImportComponents_thenAllListsAreEmpty() {
        val components = extractIfwImportComponents(IfwRules.empty())

        assertThat(components).isEqualTo(
            IfwImportComponents(emptyList(), emptyList(), emptyList()),
        )
    }

    @Test
    fun givenComponentFilterWithoutSlash_whenExtractingImportComponents_thenThrowsIndexOutOfBounds() {
        val rules = deserializer.deserialize(
            """
            <rules>
              <activity block="true" log="false">
                <component-filter name="com.example.app" />
              </activity>
            </rules>
            """.trimIndent(),
        )

        assertThrows(IndexOutOfBoundsException::class.java) { extractIfwImportComponents(rules) }
    }

    @Test
    fun givenBlockFalseRule_whenExtractingImportComponents_thenTheFilterIsStillReturned() {
        val rules = deserializer.deserialize(
            """
            <rules>
              <service block="false" log="false">
                <component-filter name="com.example.app/com.example.app.Allowed" />
              </service>
            </rules>
            """.trimIndent(),
        )

        assertThat(extractIfwImportComponents(rules).services).containsExactly(
            component("com.example.app", "com.example.app.Allowed"),
        )
    }
}
