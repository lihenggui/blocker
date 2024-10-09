/*
 * Copyright 2024 Blocker
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

package com.merxury.blocker.core.hosts.parser

import com.merxury.blocker.core.hosts.parser.model.HostEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

class FileHostsParserTest {

    private val testDispatcher: TestDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun givenNonExistentFile_whenParse_thenReturnEmptyList() = runTest {
        val tempFile = File.createTempFile("hosts", ".txt")
        val parser = FileHostsParser(tempFile, testDispatcher)
        val result = parser.parse()

        assertTrue(result.isEmpty())
    }

    @Test
    fun givenValidHostsFile_whenParse_thenReturnCorrectHostEntries() = runTest {
        val tempFile = File.createTempFile("hosts", ".txt").apply {
            writeText("127.0.0.1 localhost\n192.168.1.1 example.com # comment")
        }

        val parser = FileHostsParser(tempFile, testDispatcher)
        val result = parser.parse()

        val expected = listOf(
            HostEntry("127.0.0.1", listOf("localhost"), null),
            HostEntry("192.168.1.1", listOf("example.com"), "comment"),
        )

        assertEquals(expected, result)
    }

    @Test
    fun givenLinesWithOnlyComments_whenParse_thenIgnoreCommentLines() = runTest {
        val tempFile = File.createTempFile("hosts", ".txt").apply {
            writeText("# This is a comment\n# Another comment")
        }

        val parser = FileHostsParser(tempFile, testDispatcher)
        val result = parser.parse()

        assertTrue(result.isEmpty())
    }

    @Test
    fun givenLinesWithMultipleHostnames_whenParse_thenReturnCorrectHostEntries() = runTest {
        val tempFile = File.createTempFile("hosts", ".txt").apply {
            writeText("127.0.0.1 localhost localdomain")
        }

        val parser = FileHostsParser(tempFile, testDispatcher)
        val result = parser.parse()

        val expected = listOf(
            HostEntry("127.0.0.1", listOf("localhost", "localdomain"), null),
        )

        assertEquals(expected, result)
    }
}
