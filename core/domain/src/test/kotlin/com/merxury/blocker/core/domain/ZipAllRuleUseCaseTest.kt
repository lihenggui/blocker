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
import com.merxury.blocker.core.domain.model.ZippedRule
import com.merxury.blocker.core.testing.repository.TestUserDataRepository
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.zip.ZipFile

class ZipAllRuleUseCaseTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val userData = TestUserDataRepository()
    private val filesDir get() = folder.root.resolve("files")
    private val cacheDir get() = folder.root.resolve("cache")

    @Test
    fun givenMissingOrEmptyRuleFolder_whenExported_thenReturnsEmptyResult() = runTest {
        val useCase = createUseCase()

        assertThat(useCase()).isEqualTo(ZippedRule.EMPTY)
        filesDir.resolve("generated/en-US/nested").mkdirs()
        assertThat(useCase()).isEqualTo(ZippedRule.EMPTY)
        assertThat(cacheDir.exists()).isFalse()
    }

    @Test
    fun givenRulesInTwoLanguages_whenExported_thenArchivesOnlySelectedLanguageAndPrunesStaleZip() = runTest {
        userData.setLibDisplayLanguage("zh")
        createRule("zh/root.json")
        createRule("zh/nested/child.json")
        createRule("en-US/other.json")
        cacheDir.mkdir()
        val old = cacheDir.resolve("rules-old.zip").apply { writeText("stale") }
        val unrelated = cacheDir.resolve("other.zip").apply { writeText("keep") }

        val useCase = createUseCase()
        val result = useCase()

        assertThat(result.packageName).isNull()
        ZipFile(checkNotNull(result.zippedFile)).use {
            assertThat(it.entries().toList().map { entry -> entry.name })
                .containsExactly("zh/root.json", "zh/nested/child.json")
        }
        assertThat(old.exists()).isFalse()
        assertThat(unrelated.exists()).isTrue()
    }

    @Test
    fun givenUnwritableCachePath_whenExported_thenReturnsEmptyResult() = runTest {
        createRule("en-US/rule.json")
        cacheDir.writeText("regular file")

        val useCase = createUseCase()

        assertThat(useCase()).isEqualTo(ZippedRule.EMPTY)
        assertThat(cacheDir.readText()).isEqualTo("regular file")
    }

    private fun createRule(path: String) {
        val rule = filesDir.resolve("generated/$path")
        checkNotNull(rule.parentFile).mkdirs()
        rule.writeText("{}")
    }

    private fun TestScope.createUseCase() = ZipAllRuleUseCase(
        userData,
        cacheDir,
        filesDir,
        "generated",
        StandardTestDispatcher(testScheduler),
    )
}
