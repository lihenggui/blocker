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
import com.merxury.blocker.core.model.component.IfwBlock
import com.merxury.blocker.core.model.ifw.IfwFailure
import com.merxury.blocker.core.model.ifw.IfwResult
import com.merxury.blocker.core.testing.controller.FakeIfwFileSystem
import com.merxury.core.ifw.model.IfwRules
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.IOException

private const val DAMAGED_XML = "<rules><activity block=\"true\"><component-filter name="

private const val WAKE_FILE = "blocker-managed-wake"

/** Deleting a package's rule file without reading it, and listing the files without reading them. */
@RunWith(RobolectricTestRunner::class)
class IntentFirewallStoreClearTest {

    private val fileSystem = FakeIfwFileSystem()
    private val store = storeOver(fileSystem)

    @Test
    fun givenDamagedFile_whenClear_thenDeletedWithoutReadingAndTrue() = runTest {
        fileSystem.putFile(PKG, DAMAGED_XML)
        assertThat(store.rules(PKG)).isEqualTo(IfwResult.Failure(IfwFailure.Unreadable(PKG)))
        fileSystem.operations.clear()

        assertThat(store.clear(PKG)).isEqualTo(IfwResult.Success(true))

        assertThat(fileSystem.fileContent(PKG)).isNull()
        assertThat(fileSystem.operations).containsExactly("delete $PKG")
    }

    @Test
    fun givenUnreadableFile_whenClear_thenDeleted() = runTest {
        fileSystem.putFile(PKG, LEGACY_XML)
        fileSystem.unreadablePackages += PKG

        assertThat(store.clear(PKG)).isEqualTo(IfwResult.Success(true))
        assertThat(store.rules(PKG)).isEqualTo(IfwResult.Success(IfwRules.empty()))
    }

    @Test
    fun givenFileRemoved_whenChanges_thenEmitsOnce() = runTest {
        fileSystem.putFile(PKG, LEGACY_XML)

        store.changes.test {
            store.clear(PKG)
            assertThat(awaitItem()).isEqualTo(PKG)

            store.clear(PKG)
            expectNoEvents()
        }
    }

    @Test
    fun givenNoFile_whenClear_thenFalseAndNoEmission() = runTest {
        store.changes.test {
            assertThat(store.clear(PKG)).isEqualTo(IfwResult.Success(false))
            assertThat(store.clear(PKG)).isEqualTo(IfwResult.Success(false))
            expectNoEvents()
        }
    }

    @Test
    fun givenCachedBlocks_whenClear_thenNextBlocksReadsTheFileAgain() = runTest {
        fileSystem.putFile(PKG, LEGACY_XML)
        assertThat(store.blocks(PKG, listOf(MAIN))).containsExactly(MAIN.id, IfwBlock.BLOCKED)

        store.clear(PKG)
        fileSystem.operations.clear()

        assertThat(store.blocks(PKG, listOf(MAIN))).containsExactly(MAIN.id, IfwBlock.NOT_BLOCKED)
        assertThat(fileSystem.operations).containsExactly("peek $PKG")
    }

