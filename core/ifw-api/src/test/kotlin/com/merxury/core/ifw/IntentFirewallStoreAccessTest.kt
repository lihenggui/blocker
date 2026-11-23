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
import com.merxury.blocker.core.model.ifw.IfwFailure
import com.merxury.blocker.core.model.ifw.IfwResult
import com.merxury.blocker.core.testing.controller.FakeIfwFileSystem
import com.merxury.core.ifw.model.IfwRules
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.IOException

// An element Blocker's parser does not know, so the file cannot be read.
private val UNKNOWN_ELEMENT_XML = """<rules><activity block="true" log="true"><component-filter name="$PKG/$PKG.Main" /><mystery /></activity></rules>"""

/** Failures, the cache, the lock, the listing and the change events. */
@RunWith(RobolectricTestRunner::class)
class IntentFirewallStoreAccessTest {

    private val fileSystem = FakeIfwFileSystem()
    private val store = storeOver(fileSystem)

    @Test
    fun givenUnparseableFile_whenBlocks_thenUnknownExceptProviders() = runTest {
        fileSystem.putFile(PKG, UNKNOWN_ELEMENT_XML)

        assertThat(store.blocks(PKG, listOf(MAIN, SYNC, FILES))).containsExactly(
            MAIN.id,
            IfwBlock.UNKNOWN,
            SYNC.id,
            IfwBlock.UNKNOWN,
            FILES.id,
            IfwBlock.NOT_BLOCKED,
        )
    }

    @Test
    fun givenUnparseableOrUnreadableFile_whenChanged_thenUnreadableAndNeverWritten() = runTest {
        fileSystem.putFile(PKG, UNKNOWN_ELEMENT_XML)
        fileSystem.putFile("com.locked", "<rules />")
        fileSystem.unreadablePackages += "com.locked"

        assertThat(store.setBlocked(PKG, mapOf(SETTINGS to true))).isEqualTo(IfwResult.Failure(IfwFailure.Unreadable(PKG)))
        assertThat(store.update(PKG) { IfwRules.empty() }).isEqualTo(IfwResult.Failure(IfwFailure.Unreadable(PKG)))
        val locked = declaration("Main", ComponentType.ACTIVITY, packageName = "com.locked")
        assertThat(store.setBlocked("com.locked", mapOf(locked to true))).isEqualTo(IfwResult.Failure(IfwFailure.Unreadable("com.locked")))

        assertThat(fileSystem.changeCount).isEqualTo(0)
        assertThat(fileSystem.fileContent(PKG)).isEqualTo(UNKNOWN_ELEMENT_XML)
    }

    @Test
    fun givenUnreadable_whenUpdate_thenTransformNeverRuns() = runTest {
        fileSystem.putFile(PKG, UNKNOWN_ELEMENT_XML)
        var ran = false

        store.update(PKG) {
            ran = true
            it
        }

        assertThat(ran).isFalse()
    }

    @Test
    fun givenNoAccess_whenEachOperation_thenNoAccessAndUnknown() = runTest {
        fileSystem.putFile(PKG, LEGACY_XML)
        fileSystem.noAccess = true

        assertThat(store.blocks(PKG, listOf(MAIN, FILES))).containsExactly(MAIN.id, IfwBlock.UNKNOWN, FILES.id, IfwBlock.NOT_BLOCKED)
        assertThat(store.setBlocked(PKG, mapOf(SETTINGS to true))).isEqualTo(IfwResult.Failure(IfwFailure.NoAccess))
        assertThat(store.rules(PKG)).isEqualTo(IfwResult.Failure(IfwFailure.NoAccess))
        assertThat(store.update(PKG) { IfwRules.empty() }).isEqualTo(IfwResult.Failure(IfwFailure.NoAccess))
        assertThat(store.allRules()).isEqualTo(IfwResult.Failure(IfwFailure.NoAccess))
        assertThat(fileSystem.fileContent(PKG)).isEqualTo(LEGACY_XML)
    }

