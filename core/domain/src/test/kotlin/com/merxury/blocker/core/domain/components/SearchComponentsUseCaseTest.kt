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
import com.merxury.blocker.core.model.preference.ComponentShowPriority
import com.merxury.blocker.core.model.preference.ComponentSorting
import com.merxury.blocker.core.model.preference.SortingOrder
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
    fun givenComponentList_whenSearchByKeyword_thenGetFilteredList() = runTest {
        userDataRepository.sendUserData(defaultUserData)
        appRepository.sendAppList(listOf(app1, app2, app3))
        componentRepository.sendComponentList(components1 + components2 + components3)
        componentDetailRepository.sendComponentDetail(emptyList())
        // There would be only one component with the name "15"
        searchComponentsUseCase(app1PackageName, "15").test {
            val expectedList = ComponentSearchResult(
                app = app1.toAppItem(),
                activity = listOf(),
                service = listOf(),
                receiver = listOf(),
                provider = listOf(components1[15]),
            )
            assertEquals(expectedList, awaitItem())
        }
    }

    @Test
    fun givenComponentListWithRunningService_whenInDefault_thenShowRunningServiceOnTop() = runTest {
        userDataRepository.sendUserData(
            defaultUserData.copy(
                componentSorting = ComponentSorting.COMPONENT_NAME,
                componentSortingOrder = SortingOrder.ASCENDING,
                componentShowPriority = ComponentShowPriority.NONE,
            ),
        )
        appRepository.sendAppList(listOf(app1, app2, app3))
        componentRepository.sendComponentList(components1 + components2 + components3)
        componentDetailRepository.sendComponentDetail(emptyList())
        val runningServiceName = "$app1PackageName.5"
        serviceController.sendRunningServices(runningServiceName)

        val listWithRunningService = components1.map {
            if (it.name == runningServiceName) {
                it.copy(isRunning = true)
            } else {
                it
            }
        }

        searchComponentsUseCase(app1PackageName, "").test {
            val expectedList = ComponentSearchResult(
                app = app1.toAppItem(),
                activity = components1.filter { it.type == ComponentType.ACTIVITY }
                    .sortedBy { it.simpleName.lowercase() },
                service = listWithRunningService.filter { it.type == ComponentType.SERVICE }
                    .sortedBy { it.simpleName.lowercase() }
                    .sortedByDescending { it.name == "5" },
                receiver = components1.filter { it.type == ComponentType.RECEIVER }
                    .sortedBy { it.simpleName.lowercase() },
                provider = components1.filter { it.type == ComponentType.PROVIDER }
                    .sortedBy { it.simpleName.lowercase() },
            )
            assertEquals(expectedList, awaitItem())
        }
    }

    @Test
    fun givenSortByNameAscending_whenNoSearchKeyword_thenReturnUnfilteredResult() = runTest {
        userDataRepository.sendUserData(
            defaultUserData.copy(
                componentSorting = ComponentSorting.COMPONENT_NAME,
                componentSortingOrder = SortingOrder.ASCENDING,
                componentShowPriority = ComponentShowPriority.NONE,
            ),
        )
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

    @Test
    fun givenSortByNameDescending_whenNoSearchKeyword_thenGetSortedList() = runTest {
        userDataRepository.sendUserData(
            defaultUserData.copy(
                componentSorting = ComponentSorting.COMPONENT_NAME,
                componentSortingOrder = SortingOrder.DESCENDING,
                componentShowPriority = ComponentShowPriority.NONE,
            ),
        )
        appRepository.sendAppList(listOf(app1, app2, app3))
        componentRepository.sendComponentList(components1 + components2 + components3)
        componentDetailRepository.sendComponentDetail(emptyList())
        // Default preference is sort by COMPONENT_NAME, ascending
        searchComponentsUseCase(app2PackageName, "").test {
            val expectedList = ComponentSearchResult(
                app = app2.toAppItem(),
                activity = components2.filter { it.type == ComponentType.ACTIVITY }
                    .sortedByDescending { it.simpleName.lowercase() },
                service = components2.filter { it.type == ComponentType.SERVICE }
                    .sortedByDescending { it.simpleName.lowercase() },
                receiver = components2.filter { it.type == ComponentType.RECEIVER }
                    .sortedByDescending { it.simpleName.lowercase() },
                provider = components2.filter { it.type == ComponentType.PROVIDER }
                    .sortedByDescending { it.simpleName.lowercase() },
            )
            assertEquals(expectedList, awaitItem())
        }
    }

    @Test
    fun givenSortByFullNameAscending_whenNoSearchKeyword_thenGetSortedList() = runTest {
        userDataRepository.sendUserData(
            defaultUserData.copy(
                componentSorting = ComponentSorting.PACKAGE_NAME,
                componentSortingOrder = SortingOrder.ASCENDING,
                componentShowPriority = ComponentShowPriority.NONE,
            ),
        )
        appRepository.sendAppList(listOf(app1, app2, app3))
        componentRepository.sendComponentList(components1 + components2 + components3)
        componentDetailRepository.sendComponentDetail(emptyList())
        // Default preference is sort by COMPONENT_NAME, ascending
        searchComponentsUseCase(app3PackageName, "").test {
            val expectedList = ComponentSearchResult(
                app = app3.toAppItem(),
                activity = components3.filter { it.type == ComponentType.ACTIVITY }
                    .sortedBy { it.name.lowercase() },
                service = components3.filter { it.type == ComponentType.SERVICE }
                    .sortedBy { it.name.lowercase() },
                receiver = components3.filter { it.type == ComponentType.RECEIVER }
                    .sortedBy { it.name.lowercase() },
                provider = components3.filter { it.type == ComponentType.PROVIDER }
                    .sortedBy { it.name.lowercase() },
            )
            assertEquals(expectedList, awaitItem())
        }
    }

    @Test
    fun givenSortByFullNameDescending_whenNoSearchKeyword_thenGetSortedList() = runTest {
        userDataRepository.sendUserData(
            defaultUserData.copy(
                componentSorting = ComponentSorting.PACKAGE_NAME,
                componentSortingOrder = SortingOrder.DESCENDING,
                componentShowPriority = ComponentShowPriority.NONE,
            ),
        )
        appRepository.sendAppList(listOf(app1, app2, app3))
        componentRepository.sendComponentList(components1 + components2 + components3)
        componentDetailRepository.sendComponentDetail(emptyList())
        // Default preference is sort by COMPONENT_NAME, ascending
        searchComponentsUseCase(app2PackageName, "").test {
            val expectedList = ComponentSearchResult(
                app = app2.toAppItem(),
                activity = components2.filter { it.type == ComponentType.ACTIVITY }
                    .sortedByDescending { it.name.lowercase() },
                service = components2.filter { it.type == ComponentType.SERVICE }
                    .sortedByDescending { it.name.lowercase() },
                receiver = components2.filter { it.type == ComponentType.RECEIVER }
                    .sortedByDescending { it.name.lowercase() },
                provider = components2.filter { it.type == ComponentType.PROVIDER }
                    .sortedByDescending { it.name.lowercase() },
            )
            assertEquals(expectedList, awaitItem())
        }
    }

    @Test
    fun givenComponentListWithDisabledItems_whenSetShowDisabledFirst_thenGetSortedList() = runTest {
        userDataRepository.sendUserData(
            defaultUserData.copy(
                componentSorting = ComponentSorting.COMPONENT_NAME,
                componentSortingOrder = SortingOrder.ASCENDING,
                componentShowPriority = ComponentShowPriority.DISABLED_COMPONENTS_FIRST,
            ),
        )
        appRepository.sendAppList(listOf(app1, app2, app3))
        val componentWithDisabledItem = components1.mapIndexed { index, component ->
            if (index % 2 == 0) {
                component.copy(pmBlocked = true)
            } else {
                component
            }
        }
        componentRepository.sendComponentList(componentWithDisabledItem)
        componentDetailRepository.sendComponentDetail(emptyList())
        // Default preference is sort by COMPONENT_NAME, ascending
        searchComponentsUseCase(app1PackageName, "").test {
            val expectedList = ComponentSearchResult(
                app = app1.toAppItem(),
                activity = componentWithDisabledItem.filter { it.type == ComponentType.ACTIVITY }
                    .sortedBy { it.simpleName.lowercase() }
                    .sortedBy { it.enabled() },
                service = componentWithDisabledItem.filter { it.type == ComponentType.SERVICE }
                    .sortedBy { it.simpleName.lowercase() }
                    .sortedBy { it.enabled() },
                receiver = componentWithDisabledItem.filter { it.type == ComponentType.RECEIVER }
                    .sortedBy { it.simpleName.lowercase() }
                    .sortedBy { it.enabled() },
                provider = componentWithDisabledItem.filter { it.type == ComponentType.PROVIDER }
                    .sortedBy { it.simpleName.lowercase() }
                    .sortedBy { it.enabled() },
            )
            assertEquals(expectedList, awaitItem())
        }
    }

    @Test
    fun givenComponentListWithDisabledItems_whenSetShowEnabledFirst_thenGetSortedList() = runTest {
        userDataRepository.sendUserData(
            defaultUserData.copy(
                componentSorting = ComponentSorting.COMPONENT_NAME,
                componentSortingOrder = SortingOrder.ASCENDING,
                componentShowPriority = ComponentShowPriority.ENABLED_COMPONENTS_FIRST,
            ),
        )
        appRepository.sendAppList(listOf(app1, app2, app3))
        val componentWithDisabledItem = components1.mapIndexed { index, component ->
            if (index % 2 == 0) {
                component.copy(pmBlocked = true)
            } else {
                component
            }
        }
        componentRepository.sendComponentList(componentWithDisabledItem)
        componentDetailRepository.sendComponentDetail(emptyList())
        // Default preference is sort by COMPONENT_NAME, ascending
        searchComponentsUseCase(app1PackageName, "").test {
            val expectedList = ComponentSearchResult(
                app = app1.toAppItem(),
                activity = componentWithDisabledItem.filter { it.type == ComponentType.ACTIVITY }
                    .sortedBy { it.simpleName.lowercase() }
                    .sortedByDescending { it.enabled() },
                service = componentWithDisabledItem.filter { it.type == ComponentType.SERVICE }
                    .sortedBy { it.simpleName.lowercase() }
                    .sortedByDescending { it.enabled() },
                receiver = componentWithDisabledItem.filter { it.type == ComponentType.RECEIVER }
                    .sortedBy { it.simpleName.lowercase() }
                    .sortedByDescending { it.enabled() },
                provider = componentWithDisabledItem.filter { it.type == ComponentType.PROVIDER }
                    .sortedBy { it.simpleName.lowercase() }
                    .sortedByDescending { it.enabled() },
            )
            assertEquals(expectedList, awaitItem())
        }
    }
}