    @Test
    fun givenWakeFileName_whenClear_thenIllegalArgumentAndFileKept() {
        fileSystem.putFile(WAKE_FILE, CONDITIONAL_MAIN_XML)

        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { store.clear(WAKE_FILE) }
        }
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { store.clear("$WAKE_FILE-com.merxury.blocker.debug") }
        }
        assertThat(fileSystem.fileContent(WAKE_FILE)).isEqualTo(CONDITIONAL_MAIN_XML)
        assertThat(fileSystem.operations).isEmpty()
    }

    @Test
    fun givenFilesUnderNamesOfTheirOwn_whenClear_thenDeleted() = runTest {
        listOf("my-rules", "custom..rules", "1app").forEach { name ->
            fileSystem.putFile(name, DAMAGED_XML)

            assertThat(store.clear(name)).isEqualTo(IfwResult.Success(true))
            assertThat(fileSystem.fileContent(name)).isNull()
        }
    }

    @Test
    fun givenNamesThatLeaveTheDirectory_whenClear_thenIllegalArgument() {
        listOf("", "..", "../x", ".hidden").forEach { name ->
            assertThrows(name, IllegalArgumentException::class.java) {
                runBlocking { store.clear(name) }
            }
        }
        assertThat(fileSystem.operations).isEmpty()
    }

    @Test
    fun givenSingleSegmentPackage_whenClear_thenDeleted() = runTest {
        fileSystem.putFile("android", LEGACY_XML)

        assertThat(store.clear("android")).isEqualTo(IfwResult.Success(true))
        assertThat(fileSystem.fileContent("android")).isNull()
    }

    @Test
    fun givenNoAccess_whenClear_thenNoAccessAndFileKept() = runTest {
        fileSystem.putFile(PKG, LEGACY_XML)
        fileSystem.noAccess = true

        assertThat(store.clear(PKG)).isEqualTo(IfwResult.Failure(IfwFailure.NoAccess))
        assertThat(store.ruleFileNames()).isEqualTo(IfwResult.Failure(IfwFailure.NoAccess))
        assertThat(fileSystem.fileContent(PKG)).isEqualTo(LEGACY_XML)
    }

    @Test
    fun givenDeleteFails_whenClear_thenWriteFailedAndNoEmission() = runTest {
        fileSystem.putFile(PKG, LEGACY_XML)
        fileSystem.writeFailure = IOException("EROFS")

        store.changes.test {
            assertThat(store.clear(PKG)).isEqualTo(IfwResult.Failure(IfwFailure.WriteFailed(PKG, "EROFS")))
            expectNoEvents()
        }
        assertThat(fileSystem.fileContent(PKG)).isEqualTo(LEGACY_XML)
    }

    @Test
    fun givenSetBlockedRunning_whenClear_thenDeletesOnlyAfterTheWrite() = runTest {
        val gate = CompletableDeferred<Unit>()
        fileSystem.beforeRead = { if (!gate.isCompleted) gate.await() }

        val write = launch { store.setBlocked(PKG, mapOf(MAIN to true)) }
        val clear = launch { store.clear(PKG) }
        runCurrent()
        assertThat(fileSystem.operations).containsExactly("read $PKG")
        gate.complete(Unit)
        write.join()
        clear.join()

        assertThat(fileSystem.operations).containsExactly("read $PKG", "write $PKG", "delete $PKG").inOrder()
        assertThat(fileSystem.fileContent(PKG)).isNull()
    }

    @Test
    fun givenClearRunning_whenSetBlocked_thenBuiltOnTheDeletedFile() = runTest {
        fileSystem.putFile(PKG, LEGACY_XML)
        val gate = CompletableDeferred<Unit>()
        fileSystem.beforeRead = { if (!gate.isCompleted) gate.await() }

        // Holds the package's lock in its read, so the clear behind it waits.
        val first = launch { store.setBlocked(PKG, mapOf(SETTINGS to true)) }
        val clear = launch { store.clear(PKG) }
        val second = launch { store.setBlocked(PKG, mapOf(SYNC to false)) }
        runCurrent()
        gate.complete(Unit)
        first.join()
        clear.join()
        second.join()

        assertThat(fileSystem.operations).containsExactly("read $PKG", "write $PKG", "delete $PKG", "read $PKG").inOrder()
        assertThat(fileSystem.fileContent(PKG)).isNull()
    }

    @Test
    fun givenPackageCustomWakeAndHiddenFiles_whenRuleFileNames_thenEveryRuleFileWithoutReading() = runTest {
        fileSystem.putFile(PKG, LEGACY_XML)
        fileSystem.putFile("com.broken", DAMAGED_XML)
        fileSystem.putFile("my-rules", DAMAGED_XML)
        fileSystem.putFile("custom..rules", LEGACY_XML)
        fileSystem.putFile(WAKE_FILE, CONDITIONAL_MAIN_XML)
        fileSystem.putFile(".blocker-ifw-1", LEGACY_XML)

        val names = store.ruleFileNames()

        assertThat((names as IfwResult.Success).value).containsExactly(PKG, "com.broken", "my-rules", "custom..rules")
        assertThat(fileSystem.operations).containsExactly("list")
    }
}
