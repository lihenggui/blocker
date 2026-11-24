// Copyright 2026 Blocker
// SPDX-License-Identifier: Apache-2.0

package com.merxury.blocker.core.data.wake

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import com.merxury.blocker.core.testing.controller.FakeIfwFileSystem
import com.merxury.core.ifw.ManagedWakeReset
import com.merxury.core.ifw.di.IfwModule
import com.merxury.core.ifw.xml.IfwXmlDeserializer
import com.merxury.core.ifw.xml.IfwXmlSerializer
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.io.IOException

private const val VALID_XML = """<rules><activity block="true" log="false"><component-filter name="a.pkg/a.pkg.Main" /></activity></rules>"""
private const val DAMAGED_XML = """<rules><activity block="true"><component-filter name="""
private const val WAKE_FILE = "blocker-managed-wake"

class FirewallResetRepositoryTest {
    private val wakeResets = mutableListOf<String?>()
    private val files = FakeIfwFileSystem()

    /** The package file operations done before the wake feature reset its files. */
    private var operationsBeforeWakeReset: List<String>? = null

    /** Whether the channel may write the wake feature's files, as the wake feature answers. */
    private var wakeCanWrite = true
    private val managed = ManagedWakeReset { target ->
        wakeResets += target
        operationsBeforeWakeReset = files.operations.toList()
        wakeCanWrite
    }
    private val repository = FirewallResetRepository(
        managed,
        IfwModule.providesIfwStore(files, IfwXmlSerializer(), IfwXmlDeserializer()),
    )

    @Test
    fun givenWakeAccessAndNoPackageFiles_whenReset_thenZeroAndWakeRulesReset() = runTest {
        assertThat(repository.reset(null) { _, _, _ -> error("unexpected package file deletion") }).isEqualTo(0)

        assertThat(wakeResets).containsExactly(null)
        assertThat(files.operations).containsExactly("list")
    }

    @Test
    fun givenFilesUnderNamesOfTheirOwn_whenResetAll_thenDeletedWithPackageFiles() = runTest {
        files.putFile("b.pkg", DAMAGED_XML)
        files.putFile("android", VALID_XML)
        files.putFile("my-rules", VALID_XML)
        files.putFile("custom..rules", DAMAGED_XML)

        assertThat(repository.reset(null) { _, _, _ -> }).isEqualTo(4)

        listOf("b.pkg", "android", "my-rules", "custom..rules").forEach {
            assertWithMessage(it).that(files.fileContent(it)).isNull()
        }
    }

    @Test
    fun givenFilesUnderNamesOfTheirOwn_whenResetOnePackage_thenUntouched() = runTest {
        files.putFile("android", DAMAGED_XML)
        files.putFile("my-rules", VALID_XML)
        files.putFile("custom..rules", VALID_XML)

        assertThat(repository.reset("android") { _, _, _ -> }).isEqualTo(1)

        assertThat(files.fileContent("android")).isNull()
        assertThat(files.fileContent("my-rules")).isEqualTo(VALID_XML)
        assertThat(files.fileContent("custom..rules")).isEqualTo(VALID_XML)
        assertThat(files.operations).containsExactly("delete android")
    }

    @Test
    fun givenDamagedAndValidFiles_whenResetAll_thenBothDeletedAndWakeFileLeftToTheWakeReset() = runTest {
        files.putFile("a.pkg", VALID_XML)
        files.putFile("b.pkg", DAMAGED_XML)
        files.putFile(WAKE_FILE, VALID_XML)
        val progress = mutableListOf<String>()

        val removed = repository.reset(null) { pkg, index, total -> progress += "$pkg $index/$total" }

        assertThat(removed).isEqualTo(2)
        assertThat(progress).containsExactly("a.pkg 1/2", "b.pkg 2/2").inOrder()
        assertThat(files.fileContent("a.pkg")).isNull()
        assertThat(files.fileContent("b.pkg")).isNull()
        assertThat(files.fileContent(WAKE_FILE)).isEqualTo(VALID_XML)
        assertThat(files.operations).containsExactly("list", "delete a.pkg", "delete b.pkg").inOrder()
        assertThat(wakeResets).containsExactly(null)
        assertThat(operationsBeforeWakeReset).containsExactly("list", "delete a.pkg", "delete b.pkg").inOrder()
    }

    @Test
    fun givenTarget_whenReset_thenOnlyItsFileDeletedWithoutListing() = runTest {
        files.putFile("a.pkg", DAMAGED_XML)
        files.putFile("b.pkg", VALID_XML)

        assertThat(repository.reset("a.pkg") { _, _, _ -> }).isEqualTo(1)

        assertThat(files.fileContent("a.pkg")).isNull()
        assertThat(files.fileContent("b.pkg")).isEqualTo(VALID_XML)
        assertThat(files.operations).containsExactly("delete a.pkg")
        assertThat(wakeResets).containsExactly("a.pkg")
    }

    @Test
    fun givenTargetWithoutFile_whenReset_thenNothingRemoved() = runTest {
        assertThat(repository.reset("a.pkg") { _, _, _ -> }).isEqualTo(0)
    }

    @Test
    fun givenChannelCannotWriteWakeFiles_whenReset_thenPackageFilesStillDeletedThroughTheStore() = runTest {
        wakeCanWrite = false
        files.putFile("a.pkg", VALID_XML)

        assertThat(repository.reset(null) { _, _, _ -> }).isEqualTo(1)

        assertThat(wakeResets).containsExactly(null)
        assertThat(files.fileContent("a.pkg")).isNull()
    }

    @Test
    fun givenNoAccessToRuleDirectory_whenReset_thenNullAndFilesKept() = runTest {
        wakeCanWrite = false
        files.putFile("a.pkg", VALID_XML)
        files.noAccess = true

        assertThat(repository.reset(null) { _, _, _ -> error("unexpected package file deletion") }).isNull()
        assertThat(repository.reset("a.pkg") { _, _, _ -> }).isNull()

        files.noAccess = false
        assertThat(files.fileContent("a.pkg")).isEqualTo(VALID_XML)
    }

    @Test
    fun givenSecondDeletionFails_whenReset_thenFailurePropagatesAndWakeFilesAreReset() = runTest {
        files.putFile("a.pkg", VALID_XML)
        files.putFile("b.pkg", VALID_XML)

        val result = runCatching {
            repository.reset(null) { _, index, _ -> if (index == 2) files.writeFailure = IOException("EROFS") }
        }

        assertThat(result.exceptionOrNull()).isInstanceOf(IOException::class.java)
        assertThat(files.fileContent("a.pkg")).isNull()
        assertThat(files.fileContent("b.pkg")).isEqualTo(VALID_XML)
        assertThat(wakeResets).containsExactly(null)
    }

    @Test
    fun givenNoAccess_whenReset_thenNullAndWakeResetAskedAnyway() = runTest {
        wakeCanWrite = false
        files.noAccess = true

        assertThat(repository.reset("a.pkg") { _, _, _ -> }).isNull()
        assertThat(repository.reset(null) { _, _, _ -> }).isNull()

        assertThat(wakeResets).containsExactly("a.pkg", null).inOrder()
    }
}
