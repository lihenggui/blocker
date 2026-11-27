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
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.util.zip.ZipFile

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
        val stale = cacheDir.resolve("Blocker-log-old.zip").apply { writeText("stale") }
        val ruleZip = cacheDir.resolve("rules-old.zip").apply { writeText("keep") }

        val useCase = createUseCase()
        val result = checkNotNull(useCase())

        ZipFile(result).use {
            assertThat(it.entries().toList().map { entry -> entry.name })
                .containsExactly("2025-05-08.log", "2025-05-08.log.1")
        }
        assertThat(stale.exists()).isFalse()
        assertThat(ruleZip.exists()).isTrue()
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
