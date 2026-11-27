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
import com.merxury.blocker.core.domain.rule.status
import com.merxury.blocker.core.model.component.AppKey
import com.merxury.blocker.core.model.component.UserId
import com.merxury.blocker.core.testing.repository.TestUserDataRepository
import com.merxury.blocker.core.testing.repository.device.FakeComponentStatusRepository
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.util.zip.ZipFile

class ZipAppRuleUseCaseTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val app = AppKey("com.example", UserId(10))
    private val components = FakeComponentStatusRepository()
    private val filesDir get() = folder.root.resolve("files")
    private val cacheDir get() = folder.root.resolve("cache")

    @Test
    fun givenRulesForMultipleComponents_whenExported_thenArchivesMatchingJsonForSelectedUser() = runTest {
        components.sendComponents(app.userId, listOf(status(app.packageName, "com.example.json.Receiver")))
        components.sendComponents(UserId.SYSTEM, listOf(status(app.packageName, "com.example.OwnerReceiver")))
        createRule("com/example.json/Receiver.json")
        createRule("com/example.json/Receiver.txt")
        createRule("com/example/OwnerReceiver.json")
        cacheDir.mkdir()
        val stale = cacheDir.resolve("rules-com.example-old.zip").apply { writeText("stale") }
        val other = cacheDir.resolve("rules-other-old.zip").apply { writeText("keep") }

        val useCase = createUseCase()
        val result = useCase(app)

        assertThat(result.packageName).isEqualTo(app.packageName)
        ZipFile(checkNotNull(result.zippedFile)).use {
            assertThat(it.entries().toList().map { entry -> entry.name })
                .containsExactly("en-US/com/example.json/Receiver.json")
        }
        assertThat(stale.exists()).isFalse()
        assertThat(other.exists()).isTrue()
    }

    @Test
    fun givenNoMatchingComponents_whenExported_thenReturnsEmptyResult() = runTest {
        createRule("com/example/OtherReceiver.json")

        val useCase = createUseCase()

        assertThat(useCase(app)).isEqualTo(ZippedRule.EMPTY)
        assertThat(cacheDir.exists()).isFalse()
    }

    @Test
    fun givenMatchedRuleAndUnwritableCachePath_whenExported_thenReturnsEmptyResult() = runTest {
        components.sendComponents(app.userId, listOf(status(app.packageName, "com.example.Receiver")))
        createRule("com/example/Receiver.json")
        cacheDir.writeText("regular file")

        val useCase = createUseCase()

        assertThat(useCase(app)).isEqualTo(ZippedRule.EMPTY)
    }

    private fun createRule(path: String) {
        val rule = filesDir.resolve("generated/en-US/$path")
        checkNotNull(rule.parentFile).mkdirs()
        rule.writeText("{}")
    }

    private fun TestScope.createUseCase() = ZipAppRuleUseCase(
        components,
        TestUserDataRepository(),
        cacheDir,
        filesDir,
        "generated",
        StandardTestDispatcher(testScheduler),
    )
}
