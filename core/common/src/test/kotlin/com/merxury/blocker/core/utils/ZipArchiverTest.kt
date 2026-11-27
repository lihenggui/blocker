/*
 * Copyright 2025 Blocker
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

package com.merxury.blocker.core.utils

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.util.zip.ZipException
import java.util.zip.ZipFile

class ZipArchiverTest {
    @get:Rule
    val folder = TemporaryFolder()

    @Test
    fun givenSingleFile_whenZipped_thenCreatesArchiveWithFileContents() = runTest {
        val source = folder.newFile("entry.txt").apply { writeText("one file") }
        val target = folder.root.resolve("cache/out.zip")

        val archive = ZipArchiver.zipFile(source, target, StandardTestDispatcher(testScheduler)).getOrThrow()

        assertThat(archive).isEqualTo(target)
        ZipFile(archive).use {
            assertThat(it.entries().toList().map { entry -> entry.name }).containsExactly("entry.txt")
            assertThat(it.getInputStream(it.getEntry("entry.txt")).bufferedReader().use { reader -> reader.readText() })
                .isEqualTo("one file")
        }
    }

    @Test
    fun givenNestedDirectory_whenZipped_thenUsesRelativePathsWithForwardSlashes() = runTest {
        val source = folder.newFolder("source")
        source.resolve("root.txt").writeText("root")
        source.resolve("nested").mkdir()
        source.resolve("nested/leaf.txt").writeText("leaf")
        val target = folder.root.resolve("source-exports/out.zip")

        val archive = ZipArchiver.zipDirectory(source, target, StandardTestDispatcher(testScheduler)).getOrThrow()

        ZipFile(archive).use {
            assertThat(it.entries().toList().map { entry -> entry.name }).containsExactly("root.txt", "nested/leaf.txt")
        }
    }

    @Test
    fun givenSelectedFilesAndExistingZip_whenZipped_thenReplacesItWithOnlySelectedFiles() = runTest {
        val source = folder.newFolder("source")
        val first = source.resolve("first.txt").apply { writeText("first") }
        source.resolve("extra.txt").writeText("extra")
        source.resolve("nested").mkdir()
        val second = source.resolve("nested/second.txt").apply { writeText("second") }
        val target = folder.newFile("out.zip").apply { writeText("old archive") }

        ZipArchiver.zipFiles(source, listOf(second, first), target, StandardTestDispatcher(testScheduler)).getOrThrow()

        ZipFile(target).use {
            assertThat(it.entries().toList().map { entry -> entry.name })
                .containsExactly("nested/second.txt", "first.txt")
                .inOrder()
        }
        assertThat(folder.root.resolve("out.zip.tmp").exists()).isFalse()
    }

    @Test
    fun givenCancelledWrite_whenClosingZip_thenDeletesTempAndPreservesExistingArchive() = runTest {
        val source = folder.newFolder("source")
        val first = source.resolve("first.txt").apply { writeText("first entry") }
        val second = source.resolve("second.txt").apply { writeText("second entry") }
        val target = folder.newFile("out.zip").apply { writeText("previous export") }
        val temp = folder.root.resolve("out.zip.tmp")
        val writer =
            launch {
                val cancelledEntry =
                    object : File(second.path) {
                        override fun getCanonicalFile(): File = this

                        override fun lastModified(): Long {
                            assertThat(temp.exists()).isTrue()
                            cancel("Cancel after writing the first entry")
                            return super.lastModified()
                        }
                    }
                ZipArchiver.zipFiles(source, listOf(first, cancelledEntry), target, StandardTestDispatcher(testScheduler))
                throw AssertionError("Expected ZIP creation to propagate cancellation")
            }

        writer.join()

        assertThat(writer.isCancelled).isTrue()
        assertThat(temp.exists()).isFalse()
        assertThat(target.readText()).isEqualTo("previous export")
    }

    @Test
    fun givenMissingOrWrongSourceType_whenZipped_thenReturnsFailure() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val target = folder.root.resolve("out.zip")
        val directory = folder.newFolder("source")
        val file = folder.newFile("source.txt")

        assertThat(ZipArchiver.zipFile(folder.root.resolve("missing.txt"), target, dispatcher).exceptionOrNull())
            .isInstanceOf(IllegalArgumentException::class.java)
        assertThat(ZipArchiver.zipFile(directory, target, dispatcher).exceptionOrNull())
            .isInstanceOf(IllegalArgumentException::class.java)
        assertThat(ZipArchiver.zipDirectory(file, target, dispatcher).exceptionOrNull())
            .isInstanceOf(IllegalArgumentException::class.java)
        assertThat(target.exists()).isFalse()
    }

    @Test
    fun givenOutsideFileAndSymlink_whenZipped_thenRejectsBothPaths() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val base = folder.newFolder("source")
        val outside = folder.newFolder("source-extra").resolve("outside.txt").apply { writeText("outside") }
        val symlink = base.resolve("link.txt")
        Files.createSymbolicLink(symlink.toPath(), outside.toPath())
        val target = folder.root.resolve("out.zip")

        val outsidePath = base.resolve("../source-extra/outside.txt")
        assertThat(ZipArchiver.zipFiles(base, listOf(outsidePath), target, dispatcher).exceptionOrNull())
            .hasMessageThat().contains("outside the base directory")
        assertThat(ZipArchiver.zipDirectory(base, target, dispatcher).exceptionOrNull())
            .hasMessageThat().contains("outside the base directory")
        assertThat(target.exists()).isFalse()
    }

    @Test
    fun givenTemporaryPathLinksToDirectory_whenZipped_thenReplacesLinkAndPreservesDirectory() = runTest {
        val source = folder.newFile("source.txt")
        val target = folder.root.resolve("out.zip")
        val temp = folder.root.resolve("out.zip.tmp")
        val linkedDirectory = folder.newFolder("keep")
        val marker = linkedDirectory.resolve("marker.txt").apply { writeText("keep") }
        Files.createSymbolicLink(temp.toPath(), linkedDirectory.toPath())

        val archive = ZipArchiver.zipFile(source, target, StandardTestDispatcher(testScheduler)).getOrThrow()

        ZipFile(archive).use {
            assertThat(it.entries().toList().map { entry -> entry.name }).containsExactly("source.txt")
        }
        assertThat(Files.isSymbolicLink(temp.toPath())).isFalse()
        assertThat(temp.exists()).isFalse()
        assertThat(marker.readText()).isEqualTo("keep")
    }

    @Test
    fun givenTargetIsDirectory_whenZipped_thenCleansUpTempAndPreservesTarget() = runTest {
        val source = folder.newFile("source.txt")
        val target = folder.newFolder("out.zip")
        val marker = target.resolve("marker.txt").apply { writeText("keep") }

        val result = ZipArchiver.zipFile(source, target, StandardTestDispatcher(testScheduler))

        assertThat(result.exceptionOrNull()).isInstanceOf(IOException::class.java)
        assertThat(folder.root.resolve("out.zip.tmp").exists()).isFalse()
        assertThat(marker.readText()).isEqualTo("keep")
    }

    @Test
    fun givenDuplicateEntry_whenZipped_thenDeletesPartialTempAndPreservesTarget() = runTest {
        val source = folder.newFile("source.txt")
        val target = folder.newFile("out.zip").apply { writeText("previous export") }

        val result = ZipArchiver.zipFiles(folder.root, listOf(source, source), target, StandardTestDispatcher(testScheduler))

        assertThat(result.exceptionOrNull()).isInstanceOf(ZipException::class.java)
        assertThat(folder.root.resolve("out.zip.tmp").exists()).isFalse()
        assertThat(target.readText()).isEqualTo("previous export")
    }

    @Test
    fun givenMixedCacheFiles_whenPruned_thenRemovesOnlyMatchingFilesExceptKeep() {
        folder.newFile("rules-old.zip")
        val keep = folder.newFile("rules-current.zip")
        folder.newFile("rules-preview.txt")
        folder.newFile("other.zip")
        folder.newFolder("rules-directory.zip")

        ZipArchiver.pruneMatchingFiles(folder.root, "rules-", ".zip", keep)

        assertThat(folder.root.list().orEmpty().asList())
            .containsExactly("rules-current.zip", "rules-preview.txt", "other.zip", "rules-directory.zip")
    }
}
