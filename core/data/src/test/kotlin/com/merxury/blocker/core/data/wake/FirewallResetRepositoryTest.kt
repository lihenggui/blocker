// Copyright 2026 Blocker
// SPDX-License-Identifier: Apache-2.0

package com.merxury.blocker.core.data.wake

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import com.merxury.blocker.core.testing.controller.FakeIfwFileSystem
import com.merxury.core.ifw.IIntentFirewall
import com.merxury.core.ifw.ManagedWakeReset
import com.merxury.core.ifw.WakeResetTransport
import com.merxury.core.ifw.di.IfwModule
import com.merxury.core.ifw.xml.IfwXmlDeserializer
import com.merxury.core.ifw.xml.IfwXmlSerializer
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.io.IOException

private const val VALID_XML = """<rules><activity block="true" log="false"><component-filter name="a.pkg/a.pkg.Main" /></activity></rules>"""
private const val DAMAGED_XML = """<rules><activity block="true"><component-filter name="""
private const val WAKE_FILE = "blocker-managed-wake"

class FirewallResetRepositoryTest {
    private val authorization = mock<WakeAuthorizationRepository>()
    private val wakeResets = mutableListOf<Pair<String?, WakeResetTransport>>()
    private val files = FakeIfwFileSystem()

    /** The package file operations done before the wake feature reset its files. */
    private var operationsBeforeWakeReset: List<String>? = null

    private val managed = ManagedWakeReset { target, transport ->
        wakeResets += target to transport
        operationsBeforeWakeReset = files.operations.toList()
        true
    }
    private val firewall = mock<IIntentFirewall>()
    private val repository = FirewallResetRepository(
        authorization,
        managed,
        IfwModule.providesIfwStore(files, IfwXmlSerializer(), IfwXmlDeserializer()),
        firewall,
    )

    @Test
    fun givenWakeAccessAndNoPackageFiles_whenReset_thenZeroAndWakeRulesReset() = runTest {
        whenever(authorization.resetTransport()).thenReturn(WakeResetTransport.SHIZUKU_ROOT)

        assertThat(repository.reset(null) { _, _, _ -> error("unexpected package file deletion") }).isEqualTo(0)

        assertThat(wakeResets).containsExactly(null to WakeResetTransport.SHIZUKU_ROOT)
        assertThat(files.operations).containsExactly("list")
        verify(firewall).resetCache()
    }

    @Test
    fun givenGrantObtainedWhileDeletingPackageFiles_whenReset_thenWakeFilesResetToo() = runTest {
        // Like Shizuku: the wake feature can reach its files only once the store has connected.
        whenever(authorization.resetTransport()).thenAnswer { if (files.operations.isEmpty()) null else WakeResetTransport.SHIZUKU_ROOT }
        files.putFile("a.pkg", VALID_XML)

        assertThat(repository.reset(null) { _, _, _ -> }).isEqualTo(1)

        assertThat(wakeResets).containsExactly(null to WakeResetTransport.SHIZUKU_ROOT)
        assertThat(operationsBeforeWakeReset).containsExactly("list", "delete a.pkg").inOrder()
    }

    @Test
    fun givenFilesUnderNamesOfTheirOwn_whenResetAll_thenDeletedWithPackageFiles() = runTest {
        whenever(authorization.resetTransport()).thenReturn(WakeResetTransport.DIRECT_ROOT)
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
        whenever(authorization.resetTransport()).thenReturn(WakeResetTransport.DIRECT_ROOT)
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
        whenever(authorization.resetTransport()).thenReturn(WakeResetTransport.DIRECT_ROOT)
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
        assertThat(wakeResets).containsExactly(null to WakeResetTransport.DIRECT_ROOT)
        assertThat(operationsBeforeWakeReset).containsExactly("list", "delete a.pkg", "delete b.pkg").inOrder()
        verify(firewall).resetCache()
    }

    @Test
    fun givenTarget_whenReset_thenOnlyItsFileDeletedWithoutListing() = runTest {
        whenever(authorization.resetTransport()).thenReturn(WakeResetTransport.DIRECT_ROOT)
        files.putFile("a.pkg", DAMAGED_XML)
        files.putFile("b.pkg", VALID_XML)

        assertThat(repository.reset("a.pkg") { _, _, _ -> }).isEqualTo(1)

        assertThat(files.fileContent("a.pkg")).isNull()
        assertThat(files.fileContent("b.pkg")).isEqualTo(VALID_XML)
        assertThat(files.operations).containsExactly("delete a.pkg")
        assertThat(wakeResets).containsExactly("a.pkg" to WakeResetTransport.DIRECT_ROOT)
    }

    @Test
    fun givenTargetWithoutFile_whenReset_thenNothingRemoved() = runTest {
        whenever(authorization.resetTransport()).thenReturn(WakeResetTransport.DIRECT_ROOT)

        assertThat(repository.reset("a.pkg") { _, _, _ -> }).isEqualTo(0)
    }

    @Test
    fun givenNoWakeAccess_whenReset_thenPackageFilesStillDeletedThroughTheStore() = runTest {
        whenever(authorization.resetTransport()).thenReturn(null)
        files.putFile("a.pkg", VALID_XML)

        assertThat(repository.reset(null) { _, _, _ -> }).isEqualTo(1)

        assertThat(wakeResets).isEmpty()
        assertThat(files.fileContent("a.pkg")).isNull()
        verify(firewall).resetCache()
    }

    @Test
    fun givenNoAccessToRuleDirectory_whenReset_thenNullAndFilesKept() = runTest {
        whenever(authorization.resetTransport()).thenReturn(null)
        files.putFile("a.pkg", VALID_XML)
        files.noAccess = true

        assertThat(repository.reset(null) { _, _, _ -> error("unexpected package file deletion") }).isNull()
        assertThat(repository.reset("a.pkg") { _, _, _ -> }).isNull()

        files.noAccess = false
        assertThat(files.fileContent("a.pkg")).isEqualTo(VALID_XML)
    }

    @Test
    fun givenSecondDeletionFails_whenReset_thenFailurePropagatesAndCacheCleared() = runTest {
        whenever(authorization.resetTransport()).thenReturn(WakeResetTransport.DIRECT_ROOT)
        files.putFile("a.pkg", VALID_XML)
        files.putFile("b.pkg", VALID_XML)

        val result = runCatching {
            repository.reset(null) { _, index, _ -> if (index == 2) files.writeFailure = IOException("EROFS") }
        }

        assertThat(result.exceptionOrNull()).isInstanceOf(IOException::class.java)
        assertThat(files.fileContent("a.pkg")).isNull()
        assertThat(files.fileContent("b.pkg")).isEqualTo(VALID_XML)
        assertThat(wakeResets).containsExactly(null to WakeResetTransport.DIRECT_ROOT)
        verify(firewall).resetCache()
    }

    @Test
    fun givenNoAccess_whenReset_thenNullAndWakeFilesStillReset() = runTest {
        whenever(authorization.resetTransport()).thenReturn(WakeResetTransport.SHIZUKU_ROOT)
        files.noAccess = true

        assertThat(repository.reset("a.pkg") { _, _, _ -> }).isNull()
        assertThat(repository.reset(null) { _, _, _ -> }).isNull()

        assertThat(wakeResets).containsExactly("a.pkg" to WakeResetTransport.SHIZUKU_ROOT, null to WakeResetTransport.SHIZUKU_ROOT).inOrder()
        verify(firewall, times(2)).resetCache()
    }
}
