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

package com.merxury.blocker.core.domain

import com.google.common.truth.Truth.assertThat
import com.merxury.blocker.core.logging.LOG_DIR
import com.merxury.blocker.core.logging.LogFileLock
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.util.zip.ZipFile
import kotlin.time.Duration.Companion.hours

class ZipLogFileUseCaseTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val filesDir get() = folder.root.resolve("files")
    private val cacheDir get() = folder.root.resolve("cache")
    private val logs get() = filesDir.resolve(LOG_DIR)

    @Test
    fun givenCurrentAndRotatedLogs_whenExported_thenArchivesBothAndPrunesOnlyOldLogZips() = runTest {
        logs.mkdirs()
        logs.resolve("2025-05-08.log").writeText("active")
        logs.resolve("2025-05-08.log.1").writeText("rotated")
        cacheDir.mkdir()
        val now = System.currentTimeMillis()
        val stale = cacheDir.resolve("Blocker-log-old.zip").apply { writeText("stale") }
        val recent = cacheDir.resolve("Blocker-log-recent.zip").apply { writeText("shared") }
        val ruleZip = cacheDir.resolve("all-rules-old.zip").apply { writeText("keep") }
        assertThat(stale.setLastModified(now - 25.hours.inWholeMilliseconds)).isTrue()
        assertThat(recent.setLastModified(now - 23.hours.inWholeMilliseconds)).isTrue()
        assertThat(ruleZip.setLastModified(now - 25.hours.inWholeMilliseconds)).isTrue()

        val useCase = createUseCase()
        val result = checkNotNull(useCase())

        ZipFile(result).use {
            assertThat(it.entries().toList().map { entry -> entry.name })
                .containsExactly("2025-05-08.log", "2025-05-08.log.1")
        }
        assertThat(stale.exists()).isFalse()
        assertThat(recent.readText()).isEqualTo("shared")
        assertThat(ruleZip.exists()).isTrue()
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun givenLogRotationInProgress_whenExported_thenWaitsForTheCompletedRotation() = runTest {
        logs.mkdirs()
        val active = logs.resolve("2025-05-08.log").apply { writeText("old entry") }
        val rotated = logs.resolve("2025-05-08.log.1")
        val useCase = createUseCase()

        val export = LogFileLock.mutex.withLock {
            val pending = async { useCase() }
            runCurrent()
            assertThat(pending.isCompleted).isFalse()
            assertThat(active.renameTo(rotated)).isTrue()
            active.writeText("new entry")
            pending
        }

        ZipFile(checkNotNull(export.await())).use { archive ->
            val contents = archive.entries().toList().associate { entry ->
                entry.name to archive.getInputStream(entry).bufferedReader().use { it.readText() }
            }
            assertThat(contents).containsExactly(active.name, "new entry", rotated.name, "old entry")
        }
    }

    @Test
    fun givenMissingLogDirectory_whenExported_thenReturnsNull() = runTest {
        val useCase = createUseCase()

        assertThat(useCase()).isNull()
        assertThat(cacheDir.exists()).isFalse()
    }

    @Test
    fun givenLogsAndUnwritableCachePath_whenExported_thenReturnsNull() = runTest {
        logs.mkdirs()
        logs.resolve("2025-05-08.log").writeText("active")
        cacheDir.writeText("regular file")

        val useCase = createUseCase()

        assertThat(useCase()).isNull()
    }

    private fun TestScope.createUseCase() = ZipLogFileUseCase(cacheDir, filesDir, StandardTestDispatcher(testScheduler))
}
