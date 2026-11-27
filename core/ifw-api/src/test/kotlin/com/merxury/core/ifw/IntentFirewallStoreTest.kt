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

package com.merxury.core.ifw

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.merxury.blocker.core.model.ComponentType
import com.merxury.blocker.core.model.component.IfwBlock
import com.merxury.blocker.core.model.ifw.IfwResult
import com.merxury.blocker.core.testing.controller.FakeIfwFileSystem
import com.merxury.core.ifw.model.IfwComponentType
import com.merxury.core.ifw.model.IfwFilter
import com.merxury.core.ifw.model.IfwRule
import com.merxury.core.ifw.model.IfwRules
import com.merxury.core.ifw.model.SenderType
import com.merxury.core.ifw.xml.IfwXmlDeserializer
import com.merxury.core.ifw.xml.IfwXmlSerializer
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** What blocks a component, and how blocking and unblocking change the rules. */
@RunWith(RobolectricTestRunner::class)
class IntentFirewallStoreTest {

    private val fileSystem = FakeIfwFileSystem()
    private val store = storeOver(fileSystem)
    private val serializer = IfwXmlSerializer()

    @Test
    fun givenNoFile_whenBlocks_thenNothingBlocked() = runTest {
        assertThat(store.blocks(PKG, listOf(MAIN, SYNC, BOOT, FILES))).containsExactly(
            MAIN.id,
            IfwBlock.NOT_BLOCKED,
            SYNC.id,
            IfwBlock.NOT_BLOCKED,
            BOOT.id,
            IfwBlock.NOT_BLOCKED,
            FILES.id,
            IfwBlock.NOT_BLOCKED,
        )
    }

    @Test
    fun givenLegacyFile_whenBlocks_thenEveryNamedComponentBlocked() = runTest {
        fileSystem.putFile(PKG, LEGACY_XML)

        assertThat(store.blocks(PKG, listOf(MAIN, SETTINGS, SYNC, BOOT))).containsExactly(
            MAIN.id,
            IfwBlock.BLOCKED,
            SETTINGS.id,
            IfwBlock.NOT_BLOCKED,
            SYNC.id,
            IfwBlock.BLOCKED,
            BOOT.id,
            IfwBlock.BLOCKED,
        )
    }

    @Test
    fun givenLegacyFile_whenBlockAnother_thenEveryRuleKeptAndOneFilterAdded() = runTest {
        fileSystem.putFile(PKG, LEGACY_XML)
        val before = IfwXmlDeserializer().deserialize(LEGACY_XML)

        store.setBlocked(PKG, mapOf(SETTINGS to true))

        val activity = before.rules[0]
        assertThat(fileSystem.rulesOf()).isEqualTo(
            IfwRules(
                listOf(
                    activity.copy(filters = activity.filters + IfwFilter.ComponentFilter("$PKG/$PKG.Settings")),
                    before.rules[2],
                    before.rules[1],
                ),
            ),
        )
        assertThat(store.blocks(PKG, listOf(MAIN, SETTINGS, SYNC, BOOT)).values).containsExactly(IfwBlock.BLOCKED, IfwBlock.BLOCKED, IfwBlock.BLOCKED, IfwBlock.BLOCKED)
    }

    @Test
    fun givenSenderCondition_whenBlocks_thenConditional() = runTest {
        fileSystem.putFile(PKG, CONDITIONAL_MAIN_XML)

        assertThat(store.blocks(PKG, listOf(MAIN))).containsExactly(MAIN.id, IfwBlock.CONDITIONAL)
    }

    @Test
    fun givenIntentFilterBesideComponentFilter_whenBlocks_thenBlocked() = runTest {
        // Android picks the rule by the component-filter alone; the intent-filter is an alternative.
        fileSystem.putFile(
            PKG,
            """
            <rules>
              <activity block="true" log="false">
                <intent-filter><action name="com.example.OPEN" /></intent-filter>
                <component-filter name="$PKG/$PKG.Main" />
              </activity>
            </rules>
            """.trimIndent(),
        )

        assertThat(store.blocks(PKG, listOf(MAIN))).containsExactly(MAIN.id, IfwBlock.BLOCKED)
    }

