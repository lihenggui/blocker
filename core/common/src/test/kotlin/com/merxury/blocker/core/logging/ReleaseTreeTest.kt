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

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import timber.log.Timber
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
class ReleaseTreeTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var tree: ReleaseTree

    @Before
    fun setUp() {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        tree = ReleaseTree(
            filesDir = tempFolder.root,
            coroutineScope = scope,
            ioDispatcher = Dispatchers.Default,
        )
        Timber.plant(tree)
        Thread.sleep(500)
    }

    @After
    fun tearDown() {
        Timber.uproot(tree)
    }

    private fun logDir() = tempFolder.root.resolve(LOG_DIR)

    @Test
    fun givenReleaseTree_whenInitialized_thenLogFileIsCreatedInLogsDirectory() {
        Timber.tag("TestTag").i("init message")
        Thread.sleep(500)

        val logDir = logDir()
        assertTrue(logDir.exists())
        val logFiles = logDir.listFiles().orEmpty()
        assertTrue(logFiles.isNotEmpty())
        assertTrue(logFiles.first().name.endsWith(".log"))
    }

    @Test
    fun givenVerbosePriority_whenLogging_thenMessageIsNotWrittenToFile() {
        Timber.tag("TestTag").i("info message")
        Thread.sleep(500)
        Timber.tag("TestTag").v("verbose message")
        Thread.sleep(500)

        val logFile = logDir().listFiles()!!.first()
        val content = logFile.readText()
        assertTrue(content.contains("info message"))
        assertFalse(content.contains("verbose message"))
    }

    @Test
    fun givenDebugPriority_whenLogging_thenMessageIsWrittenWithCorrectFormat() {
        Timber.tag("TestTag").d("debug message")
        Thread.sleep(500)

        val logFile = logDir().listFiles()!!.first()
        val content = logFile.readText()
        assertTrue(content.contains("D/TestTag: debug message"))
    }

    @Test
    fun givenMoreThanSevenLogFiles_whenInitialized_thenOldFilesAreCleaned() {
        val logDir = logDir()
        logDir.mkdirs()

        for (i in 1..10) {
            val file = logDir.resolve("2025-01-${"%02d".format(i)}.log")
            file.writeText("old log $i")
            file.setLastModified(i * 1000L)
        }
        assertEquals(10, logDir.listFiles()!!.size)

        Timber.uproot(tree)

        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        tree = ReleaseTree(
            filesDir = tempFolder.root,
            coroutineScope = scope,
            ioDispatcher = Dispatchers.Default,
        )
        Timber.plant(tree)
        Thread.sleep(500)

        val remainingFiles = logDir.listFiles()!!
        assertTrue(remainingFiles.size <= 8)
    }
}
