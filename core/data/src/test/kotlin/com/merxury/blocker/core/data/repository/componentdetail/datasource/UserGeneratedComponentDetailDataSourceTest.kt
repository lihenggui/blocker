/*
 * Copyright 2026 Blocker
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

package com.merxury.blocker.core.data.repository.componentdetail.datasource

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.merxury.blocker.core.model.ComponentType
import com.merxury.blocker.core.model.component.AppKey
import com.merxury.blocker.core.model.component.ComponentDeclaration
import com.merxury.blocker.core.model.component.ComponentId
import com.merxury.blocker.core.model.component.ComponentStatus
import com.merxury.blocker.core.model.component.ControlState
import com.merxury.blocker.core.model.component.UserId
import com.merxury.blocker.core.model.data.ComponentDetail
import com.merxury.blocker.core.testing.repository.TestUserDataRepository
import com.merxury.blocker.core.testing.repository.defaultUserData
import com.merxury.blocker.core.testing.repository.device.FakeComponentStatusRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class UserGeneratedComponentDetailDataSourceTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val userData = TestUserDataRepository().apply { sendUserData(defaultUserData) }
    private val cache = FakeComponentStatusRepository()

    @Test
    fun givenWorkAppWithSavedDescriptions_whenItsDeclarationsArrive_thenOnlyThatAppsDescriptionsAreListed() = runTest {
        val source = source()
        val ours = ComponentDetail("com.example.Sync", description = "Sync")
        val other = ComponentDetail("com.other.Sync", description = "Other")
        source.saveComponentData(ours).first()
        source.saveComponentData(other).first()
        cache.sendComponents(UserId.SYSTEM, listOf(status("com.example", ours.name)))

        source.getByApp(AppKey("com.example", UserId(10))).test {
            assertThat(awaitItem()).isEmpty()
            cache.sendComponents(UserId(10), listOf(status("com.example", ours.name), status("com.other", other.name)))
            assertThat(awaitItem()).containsExactly(ours)
        }
        assertThat(source.findByNames(setOf(other.name), "en-US")).containsExactly(other.name, other)
    }

    @Test
    fun givenInvalidNames_whenReadingOrSaving_thenTheyAreRejected() = runTest {
        val source = source()
        val workingDir = folder.newFolder("generated", "en-US")
        // An unvalidated name "escape/Component" would resolve to this existing file.
        val injected = workingDir.resolve("escape/Component.json")
        check(checkNotNull(injected.parentFile).mkdirs())
        val original = ComponentDetail("escape/Component", description = "existing file")
        injected.writeText(Json.encodeToString(original))
        val invalid = setOf("", "escape/Component", "escape\\Component", "../outside", "a..b", ".Leading", "Trailing.")

        for (name in invalid) {
            assertThat(source.saveComponentData(ComponentDetail(name, description = "invalid")).first()).isFalse()
        }

        assertThat(source.findByNames(invalid, "en-US")).isEmpty()
        assertThat(injected.readText()).isEqualTo(Json.encodeToString(original))
        assertThat(folder.root.walkTopDown().filter { it.isFile }.toList()).containsExactly(injected)
    }

    @Test
    fun givenUnchangedDeclarations_whenDetailsAreEditedOrTheLanguageChanges_thenTheListUpdates() = runTest {
        val source = source()
        val detail = ComponentDetail("com.example.Sync", description = "Original")
        val edited = detail.copy(description = "Edited")
        cache.sendComponents(UserId.SYSTEM, listOf(status("com.example", detail.name)))

        source.getByApp(AppKey("com.example", UserId.SYSTEM)).test {
            assertThat(awaitItem()).isEmpty()

            assertThat(source.saveComponentData(detail).first()).isTrue()
            assertThat(awaitItem()).containsExactly(detail)
            assertThat(source.saveComponentData(edited).first()).isTrue()
            assertThat(awaitItem()).containsExactly(edited)

            userData.setLibDisplayLanguage("zh-CN")
            assertThat(awaitItem()).isEmpty()
            val translated = detail.copy(description = "Translated")
            assertThat(source.saveComponentData(translated).first()).isTrue()
            assertThat(awaitItem()).containsExactly(translated)

            userData.setLibDisplayLanguage("en-US")
            assertThat(awaitItem()).containsExactly(edited)
        }
    }

    private fun TestScope.source() = UserGeneratedComponentDetailDataSource(
        userData,
        cache,
        folder.root,
        "generated",
        Json,
        StandardTestDispatcher(testScheduler),
    )

    private fun status(packageName: String, className: String) = ComponentStatus(
        ComponentDeclaration(ComponentId(packageName, className), ComponentType.SERVICE, true, true),
        ControlState.UNKNOWN,
    )
}