    @Test
    fun givenRuleThatOnlyLogs_whenBlocks_thenNotBlocked() = runTest {
        fileSystem.putFile(PKG, """<rules><activity block="false" log="true"><component-filter name="$PKG/$PKG.Main" /></activity></rules>""")

        assertThat(store.blocks(PKG, listOf(MAIN))).containsExactly(MAIN.id, IfwBlock.NOT_BLOCKED)
    }

    @Test
    fun givenShortClassName_whenBlocks_thenBlocked() = runTest {
        fileSystem.putFile(PKG, """<rules><service block="true" log="true"><component-filter name="$PKG/.Sync" /></service></rules>""")

        assertThat(store.blocks(PKG, listOf(SYNC))).containsExactly(SYNC.id, IfwBlock.BLOCKED)
    }

    @Test
    fun givenRuleOfOtherType_whenBlocks_thenNotBlocked() = runTest {
        fileSystem.putFile(PKG, """<rules><service block="true" log="true"><component-filter name="$PKG/$PKG.Main" /></service></rules>""")

        assertThat(store.blocks(PKG, listOf(MAIN))).containsExactly(MAIN.id, IfwBlock.NOT_BLOCKED)
    }

    @Test
    fun givenNoFile_whenBlockEachType_thenOneUnconditionalRulePerTypeAndReceiverAsBroadcast() = runTest {
        val result = store.setBlocked(PKG, mapOf(MAIN to true, SETTINGS to true, SYNC to true, BOOT to true))

        assertThat(result).isEqualTo(
            IfwResult.Success(mapOf(MAIN.id to IfwBlock.BLOCKED, SETTINGS.id to IfwBlock.BLOCKED, SYNC.id to IfwBlock.BLOCKED, BOOT.id to IfwBlock.BLOCKED)),
        )
        assertThat(fileSystem.rulesOf()).isEqualTo(
            IfwRules(
                listOf(
                    IfwRule(IfwComponentType.ACTIVITY, filters = listOf(filterFor("Main"), filterFor("Settings"))),
                    IfwRule(IfwComponentType.BROADCAST, filters = listOf(filterFor("Boot"))),
                    IfwRule(IfwComponentType.SERVICE, filters = listOf(filterFor("Sync"))),
                ),
            ),
        )
    }

    @Test
    fun givenOnlyConditionalRule_whenBlock_thenNewUnconditionalRuleAndConditionalUntouched() = runTest {
        fileSystem.putFile(PKG, CONDITIONAL_MAIN_XML)
        val conditional = IfwXmlDeserializer().deserialize(CONDITIONAL_MAIN_XML).rules.single()

        val result = store.setBlocked(PKG, mapOf(SETTINGS to true, MAIN to true))

        assertThat(result).isEqualTo(IfwResult.Success(mapOf(SETTINGS.id to IfwBlock.BLOCKED, MAIN.id to IfwBlock.BLOCKED)))
        assertThat(fileSystem.rulesOf()!!.rules).containsExactly(
            conditional,
            IfwRule(IfwComponentType.ACTIVITY, filters = listOf(filterFor("Settings"), filterFor("Main"))),
        ).inOrder()
    }

    @Test
    fun givenBlockedAndConditional_whenUnblock_thenConditionalStaysAndIsReported() = runTest {
        fileSystem.putFile(PKG, CONDITIONAL_MAIN_XML)
        store.setBlocked(PKG, mapOf(MAIN to true))

        val result = store.setBlocked(PKG, mapOf(MAIN to false))

        assertThat(result).isEqualTo(IfwResult.Success(mapOf(MAIN.id to IfwBlock.CONDITIONAL)))
        assertThat(fileSystem.rulesOf()).isEqualTo(IfwXmlDeserializer().deserialize(CONDITIONAL_MAIN_XML))
    }

    @Test
    fun givenRuleWithIntentFilter_whenBlockAnother_thenNotAddedToThatRule() = runTest {
        val userRule = IfwXmlDeserializer().deserialize(
            """<rules><activity block="true" log="true"><intent-filter><action name="com.example.OPEN" /></intent-filter><component-filter name="$PKG/$PKG.Main" /></activity></rules>""",
        ).rules.single()
        fileSystem.putFile(PKG, serializer.serialize(IfwRules(listOf(userRule))))

        store.setBlocked(PKG, mapOf(SETTINGS to true))

        assertThat(fileSystem.rulesOf()!!.rules).containsExactly(
            userRule,
            IfwRule(IfwComponentType.ACTIVITY, filters = listOf(filterFor("Settings"))),
        ).inOrder()
    }

