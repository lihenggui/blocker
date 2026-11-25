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

package com.merxury.blocker.core.data.respository.componentdetail.datasource

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
import com.merxury.blocker.core.testing.repository.device.FakeComponentStatusRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class UserGeneratedComponentDetailDataSourceTest {
    @get:Rule
    val folder = TemporaryFolder()

    @Test
    fun givenWorkAppWithSavedDescriptions_whenItsDeclarationsArrive_thenOnlyThatAppsDescriptionsAreListed() = runTest {
        val cache = FakeComponentStatusRepository()
        val source = UserGeneratedComponentDetailDataSource(
            TestUserDataRepository(),
            cache,
            folder.root,
            "generated",
            Json,
            StandardTestDispatcher(testScheduler),
        )
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
        assertThat(source.getByComponentName(other.name).first()).isEqualTo(other)
    }

    private fun status(packageName: String, className: String) = ComponentStatus(
        ComponentDeclaration(ComponentId(packageName, className), ComponentType.SERVICE, true, true),
        ControlState.UNKNOWN,
    )
}
