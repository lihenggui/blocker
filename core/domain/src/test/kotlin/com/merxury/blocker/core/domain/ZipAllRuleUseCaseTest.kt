/*
 * Copyright 2023 Blocker
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

package com.merxury.blocker.core.domain

import com.merxury.blocker.core.testing.util.MainDispatcherRule
import junit.framework.TestCase.assertEquals
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.zip.ZipFile
import kotlin.test.assertTrue

class ZipAllRuleUseCaseTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @get:Rule
    val folder: TemporaryFolder = TemporaryFolder.builder()
        .assureDeletion()
        .build()

    private lateinit var filesDir: File
    private lateinit var cacheDir: File
    private val ruleBaseFolder = "blocker-general-rule"

    private lateinit var useCase: ZipAllRuleUseCase

    @Before
    fun setUp() {
        filesDir = folder.newFolder("filesDir")
        cacheDir = folder.newFolder("cacheDir")
        useCase = ZipAllRuleUseCase(
            cacheDir = cacheDir,
            filesDir = filesDir,
            ruleBaseFolder = ruleBaseFolder,
            ioDispatcher = Dispatchers.IO,
        )
    }

    @Test
    fun whenNoFilesExist_returnNull() = runTest {
        // Do not create files before running the test
        val baseFolder = filesDir.resolve(ruleBaseFolder)
        if (baseFolder.exists()) {
            baseFolder.deleteRecursively()
        }
        val result = useCase().first()
        assertEquals(null, result)
    }

    @Test
    fun whenOneFileInRuleFolder_returnFileWithExactlyOneFile() = runTest {
        val baseFolder = filesDir.resolve(ruleBaseFolder)
        if (baseFolder.exists()) {
            baseFolder.deleteRecursively()
        }
        baseFolder.mkdirs()

        val file = baseFolder.resolve("test.txt")
        file.createNewFile()

        val zipFile = useCase().first()?.let {
            ZipFile(it)
        } ?: throw AssertionError("Zip file should not be null")
        assertEquals(1, zipFile.size())
        assertEquals(
            File.separator + ruleBaseFolder + File.separator + "test.txt",
            zipFile.entries().nextElement().name,
        )
        zipFile.close()
    }

    @Test
    fun when5FilesInRuleFolder_returnFileWithExactly5Files() = runTest {
        val baseFolder = filesDir.resolve(ruleBaseFolder)
        if (baseFolder.exists()) {
            baseFolder.deleteRecursively()
        }
        baseFolder.mkdirs()
        for (i in 1..5) {
            val file = baseFolder.resolve("test$i.txt")
            file.createNewFile()
        }

        val zipFile = useCase().first()?.let {
            ZipFile(it)
        } ?: throw AssertionError("Zip file should not be null")
        // Check file size
        assertEquals(5, zipFile.size())
        // Verify file names
        val filesInZip = zipFile.entries().toList()
        for (i in 1..5) {
            val fileName = File.separator + ruleBaseFolder + File.separator + "test$i.txt"
            assertTrue(
                filesInZip.any { it.name == fileName },
                "File $fileName should exist in zip file",
            )
        }
        zipFile.close()
    }

    @Test
    fun whenContainingFolders_returnFileWithExactSameStructure() = runTest {
        val baseFolder = filesDir.resolve(ruleBaseFolder)
        if (baseFolder.exists()) {
            baseFolder.deleteRecursively()
        }
        baseFolder.mkdirs()
        for (i in 1..5) {
            val folder = baseFolder.resolve("folder$i")
            folder.mkdirs()
            val file = folder.resolve("test$i.txt")
            file.createNewFile()
        }

        val zipFile = useCase().first()?.let {
            ZipFile(it)
        } ?: throw AssertionError("Zip file should not be null")
        // Check file size
        assertEquals(5, zipFile.size())
        // Verify file names
        val filesInZip = zipFile.entries().toList()
        for (i in 1..zipFile.size()) {
            val fileName = File.separator + ruleBaseFolder + File.separator + "folder$i" + File.separator + "test$i.txt"
            assertTrue(
                filesInZip.any { it.name == fileName },
                "File $fileName should exist in zip file",
            )
        }
        zipFile.close()
    }
}
