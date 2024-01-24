/*
 * Copyright 2024 Blocker
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

package com.merxury.blocker.core.domain.components

import app.cash.turbine.test
import com.merxury.blocker.core.domain.controller.GetServiceControllerUseCase
import com.merxury.blocker.core.domain.model.ComponentSearchResult
import com.merxury.blocker.core.model.ComponentType
import com.merxury.blocker.core.model.data.ComponentInfo
import com.merxury.blocker.core.model.data.InstalledApp
import com.merxury.blocker.core.model.data.toAppItem
import com.merxury.blocker.core.testing.controller.FakeServiceController
import com.merxury.blocker.core.testing.repository.TestAppRepository
import com.merxury.blocker.core.testing.repository.TestComponentDetailRepository
import com.merxury.blocker.core.testing.repository.TestComponentRepository
import com.merxury.blocker.core.testing.repository.TestUserDataRepository
import com.merxury.blocker.core.testing.repository.defaultUserData
import com.merxury.blocker.core.testing.util.MainDispatcherRule
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import kotlin.test.Test
import kotlin.test.assertEquals

class SearchComponentsUseCaseTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val userDataRepository = TestUserDataRepository()
    private val appRepository = TestAppRepository()
    private val componentRepository = TestComponentRepository()
    private val componentDetailRepository = TestComponentDetailRepository()
    private val serviceController = FakeServiceController()
    private val getServiceControllerUseCase =
        GetServiceControllerUseCase(userDataRepository, serviceController, serviceController)
    private val searchComponentsUseCase = SearchComponentsUseCase(
        userDataRepository,
        appRepository,
        componentRepository,
        componentDetailRepository,
        getServiceControllerUseCase,
        mainDispatcherRule.testDispatcher,
    )
    private val app1PackageName = "com.merxury.blocker.test1"
    private val app2PackageName = "com.merxury.blocker.test2"
    private val app3PackageName = "com.merxury.blocker.test3"
    private val app1 = InstalledApp(packageName = app1PackageName)
    private val app2 = InstalledApp(packageName = app2PackageName)
    private val app3 = InstalledApp(packageName = app3PackageName)
    private val components1 = createComponentInfoList(app1PackageName)
    private val components2 = createComponentInfoList(app2PackageName)
    private val components3 = createComponentInfoList(app3PackageName)
    private fun createComponentInfoList(packageName: String): List<ComponentInfo> {
        val componentMapping = mapOf(
            0 to ComponentType.RECEIVER,
            1 to ComponentType.SERVICE,
            2 to ComponentType.ACTIVITY,
            3 to ComponentType.PROVIDER,
        )
        return (0 until 16).map {
            ComponentInfo(
                packageName = packageName,
                simpleName = "$it",
                name = "$packageName.$it",
                type = componentMapping[it % 4] ?: ComponentType.ACTIVITY,
                pmBlocked = false,
            )
        }
    }

    @Test
    fun givenEmptyList_whenSearchComponents_thenEmptyResult() = runTest {
        userDataRepository.sendUserData(defaultUserData)
        appRepository.sendAppList(emptyList())
        componentRepository.sendComponentList(emptyList())
        componentDetailRepository.sendComponentDetail(emptyList())
        searchComponentsUseCase("com.merxury.blocker", "test").test {
            assertEquals(ComponentSearchResult(app = null), awaitItem())
        }
    }

    @Test
    fun givenAppAndComponentList_whenNoSearchKeyword_thenReturnUnfilteredResult() = runTest {
        userDataRepository.sendUserData(defaultUserData)
        appRepository.sendAppList(listOf(app1, app2, app3))
        componentRepository.sendComponentList(components1 + components2 + components3)
        componentDetailRepository.sendComponentDetail(emptyList())
        // Default preference is sort by COMPONENT_NAME, ascending
        searchComponentsUseCase(app1PackageName, "").test {
            val expectedList = ComponentSearchResult(
                app = app1.toAppItem(),
                activity = components1.filter { it.type == ComponentType.ACTIVITY }
                    .sortedBy { it.simpleName.lowercase() },
                service = components1.filter { it.type == ComponentType.SERVICE }
                    .sortedBy { it.simpleName.lowercase() },
                receiver = components1.filter { it.type == ComponentType.RECEIVER }
                    .sortedBy { it.simpleName.lowercase() },
                provider = components1.filter { it.type == ComponentType.PROVIDER }
                    .sortedBy { it.simpleName.lowercase() },
            )
            assertEquals(expectedList, awaitItem())
        }
    }
}