    @Test
    fun givenRuleWithIntentFilter_whenUnblockItsComponent_thenFilterRemovedAndRuleKept() = runTest {
        val userRule = IfwXmlDeserializer().deserialize(
            """<rules><activity block="true" log="true"><intent-filter><action name="com.example.OPEN" /></intent-filter><component-filter name="$PKG/$PKG.Main" /></activity></rules>""",
        ).rules.single()
        fileSystem.putFile(PKG, serializer.serialize(IfwRules(listOf(userRule))))

        val result = store.setBlocked(PKG, mapOf(MAIN to false))

        assertThat(result).isEqualTo(IfwResult.Success(mapOf(MAIN.id to IfwBlock.NOT_BLOCKED)))
        assertThat(fileSystem.rulesOf()!!.rules).containsExactly(userRule.copy(filters = emptyList()))
    }

    @Test
    fun givenComponentInTwoRules_whenUnblock_thenRemovedFromBothAndEmptyRuleDropped() = runTest {
        fileSystem.putFile(
            PKG,
            """
            <rules>
              <activity block="true" log="true"><component-filter name="$PKG/$PKG.Main" /></activity>
              <activity block="true" log="false"><component-filter name="$PKG/.Main" /><component-filter name="$PKG/$PKG.Settings" /></activity>
            </rules>
            """.trimIndent(),
        )

        store.setBlocked(PKG, mapOf(MAIN to false))

        assertThat(fileSystem.rulesOf()).isEqualTo(
            IfwRules(listOf(IfwRule(IfwComponentType.ACTIVITY, log = false, filters = listOf(filterFor("Settings"))))),
        )
    }

    @Test
    fun givenLastBlockedComponent_whenUnblock_thenFileDeleted() = runTest {
        store.setBlocked(PKG, mapOf(MAIN to true))

        store.setBlocked(PKG, mapOf(MAIN to false))

        assertThat(fileSystem.fileContent(PKG)).isNull()
        assertThat(fileSystem.operations.last()).isEqualTo("delete $PKG")
    }

    @Test
    fun whenBlockProvider_thenNotBlockedAndNothingWritten() = runTest {
        val result = store.setBlocked(PKG, mapOf(FILES to true))

        assertThat(result).isEqualTo(IfwResult.Success(mapOf(FILES.id to IfwBlock.NOT_BLOCKED)))
        assertThat(fileSystem.operations).isEmpty()
    }

    @Test
    fun whenBlockProviderWithActivity_thenOnlyActivityWritten() = runTest {
        val result = store.setBlocked(PKG, mapOf(FILES to true, MAIN to true))

        assertThat(result).isEqualTo(IfwResult.Success(mapOf(FILES.id to IfwBlock.NOT_BLOCKED, MAIN.id to IfwBlock.BLOCKED)))
        assertThat(fileSystem.rulesOf()).isEqualTo(IfwRules(listOf(IfwRule(IfwComponentType.ACTIVITY, filters = listOf(filterFor("Main"))))))
    }

    @Test
    fun givenAlreadyInRequestedState_whenSetBlocked_thenNoWrite() = runTest {
        fileSystem.putFile(PKG, LEGACY_XML)

        val result = store.setBlocked(PKG, mapOf(MAIN to true, SETTINGS to false))

        assertThat(result).isEqualTo(IfwResult.Success(mapOf(MAIN.id to IfwBlock.BLOCKED, SETTINGS.id to IfwBlock.NOT_BLOCKED)))
        assertThat(fileSystem.changeCount).isEqualTo(0)
        assertThat(fileSystem.fileContent(PKG)).isEqualTo(LEGACY_XML)
    }

    @Test
    fun givenNoFile_whenUnblock_thenNothingWritten() = runTest {
        store.setBlocked(PKG, mapOf(MAIN to false))

        assertThat(fileSystem.changeCount).isEqualTo(0)
    }