    @Test
    fun givenUnparseableFile_whenRuleFileText_thenReturnsTheContentAsStored() = runTest {
        fileSystem.putFile(PKG, UNKNOWN_ELEMENT_XML)

        assertThat(store.ruleFileText(PKG)).isEqualTo(IfwResult.Success(UNKNOWN_ELEMENT_XML))
        assertThat(store.ruleFileText("com.missing")).isEqualTo(IfwResult.Success(null))
        assertThat(fileSystem.operations).containsExactly("read $PKG", "read com.missing").inOrder()
    }

    @Test
    fun givenUnreadableFileOrNoAccess_whenRuleFileText_thenTypedFailure() = runTest {
        fileSystem.putFile("com.locked", "<rules />")
        fileSystem.unreadablePackages += "com.locked"

        assertThat(store.ruleFileText("com.locked")).isEqualTo(IfwResult.Failure(IfwFailure.Unreadable("com.locked")))
        fileSystem.noAccess = true
        assertThat(store.ruleFileText(PKG)).isEqualTo(IfwResult.Failure(IfwFailure.NoAccess))
    }

    @Test
    fun givenOwnFileName_whenRestoreRuleFile_thenWrittenAsGivenOnlyWhenAbsent() = runTest {
        fileSystem.putFile("kept-rules", "on the device")

        assertThat(store.restoreRuleFile("my-rules", UNKNOWN_ELEMENT_XML)).isEqualTo(IfwResult.Success(true))
        assertThat(store.restoreRuleFile("kept-rules", LEGACY_XML)).isEqualTo(IfwResult.Success(false))

        assertThat(fileSystem.fileContent("my-rules")).isEqualTo(UNKNOWN_ELEMENT_XML)
        assertThat(fileSystem.fileContent("kept-rules")).isEqualTo("on the device")
        // A name that looks like a package is just a name here.
        assertThat(store.restoreRuleFile("privacy.rules", LEGACY_XML)).isEqualTo(IfwResult.Success(true))
        assertThat(fileSystem.fileContent("privacy.rules")).isEqualTo(LEGACY_XML)
        assertThrows(IllegalArgumentException::class.java) { runBlocking { store.restoreRuleFile("../escape", LEGACY_XML) } }
    }

    @Test
    fun givenNotConnected_whenBlocks_thenUnknownWithoutConnecting() = runTest {
        fileSystem.putFile(PKG, LEGACY_XML)
        fileSystem.connected = false

        assertThat(store.blocks(PKG, listOf(MAIN))).containsExactly(MAIN.id, IfwBlock.UNKNOWN)
        assertThat(fileSystem.operations).containsExactly("peek $PKG")
    }

    @Test
    fun givenNotConnected_whenRulesOrSetBlocked_thenConnectsToRead() = runTest {
        fileSystem.connected = false

        store.rules(PKG)
        store.setBlocked(PKG, mapOf(MAIN to true))

        assertThat(fileSystem.operations).containsExactly("read $PKG", "read $PKG", "write $PKG").inOrder()
    }

    @Test
    fun givenWriteFails_whenSetBlocked_thenWriteFailedAndOldFileKept() = runTest {
        fileSystem.putFile(PKG, LEGACY_XML)
        fileSystem.writeFailure = IOException("EIO")

        val result = store.setBlocked(PKG, mapOf(SETTINGS to true))

        assertThat(result).isEqualTo(IfwResult.Failure(IfwFailure.WriteFailed(PKG, "EIO")))
        assertThat(fileSystem.fileContent(PKG)).isEqualTo(LEGACY_XML)
    }

    @Test
    fun givenDeleteFails_whenUnblockLast_thenWriteFailed() = runTest {
        store.setBlocked(PKG, mapOf(MAIN to true))
        fileSystem.writeFailure = IOException("EROFS")

        assertThat(store.setBlocked(PKG, mapOf(MAIN to false))).isEqualTo(IfwResult.Failure(IfwFailure.WriteFailed(PKG, "EROFS")))
    }

