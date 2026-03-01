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

import app.cash.turbine.test
import com.merxury.blocker.core.model.ComponentType.ACTIVITY
import com.merxury.blocker.core.model.ComponentType.SERVICE
import com.merxury.blocker.core.model.data.ComponentInfo
import com.merxury.blocker.core.model.data.GeneralRule
import com.merxury.blocker.core.model.data.InstalledApp
import com.merxury.blocker.core.testing.repository.TestAppRepository
import com.merxury.blocker.core.testing.repository.TestComponentRepository
import com.merxury.blocker.core.testing.repository.TestGeneralRuleRepository
import com.merxury.blocker.core.testing.repository.TestUserDataRepository
import com.merxury.blocker.core.testing.repository.defaultUserData
import com.merxury.blocker.core.testing.util.MainDispatcherRule
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import kotlin.test.Test
import kotlin.test.assertEquals

class GatherAllMatchedComponentsUseCaseTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val generalRuleRepository = TestGeneralRuleRepository()
    private val componentRepository = TestComponentRepository()
    private val appRepository = TestAppRepository()
    private val userDataRepository = TestUserDataRepository()

    private val useCase = GatherAllMatchedComponentsUseCase(
        generalRuleRepository = generalRuleRepository,
        componentRepository = componentRepository,
        appRepository = appRepository,
        userDataRepository = userDataRepository,
        ioDispatcher = mainDispatcherRule.testDispatcher,
    )

    @Test
    fun emptyRules_returnsEmptyList() = runTest {
        userDataRepository.sendUserData(defaultUserData)
        generalRuleRepository.sendRuleList(emptyList())
        componentRepository.sendComponentList(emptyList())
        appRepository.sendAppList(emptyList())
        useCase().test {
            assertEquals(emptyList(), awaitItem())
            awaitComplete()
        }
    }

    @Test
    fun singleRuleWithKeywords_returnsMatchingComponents() = runTest {
        userDataRepository.sendUserData(defaultUserData.copy(showSystemApps = true))
        val components = listOf(
            ComponentInfo(
                simpleName = "TrackerActivity",
                name = "com.tracker.sdk.TrackerActivity",
                packageName = "com.example.app",
                type = ACTIVITY,
            ),
            ComponentInfo(
                simpleName = "TrackerService",
                name = "com.tracker.sdk.TrackerService",
                packageName = "com.example.app",
                type = SERVICE,
            ),
        )
        componentRepository.sendComponentList(components)
        appRepository.sendAppList(
            listOf(InstalledApp(packageName = "com.example.app", label = "Example")),
        )
        generalRuleRepository.sendRuleList(
            listOf(
                GeneralRule(
                    id = 1,
                    name = "Tracker SDK",
                    searchKeyword = listOf("com.tracker.sdk"),
                    matchedAppCount = 1,
                ),
            ),
        )
        useCase().test {
            val result = awaitItem()
            assertEquals(2, result.size)
            awaitComplete()
        }
    }

    @Test
    fun multipleRulesWithOverlappingKeywords_deduplicates() = runTest {
        userDataRepository.sendUserData(defaultUserData.copy(showSystemApps = true))
        val components = listOf(
            ComponentInfo(
                simpleName = "SharedComponent",
                name = "com.shared.sdk.SharedComponent",
                packageName = "com.example.app",
                type = ACTIVITY,
            ),
            ComponentInfo(
                simpleName = "UniqueComponent",
                name = "com.unique.sdk.UniqueComponent",
                packageName = "com.example.app",
                type = SERVICE,
            ),
        )
        componentRepository.sendComponentList(components)
        appRepository.sendAppList(
            listOf(InstalledApp(packageName = "com.example.app", label = "Example")),
        )
        generalRuleRepository.sendRuleList(
            listOf(
                GeneralRule(
                    id = 1,
                    name = "Rule A",
                    searchKeyword = listOf("com.shared.sdk"),
                    matchedAppCount = 1,
                ),
                GeneralRule(
                    id = 2,
                    name = "Rule B",
                    searchKeyword = listOf("com.shared.sdk", "com.unique.sdk"),
                    matchedAppCount = 1,
                ),
            ),
        )
        useCase().test {
            val result = awaitItem()
            assertEquals(2, result.size)
            awaitComplete()
        }
    }

    @Test
    fun systemAppFiltering_excludesSystemApps() = runTest {
        userDataRepository.sendUserData(defaultUserData.copy(showSystemApps = false))
        val components = listOf(
            ComponentInfo(
                simpleName = "UserComponent",
                name = "com.tracker.sdk.UserComponent",
                packageName = "com.user.app",
                type = ACTIVITY,
            ),
            ComponentInfo(
                simpleName = "SystemComponent",
                name = "com.tracker.sdk.SystemComponent",
                packageName = "com.system.app",
                type = SERVICE,
            ),
        )
        componentRepository.sendComponentList(components)
        appRepository.sendAppList(
            listOf(
                InstalledApp(packageName = "com.user.app", label = "User App", isSystem = false),
                InstalledApp(packageName = "com.system.app", label = "System App", isSystem = true),
            ),
        )
        generalRuleRepository.sendRuleList(
            listOf(
                GeneralRule(
                    id = 1,
                    name = "Tracker",
                    searchKeyword = listOf("com.tracker.sdk"),
                    matchedAppCount = 2,
                ),
            ),
        )
        useCase().test {
            val result = awaitItem()
            assertEquals(1, result.size)
            assertEquals("com.user.app", result[0].packageName)
            awaitComplete()
        }
    }

    @Test
    fun rulesWithZeroMatches_areSkipped() = runTest {
        userDataRepository.sendUserData(defaultUserData.copy(showSystemApps = true))
        val components = listOf(
            ComponentInfo(
                simpleName = "TrackerActivity",
                name = "com.tracker.sdk.TrackerActivity",
                packageName = "com.example.app",
                type = ACTIVITY,
            ),
        )
        componentRepository.sendComponentList(components)
        appRepository.sendAppList(
            listOf(InstalledApp(packageName = "com.example.app", label = "Example")),
        )
        generalRuleRepository.sendRuleList(
            listOf(
                GeneralRule(
                    id = 1,
                    name = "Matched Rule",
                    searchKeyword = listOf("com.tracker.sdk"),
                    matchedAppCount = 1,
                ),
                GeneralRule(
                    id = 2,
                    name = "Unmatched Rule",
                    searchKeyword = listOf("com.other.sdk"),
                    matchedAppCount = 0,
                ),
            ),
        )
        useCase().test {
            val result = awaitItem()
            assertEquals(1, result.size)
            awaitComplete()
        }
    }
}