    @Test
    fun givenEveryFilterKind_whenBlockAnother_thenHandWrittenRuleSurvivesExactly() = runTest {
        val handWritten = IfwXmlDeserializer().deserialize(
            """
            <rules>
              <broadcast block="true" log="true">
                <intent-filter>
                  <action name="android.intent.action.BOOT_COMPLETED" />
                  <cat name="android.intent.category.DEFAULT" />
                  <scheme name="package" />
                </intent-filter>
                <component-filter name="$PKG/$PKG.Boot" />
                <or>
                  <sender-package name="com.other" />
                  <and>
                    <action startsWith="com.example." />
                    <not><sender-permission name="android.permission.REBOOT" /></not>
                  </and>
                  <port min="1" max="80" />
                  <component-name regex=".*Boot" />
                  <data isNull="true" />
                </or>
              </broadcast>
            </rules>
            """.trimIndent(),
        ).rules.single()
        fileSystem.putFile(PKG, serializer.serialize(IfwRules(listOf(handWritten))))

        store.setBlocked(PKG, mapOf(BOOT to true, MAIN to true))
        store.setBlocked(PKG, mapOf(MAIN to false))

        assertThat(fileSystem.rulesOf()!!.rules).containsExactly(handWritten, IfwRule(IfwComponentType.BROADCAST, filters = listOf(filterFor("Boot")))).inOrder()
        assertThat(handWritten.filters.filterIsInstance<IfwFilter.Or>().single().filters).hasSize(5)
        assertThat(store.blocks(PKG, listOf(BOOT))).containsExactly(BOOT.id, IfwBlock.BLOCKED)
    }

    @Test
    fun givenConditionalNamesTwoComponents_whenBlocks_thenBothConditional() = runTest {
        fileSystem.putFile(
            PKG,
            """<rules><service block="true" log="true"><component-filter name="$PKG/$PKG.Sync" /><component-filter name="$PKG/$PKG.Other" /><sender type="${SenderType.SIGNATURE.xmlValue}" /></service></rules>""",
        )

        assertThat(store.blocks(PKG, listOf(SYNC, declaration("Other", ComponentType.SERVICE))).values)
            .containsExactly(IfwBlock.CONDITIONAL, IfwBlock.CONDITIONAL)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun givenCachedMissingRuleAndSlowCollector_whenRestoreIsCancelledDuringWrite_thenCacheAndNotificationReflectTheWrite() = runTest {
        val files = FakeIfwFileSystem()
        val writeStarted = CompletableDeferred<Unit>()
        val finishWrite = CompletableDeferred<Unit>()
        val filesWithPausedWrite = object : IfwFileSystem by files {
            override suspend fun writeRules(packageName: String, content: String) {
                writeStarted.complete(Unit)
                finishWrite.await()
                files.writeRules(packageName, content)
            }
        }
        val store = IntentFirewallStore(filesWithPausedWrite, IfwXmlSerializer(), IfwXmlDeserializer(), "com.merxury.blocker.test")
        assertThat(store.blocks(PKG, listOf(MAIN))[MAIN.id]).isEqualTo(IfwBlock.NOT_BLOCKED)

        val releaseCollector = CompletableDeferred<Unit>()
        store.changes.onEach { if (it == "seed") releaseCollector.await() }.test {
            files.putFile("seed", "<rules/>")
            store.clear("seed")
            runCurrent()
            // Fill the change buffer so the restore's notification has to suspend.
            repeat(64) {
                files.putFile("other", "<rules/>")
                store.clear("other")
            }
            val restore = launch { store.restoreRuleFile(PKG, LEGACY_XML) }
            writeStarted.await()
            restore.cancel()
            finishWrite.complete(Unit)
            runCurrent()
            val waitingToNotify = !restore.isCompleted

            releaseCollector.complete(Unit)

            assertThat(awaitItem()).isEqualTo("seed")
            repeat(64) { assertThat(awaitItem()).isEqualTo("other") }
            assertThat(awaitItem()).isEqualTo(PKG)
            restore.join()
            assertThat(waitingToNotify).isTrue()
            assertThat(restore.isCancelled).isTrue()
            assertThat(files.fileContent(PKG)).isEqualTo(LEGACY_XML)
            assertThat(store.blocks(PKG, listOf(MAIN))[MAIN.id]).isEqualTo(IfwBlock.BLOCKED)
        }
    }

    private fun filterFor(simpleName: String) = IfwFilter.ComponentFilter("$PKG/$PKG.$simpleName")
}