    @Test
    fun givenWriteFailed_whenBlocks_thenReadsTheFileAgain() = runTest {
        store.blocks(PKG, listOf(MAIN))
        fileSystem.writeFailure = IOException("EIO")
        store.setBlocked(PKG, mapOf(MAIN to true))
        fileSystem.operations.clear()

        assertThat(store.blocks(PKG, listOf(MAIN))).containsExactly(MAIN.id, IfwBlock.NOT_BLOCKED)
        assertThat(fileSystem.operations).containsExactly("peek $PKG")
    }

    @Test
    fun whenBlocksTwice_thenFileReadOnce() = runTest {
        store.blocks(PKG, listOf(MAIN))
        store.blocks(PKG, listOf(SETTINGS))

        assertThat(fileSystem.operations).containsExactly("peek $PKG")
    }

    @Test
    fun givenExternalWriter_whenBlocks_thenStaleUntilClearCache() = runTest {
        store.blocks(PKG, listOf(MAIN))
        fileSystem.putFile(PKG, LEGACY_XML)

        assertThat(store.blocks(PKG, listOf(MAIN))).containsExactly(MAIN.id, IfwBlock.NOT_BLOCKED)
        store.clearCache()
        assertThat(store.blocks(PKG, listOf(MAIN))).containsExactly(MAIN.id, IfwBlock.BLOCKED)
    }

    @Test
    fun givenExternalWriterAfterCache_whenSetBlocked_thenChangeBuiltOnTheFile() = runTest {
        store.blocks(PKG, listOf(MAIN))
        fileSystem.putFile(PKG, LEGACY_XML)

        store.setBlocked(PKG, mapOf(SETTINGS to true))

        assertThat(store.blocks(PKG, listOf(MAIN, SETTINGS, SYNC)).values).containsExactly(IfwBlock.BLOCKED, IfwBlock.BLOCKED, IfwBlock.BLOCKED)
    }

    @Test
    fun givenClearCacheDuringRead_whenReadEnds_thenNotCached() = runTest {
        fileSystem.beforeRead = { store.clearCache() }
        store.blocks(PKG, listOf(MAIN))
        fileSystem.beforeRead = {}

        store.blocks(PKG, listOf(MAIN))

        assertThat(fileSystem.operations).containsExactly("peek $PKG", "peek $PKG")
    }

    @Test
    fun givenConcurrentChanges_whenSetBlocked_thenOneReadModifyWriteAfterTheOtherAndNoneLost() = runTest {
        val gate = CompletableDeferred<Unit>()
        fileSystem.beforeRead = { if (!gate.isCompleted) gate.await() }

        val first = launch { store.setBlocked(PKG, mapOf(MAIN to true)) }
        val second = launch { store.setBlocked(PKG, mapOf(SYNC to true)) }
        runCurrent()
        assertThat(fileSystem.operations).containsExactly("read $PKG")
        gate.complete(Unit)
        first.join()
        second.join()

        assertThat(fileSystem.operations).containsExactly("read $PKG", "write $PKG", "read $PKG", "write $PKG").inOrder()
        assertThat(store.blocks(PKG, listOf(MAIN, SYNC)).values).containsExactly(IfwBlock.BLOCKED, IfwBlock.BLOCKED)
    }

    @Test
    fun givenCancelledDuringRead_whenSetBlocked_thenCancellationPropagatesAndNothingWritten() = runTest {
        fileSystem.beforeRead = { awaitCancellation() }

        val job = launch { store.setBlocked(PKG, mapOf(MAIN to true)) }
        runCurrent()
        job.cancel()
        job.join()

        assertThat(job.isCancelled).isTrue()
        assertThat(fileSystem.changeCount).isEqualTo(0)
    }

    @Test
    fun whenChanged_thenChangesEmitsOncePerWriteAndNotForNoOpOrFailure() = runTest {
        store.changes.test {
            store.setBlocked(PKG, mapOf(MAIN to true))
            assertThat(awaitItem()).isEqualTo(PKG)

            store.setBlocked(PKG, mapOf(MAIN to true))
            fileSystem.writeFailure = IOException("EIO")
            store.setBlocked(PKG, mapOf(SYNC to true))
            fileSystem.writeFailure = null
            store.update("com.other") { IfwRules.empty() }
            expectNoEvents()

            store.update(PKG) { IfwRules.empty() }
            assertThat(awaitItem()).isEqualTo(PKG)
        }
    }

