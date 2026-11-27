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

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import timber.log.Timber
import java.io.RandomAccessFile
import kotlin.time.Clock

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class ReleaseTreeTest {
    @get:Rule
    val tempFolder = TemporaryFolder()

    private val logDir get() = tempFolder.root.resolve(LOG_DIR)
    private lateinit var tree: ReleaseTree

    @After
    fun tearDown() {
        Timber.uproot(tree)
    }

    @Test
    fun givenQueuedMessages_whenWriterStarts_thenWritesSequentiallyAndSkipsVerboseLogs() = runTest {
        val tree = plantTree()
        repeat(20) { Timber.i("entry-$it") }
        Timber.v("verbose entry")
        assertThat(logDir.exists()).isFalse()

        tree.start()
        runCurrent()

        assertThat(logDir.resolve("2025-05-08.log").readLines().map { it.substringAfter(" I/") })
            .containsExactlyElementsIn(List(20) { "Blocker: entry-$it" })
            .inOrder()
    }

    @Test
    fun givenOverflowingQueue_whenWriterStarts_thenWritesMostRecent256Entries() = runTest {
        val tree = plantTree()
        repeat(260) { Timber.i("entry-$it") }

        tree.start()
        runCurrent()

        val entries = logDir.resolve("2025-05-08.log").readLines().map { it.substringAfter("entry-").toInt() }
        assertThat(entries).containsExactlyElementsIn((4 until 260).toList()).inOrder()
    }

    @Test
    fun givenThrowable_whenLogged_thenWritesItsOriginalStackTraceOnce() = runTest {
        val tree = plantTree()
        val error = IllegalStateException("test exception")
        val stack = error.stackTraceToString()
        Timber.tag("TestTag").e(error, "test error")
        error.stackTrace = emptyArray()

        tree.start()
        runCurrent()

        val content = logDir.resolve("2025-05-08.log").readText()
        assertThat(content.substringAfter(" E/")).isEqualTo("TestTag: test error\n$stack")
    }

    @Test
    fun givenEntriesAcrossMidnight_whenDrainedTogether_thenRotatesByTimestampAndCleansLogs() = runTest {
        val clock = TestClock("2025-05-08T23:59:59")
        val tree = plantTree(clock)
        logDir.mkdirs()
        val expiring = logDir.resolve("2025-05-01.log").apply { writeText("expires tomorrow") }
        tree.start()
        runCurrent()
        assertThat(expiring.exists()).isTrue()

        Timber.i("before midnight")
        clock.localTime = "2025-05-09T00:00:00"
        Timber.i("after midnight")
        runCurrent()

        assertThat(logDir.resolve("2025-05-08.log").readLines().single()).endsWith("before midnight")
        assertThat(logDir.resolve("2025-05-09.log").readLines().single()).endsWith("after midnight")
        assertThat(expiring.exists()).isFalse()
    }

    @Test
    fun givenDatedFiles_whenWriterStarts_thenRemovesLogsOlderThanSevenDays() = runTest {
        val tree = plantTree()
        logDir.mkdirs()
        listOf("2025-04-30.log", "2025-04-30.log.1", "2025-05-01.log", "2025-05-08.log", "notes.txt")
            .forEach { logDir.resolve(it).writeText(it) }

        tree.start()
        runCurrent()

        assertThat(logDir.list().orEmpty().asList()).containsExactly("2025-05-01.log", "2025-05-08.log", "notes.txt")
    }

    @Test
    fun givenFullLogFile_whenWritten_thenReplacesPreviousRotationAndBoundsBothFiles() = runTest {
        val tree = plantTree()
        logDir.mkdirs()
        val active = logDir.resolve("2025-05-08.log").apply { writeText("just filled") }
        val rotated = logDir.resolve("2025-05-08.log.1").apply { writeText("older rotation") }
        val maxSize = 5L * 1024 * 1024
        RandomAccessFile(active, "rw").use { it.setLength(maxSize) }

        tree.start()
        Timber.i("new entry")
        runCurrent()

        assertThat(active.readLines().single()).endsWith("new entry")
        assertThat(rotated.length()).isEqualTo(maxSize)
        assertThat(rotated.readText()).startsWith("just filled")
    }

    @Test
    fun givenOversizedEntry_whenWritten_thenTruncatesToTheByteLimit() = runTest {
        val tree = plantTree()

        tree.start()
        Timber.i("é".repeat(3 * 1024 * 1024))
        runCurrent()

        val active = logDir.resolve("2025-05-08.log")
        assertThat(active.length()).isAtMost(5L * 1024 * 1024)
        val content = active.readText()
        assertThat(content).endsWith("[log entry truncated]\n")
        assertThat(content).doesNotContain("�")
    }

    @Test
    fun givenRepeatedStartCalls_whenScheduled_thenStartsOnlyOneWriter() = runTest {
        val clock = TestClock("2025-05-08T12:00:00")
        val tree = plantTree(clock)

        repeat(3) { tree.start() }
        runCurrent()

        assertThat(clock.reads).isEqualTo(1)
    }

    @Test
    fun givenTemporaryWriteFailure_whenLoggingAgain_thenWriterRecovers() = runTest {
        val tree = plantTree()
        logDir.writeText("file blocks creation of log directory")
        tree.start()
        Timber.i("should fail")
        runCurrent()

        assertThat(logDir.delete()).isTrue()
        Timber.i("recovered")
        runCurrent()

        assertThat(logDir.resolve("2025-05-08.log").readLines().single()).endsWith("recovered")
    }

    private fun TestScope.plantTree(clock: TestClock = TestClock("2025-05-08T12:00:00")): ReleaseTree {
        tree = ReleaseTree(tempFolder.root, backgroundScope, StandardTestDispatcher(testScheduler), clock)
        Timber.plant(tree)
        return tree
    }

    private class TestClock(
        var localTime: String,
    ) : Clock {
        var reads = 0
            private set

        override fun now() = LocalDateTime
            .parse(localTime)
            .toInstant(TimeZone.currentSystemDefault())
            .also { reads++ }
    }
}
