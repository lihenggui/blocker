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

package com.merxury.blocker.core.logging

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import timber.log.Timber
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class ReleaseTreeTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val testDispatcher = UnconfinedTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    private lateinit var tree: ReleaseTree

    @Before
    fun setUp() {
        tree = ReleaseTree(
            filesDir = tempFolder.root,
            coroutineScope = testScope,
            ioDispatcher = testDispatcher,
        )
        Timber.plant(tree)
    }

    @After
    fun tearDown() {
        Timber.uproot(tree)
    }

    private fun logDir() = tempFolder.root.resolve(LOG_DIR)

    @Test
    fun givenReleaseTree_whenInitialized_thenLogFileIsCreatedInLogsDirectory() = testScope.runTest {
        Timber.tag("TestTag").i("init message")
        testScheduler.advanceUntilIdle()

        val logDir = logDir()
        assertTrue(logDir.exists())
        val logFiles = logDir.listFiles().orEmpty()
        assertTrue(logFiles.isNotEmpty())
        assertTrue(logFiles.first().name.endsWith(".log"))
    }

    @Test
    fun givenVerbosePriority_whenLogging_thenMessageIsNotWrittenToFile() = testScope.runTest {
        Timber.tag("TestTag").i("info message")
        testScheduler.advanceUntilIdle()
        Timber.tag("TestTag").v("verbose message")
        testScheduler.advanceUntilIdle()

        val logFile = logDir().listFiles()!!.first()
        val content = logFile.readText()
        assertTrue(content.contains("info message"))
        assertFalse(content.contains("verbose message"))
    }

    @Test
    fun givenDebugPriority_whenLogging_thenMessageIsWrittenWithCorrectFormat() = testScope.runTest {
        Timber.tag("TestTag").d("debug message")
        testScheduler.advanceUntilIdle()

        val logFile = logDir().listFiles()!!.first()
        val content = logFile.readText()
        assertTrue(content.contains("D/TestTag: debug message"))
    }

    @Test
    fun givenMoreThanSevenLogFiles_whenInitialized_thenOldFilesAreCleaned() = testScope.runTest {
        val logDir = logDir()
        logDir.mkdirs()

        for (i in 1..10) {
            val file = logDir.resolve("2025-01-${"%02d".format(i)}.log")
            file.writeText("old log $i")
            file.setLastModified(i * 1000L)
        }

        Timber.uproot(tree)

        tree = ReleaseTree(
            filesDir = tempFolder.root,
            coroutineScope = testScope,
            ioDispatcher = testDispatcher,
        )
        Timber.plant(tree)
        testScheduler.advanceUntilIdle()

        val remainingFiles = logDir.listFiles()!!
        assertTrue(remainingFiles.size <= 8)
    }
}