    @Test
    fun givenUpdateToEmpty_whenUpdate_thenFileDeletedAndEmptyReturned() = runTest {
        fileSystem.putFile(PKG, LEGACY_XML)

        assertThat(store.update(PKG) { IfwRules.empty() }).isEqualTo(IfwResult.Success(IfwRules.empty()))
        assertThat(fileSystem.fileContent(PKG)).isNull()
        assertThat(store.rules(PKG)).isEqualTo(IfwResult.Success(IfwRules.empty()))
    }

    @Test
    fun givenUnchangedTransform_whenUpdate_thenNothingWritten() = runTest {
        fileSystem.putFile(PKG, LEGACY_XML)

        store.update(PKG) { it }

        assertThat(fileSystem.changeCount).isEqualTo(0)
    }

    @Test
    fun givenUnreadableAndWakeFiles_whenAllRules_thenEachPackageWithItsOutcomeAndNoWakeFile() = runTest {
        fileSystem.putFile(PKG, LEGACY_XML)
        fileSystem.putFile("com.broken", UNKNOWN_ELEMENT_XML)
        fileSystem.putFile("com.locked", "<rules />")
        fileSystem.unreadablePackages += "com.locked"
        fileSystem.putFile("blocker-managed-wake", CONDITIONAL_MAIN_XML)
        fileSystem.putFile("blocker-wake-capability-probe", CONDITIONAL_MAIN_XML)
        fileSystem.putFile("blocker-managed-wake-com.merxury.blocker.debug", CONDITIONAL_MAIN_XML)

        val all = (store.allRules() as IfwResult.Success).value

        assertThat(all.keys).containsExactly(PKG, "com.broken", "com.locked")
        assertThat(all[PKG]).isEqualTo(store.rules(PKG))
        assertThat(all["com.broken"]).isEqualTo(IfwResult.Failure(IfwFailure.Unreadable("com.broken")))
        assertThat(all["com.locked"]).isEqualTo(IfwResult.Failure(IfwFailure.Unreadable("com.locked")))
        assertThat(fileSystem.operations.filter { it.contains("wake") }).isEmpty()
    }

    @Test
    fun givenComponentOfOtherPackage_whenSetBlocked_thenIllegalArgument() {
        val other = declaration("Main", ComponentType.ACTIVITY, packageName = "com.other")

        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { store.setBlocked(PKG, mapOf(other to true)) }
        }
    }

    @Test
    fun givenFilesUnderNamesOfTheirOwn_whenAllRulesRulesAndUpdate_thenRead() = runTest {
        fileSystem.putFile(PKG, LEGACY_XML)
        fileSystem.putFile("my-rules", CONDITIONAL_MAIN_XML)
        fileSystem.putFile("custom..rules", CONDITIONAL_MAIN_XML)

        val all = (store.allRules() as IfwResult.Success).value

        assertThat(all.keys).containsExactly(PKG, "my-rules", "custom..rules")
        assertThat(store.rules("custom..rules")).isEqualTo(all["custom..rules"])
        assertThat(store.update("my-rules") { IfwRules.empty() }).isEqualTo(IfwResult.Success(IfwRules.empty()))
        assertThat(fileSystem.fileContent("my-rules")).isNull()
    }

    @Test
    fun givenNameNotAPackage_whenBlocksOrSetBlocked_thenIllegalArgument() {
        listOf("my-rules", "custom..rules").forEach { name ->
            val component = declaration("Main", ComponentType.ACTIVITY, packageName = name)
            assertThrows(name, IllegalArgumentException::class.java) {
                runBlocking { store.blocks(name, listOf(component)) }
            }
            assertThrows(name, IllegalArgumentException::class.java) {
                runBlocking { store.setBlocked(name, mapOf(component to true)) }
            }
        }
        assertThat(fileSystem.operations).isEmpty()
    }

    @Test
    fun givenWakeFileName_whenRules_thenIllegalArgument() {
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { store.rules("blocker-managed-wake") }
        }
    }
}
