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

package com.merxury.blocker.core.domain.applist

import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import app.cash.turbine.test
import com.merxury.blocker.core.domain.controller.GetAppControllerUseCase
import com.merxury.blocker.core.domain.controller.GetServiceControllerUseCase
import com.merxury.blocker.core.model.data.AppServiceStatus
import com.merxury.blocker.core.model.data.InstalledApp
import com.merxury.blocker.core.model.data.toAppItem
import com.merxury.blocker.core.model.preference.AppSorting
import com.merxury.blocker.core.model.preference.SortingOrder
import com.merxury.blocker.core.model.preference.TopAppType
import com.merxury.blocker.core.testing.controller.FakeAppController
import com.merxury.blocker.core.testing.controller.FakeServiceController
import com.merxury.blocker.core.testing.data.TestAppStateCache
import com.merxury.blocker.core.testing.repository.TestAppRepository
import com.merxury.blocker.core.testing.repository.TestUserDataRepository
import com.merxury.blocker.core.testing.repository.defaultUserData
import com.merxury.blocker.core.testing.util.MainDispatcherRule
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant

class SearchAppListUseCaseTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()
    private val packageInfo = mock<PackageInfo> {
        on { toString() } doReturn "MockedPackageInfo"
    }
    private val pm = mock<PackageManager> {
        on { getPackageInfo(any<String>(), any<Int>()) } doReturn packageInfo
        on {
            getPackageInfo(
                any<String>(),
                any<PackageManager.PackageInfoFlags>(),
            )
        } doReturn packageInfo
    }
    private val userDataRepository = TestUserDataRepository()
    private val appRepository = TestAppRepository()
    private val appStateCache = TestAppStateCache()
    private val appController = FakeAppController()
    private val serviceController = FakeServiceController()
    private val getAppControllerUseCase =
        GetAppControllerUseCase(userDataRepository, appController, appController)
    private val getServiceControllerUseCase =
        GetServiceControllerUseCase(userDataRepository, serviceController, serviceController)
    private val searchAppListUseCase = SearchAppListUseCase(
        pm,
        userDataRepository,
        appRepository,
        appStateCache,
        getAppControllerUseCase,
        getServiceControllerUseCase,
        mainDispatcherRule.testDispatcher,
    )

    @Test
    fun givenEmptyAppList_returnEmptyAppList() = runTest {
        val appList = emptyList<InstalledApp>()
        userDataRepository.sendUserData(defaultUserData)
        appRepository.sendAppList(appList)
        searchAppListUseCase("").test {
            assertEquals(emptyList(), awaitItem())
        }
    }

    @Test
    fun givenRandomAppList_whenSetSortByNameAscending_thenReturnSortedAppList() = runTest {
        val app1 = InstalledApp(label = "App 3")
        val app2 = InstalledApp(label = "App 2")
        val app3 = InstalledApp(label = "App 1")
        val appList = listOf(app1, app2, app3)
        userDataRepository.sendUserData(
            defaultUserData.copy(
                appSorting = AppSorting.NAME,
                appSortingOrder = SortingOrder.ASCENDING,
            ),
        )
        // Default setting is sort by name, ascending
        appRepository.sendAppList(appList)
        val expectedList = listOf(app3, app2, app1)
            .map {
                it.toAppItem(packageInfo = packageInfo)
            }
        searchAppListUseCase("").test {
            assertEquals(expectedList, awaitItem())
        }
    }

    @Test
    fun givenAppList_whenSetSortByNameDescending_thenShowTheSortedList() = runTest {
        val app1 = InstalledApp(label = "App 1")
        val app2 = InstalledApp(label = "App 2")
        val app3 = InstalledApp(label = "App 3")
        val appList = listOf(app1, app2, app3)
        userDataRepository.sendUserData(
            defaultUserData.copy(
                appSorting = AppSorting.NAME,
                appSortingOrder = SortingOrder.DESCENDING,
            ),
        )
        appRepository.sendAppList(appList)
        val expectedList = listOf(app3, app2, app1)
            .map {
                it.toAppItem(packageInfo = packageInfo)
            }
        searchAppListUseCase("").test {
            assertEquals(expectedList, awaitItem())
        }
    }

    @Test
    fun givenAppList_whenSetSortByFirstInstallTimeAscending_thenShowSortedList() = runTest {
        val app1 = InstalledApp(firstInstallTime = Instant.parse("2021-01-03T00:00:00Z"))
        val app2 = InstalledApp(firstInstallTime = Instant.parse("2021-01-02T00:00:00Z"))
        val app3 = InstalledApp(firstInstallTime = Instant.parse("2021-01-01T00:00:00Z"))
        val appList = listOf(app1, app2, app3)
        userDataRepository.sendUserData(
            defaultUserData.copy(
                appSorting = AppSorting.FIRST_INSTALL_TIME,
                appSortingOrder = SortingOrder.ASCENDING,
            ),
        )
        appRepository.sendAppList(appList)
        val expectedList = listOf(app3, app2, app1)
            .map {
                it.toAppItem(packageInfo = packageInfo)
            }
        searchAppListUseCase("").test {
            assertEquals(expectedList, awaitItem())
        }
    }

    @Test
    fun givenAppList_whenSetSortByFirstInstallTimeDescending_thenShowSortedList() = runTest {
        val app1 = InstalledApp(firstInstallTime = Instant.parse("2021-01-01T00:00:00Z"))
        val app2 = InstalledApp(firstInstallTime = Instant.parse("2021-01-02T00:00:00Z"))
        val app3 = InstalledApp(firstInstallTime = Instant.parse("2021-01-03T00:00:00Z"))
        val appList = listOf(app1, app2, app3)
        userDataRepository.sendUserData(
            defaultUserData.copy(
                appSorting = AppSorting.FIRST_INSTALL_TIME,
                appSortingOrder = SortingOrder.DESCENDING,
            ),
        )
        appRepository.sendAppList(appList)
        val expectedList = listOf(app3, app2, app1)
            .map {
                it.toAppItem(packageInfo = packageInfo)
            }
        searchAppListUseCase("").test {
            assertEquals(expectedList, awaitItem())
        }
    }

    @Test
    fun givenAppList_whenSetSortByLastUpdateTimeAscending_thenShowSortedList() = runTest {
        val app1 = InstalledApp(lastUpdateTime = Instant.parse("2021-01-03T00:00:00Z"))
        val app2 = InstalledApp(lastUpdateTime = Instant.parse("2021-01-02T00:00:00Z"))
        val app3 = InstalledApp(lastUpdateTime = Instant.parse("2021-01-01T00:00:00Z"))
        val appList = listOf(app1, app2, app3)
        userDataRepository.sendUserData(
            defaultUserData.copy(
                appSorting = AppSorting.LAST_UPDATE_TIME,
                appSortingOrder = SortingOrder.ASCENDING,
            ),
        )
        appRepository.sendAppList(appList)
        val expectedList = listOf(app3, app2, app1)
            .map {
                it.toAppItem(packageInfo = packageInfo)
            }
        searchAppListUseCase("").test {
            assertEquals(expectedList, awaitItem())
        }
    }

    @Test
    fun givenAppList_whenSetSortByLastUpdateTimeDescending_thenShowSortedList() = runTest {
        val app1 = InstalledApp(lastUpdateTime = Instant.parse("2021-01-01T00:00:00Z"))
        val app2 = InstalledApp(lastUpdateTime = Instant.parse("2021-01-02T00:00:00Z"))
        val app3 = InstalledApp(lastUpdateTime = Instant.parse("2021-01-03T00:00:00Z"))
        val appList = listOf(app1, app2, app3)
        userDataRepository.sendUserData(
            defaultUserData.copy(
                appSorting = AppSorting.LAST_UPDATE_TIME,
                appSortingOrder = SortingOrder.DESCENDING,
            ),
        )
        appRepository.sendAppList(appList)
        val expectedList = listOf(app3, app2, app1)
            .map {
                it.toAppItem(packageInfo = packageInfo)
            }
        searchAppListUseCase("").test {
            assertEquals(expectedList, awaitItem())
        }
    }

    @Test
    fun givenAppList_whenShowUserAppOnly_thenShowUserAppOnly() = runTest {
        val app1 = InstalledApp(isSystem = true)
        val app2 = InstalledApp(isSystem = false)
        val app3 = InstalledApp(isSystem = false)
        val appList = listOf(app1, app2, app3)
        userDataRepository.sendUserData(
            defaultUserData.copy(
                showSystemApps = false,
            ),
        )
        // Default setting is sort by name, ascending, show user app only
        appRepository.sendAppList(appList)
        val expectedList = listOf(app2, app3)
            .map {
                it.toAppItem(packageInfo = packageInfo)
            }
        searchAppListUseCase("").test {
            assertEquals(expectedList.size, awaitItem().size)
        }
    }

    @Test
    fun givenAppList_whenShowSystemApp_thenShowAppList() = runTest {
        val app1 = InstalledApp(isSystem = true)
        val app2 = InstalledApp(isSystem = false)
        val app3 = InstalledApp(isSystem = false)
        val appList = listOf(app1, app2, app3)
        userDataRepository.sendUserData(
            defaultUserData.copy(
                showSystemApps = true,
            ),
        )
        // Default setting is sort by name, ascending, show user app only
        appRepository.sendAppList(appList)
        val expectedList = listOf(app1, app2, app3)
            .map {
                it.toAppItem(packageInfo = packageInfo)
            }
        searchAppListUseCase("").test {
            assertEquals(expectedList.size, awaitItem().size)
        }
    }

    @Test
    fun givenAppList_whenTypeSearchLabel_thenShowFilteredList() = runTest {
        val app1 = InstalledApp(label = "App 1")
        val app2 = InstalledApp(label = "App 2")
        val app3 = InstalledApp(label = "App 3")
        val appList = listOf(app1, app2, app3)
        userDataRepository.sendUserData(
            defaultUserData.copy(
                appSorting = AppSorting.NAME,
                appSortingOrder = SortingOrder.ASCENDING,
            ),
        )
        // Default setting is sort by name, ascending
        appRepository.sendAppList(appList)
        val expectedList = listOf(app3)
            .map {
                it.toAppItem(packageInfo = packageInfo)
            }
        searchAppListUseCase("App 3").test {
            assertEquals(expectedList, awaitItem())
        }
    }

    @Test
    fun givenAppList_whenSearchPackageName_thenShowFilteredList() = runTest {
        val app1 = InstalledApp(packageName = "com.merxury.blocker.app")
        val app2 = InstalledApp(packageName = "com.merxury.blocker.core")
        val app3 = InstalledApp(packageName = "com.merxury.blocker.feature")
        val appList = listOf(app1, app2, app3)
        userDataRepository.sendUserData(
            defaultUserData.copy(
                appSorting = AppSorting.NAME,
                appSortingOrder = SortingOrder.ASCENDING,
            ),
        )
        // Default setting is sort by name, ascending
        appRepository.sendAppList(appList)
        val expectedList = listOf(app2)
            .map {
                it.toAppItem(packageInfo = packageInfo)
            }
        searchAppListUseCase("com.merxury.blocker.core").test {
            assertEquals(expectedList, awaitItem())
        }
    }

    @Test
    fun givenAppList_whenDoingFuzzyQueryOnLabel_showFilteredList() = runTest {
        val app1 = InstalledApp(label = "App 1")
        val app2 = InstalledApp(label = "App 2")
        val app3 = InstalledApp(label = "App 3 search")
        val app4 = InstalledApp(label = "App 4 search")
        val appList = listOf(app1, app2, app3, app4)
        userDataRepository.sendUserData(
            defaultUserData.copy(
                appSorting = AppSorting.NAME,
                appSortingOrder = SortingOrder.ASCENDING,
            ),
        )
        // Default setting is sort by name, ascending
        appRepository.sendAppList(appList)
        val expectedList = listOf(app3, app4)
            .map {
                it.toAppItem(packageInfo = packageInfo)
            }
        searchAppListUseCase("search").test {
            assertEquals(expectedList, awaitItem())
        }
    }

    @Test
    fun givenAppList_whenFuzzyQueryPackageName_thenShowFilteredResult() = runTest {
        val app1 = InstalledApp(packageName = "com.merxury.blocker.app")
        val app2 = InstalledApp(packageName = "com.merxury.blocker.core")
        val app3 = InstalledApp(packageName = "com.merxury.blocker.feature1.search")
        val app4 = InstalledApp(packageName = "com.merxury.blocker.feature.search")
        val appList = listOf(app1, app2, app3, app4)
        userDataRepository.sendUserData(
            defaultUserData.copy(
                appSorting = AppSorting.NAME,
                appSortingOrder = SortingOrder.ASCENDING,
            ),
        )
        // Default setting is sort by name, ascending
        appRepository.sendAppList(appList)
        val expectedList = listOf(app3, app4)
            .map {
                it.toAppItem(packageInfo = packageInfo)
            }
        searchAppListUseCase("search").test {
            assertEquals(expectedList, awaitItem())
        }
    }

    @Test
    fun givenAppList_whenSetTopAppTypeToRunning_thenShowCorrectList() = runTest {
        val app1 = InstalledApp(packageName = "1")
        val app2 = InstalledApp(packageName = "2")
        val app3 = InstalledApp(packageName = "3")
        val app4 = InstalledApp(packageName = "4")
        val appList = listOf(app1, app2, app3, app4)
        userDataRepository.sendUserData(
            defaultUserData.copy(
                appSorting = AppSorting.NAME,
                appSortingOrder = SortingOrder.ASCENDING,
                topAppType = TopAppType.RUNNING,
            ),
        )
        // Default setting is sort by name, ascending
        appRepository.sendAppList(appList)
        appController.setRunningApps("3", "4")
        val expectedList = mutableListOf(app3, app4, app1, app2)
            .map {
                it.toAppItem(packageInfo = packageInfo)
            }
            .toMutableList()
        expectedList[0] = expectedList[0].copy(isRunning = true)
        expectedList[1] = expectedList[1].copy(isRunning = true)
        searchAppListUseCase("").test {
            assertEquals(expectedList.toList(), awaitItem())
        }
    }

    @Test
    fun givenAppList_whenSetTopAppTypeToDisabled_thenShowCorrectList() = runTest {
        val app1 = InstalledApp(packageName = "1", isEnabled = true)
        val app2 = InstalledApp(packageName = "2", isEnabled = true)
        val app3 = InstalledApp(packageName = "3", isEnabled = false)
        val app4 = InstalledApp(packageName = "4", isEnabled = false)
        val appList = listOf(app1, app2, app3, app4)
        userDataRepository.sendUserData(
            defaultUserData.copy(
                appSorting = AppSorting.NAME,
                appSortingOrder = SortingOrder.ASCENDING,
                topAppType = TopAppType.DISABLED,
            ),
        )
        // Default setting is sort by name, ascending
        appRepository.sendAppList(appList)
        val expectedList = mutableListOf(app3, app4, app1, app2)
            .map {
                it.toAppItem(packageInfo = packageInfo)
            }
        searchAppListUseCase("").test {
            assertEquals(expectedList.toList(), awaitItem())
        }
    }

    @Test
    fun givenAppList_whenSetShowServiceInfoToTrue_thenShowListWithServiceInfo() = runTest {
        val app1 = InstalledApp(label = "App 1", packageName = "1")
        val app2 = InstalledApp(label = "App 2", packageName = "2")
        val app3 = InstalledApp(label = "App 3", packageName = "3")
        val appServiceStatus1 = AppServiceStatus(packageName = "1", running = 1, blocked = 1, total = 1)
        val appServiceStatus2 = AppServiceStatus(packageName = "2", running = 2, blocked = 2, total = 2)
        val appServiceStatus3 = AppServiceStatus(packageName = "3", running = 3, blocked = 3, total = 3)
        val appList = listOf(app1, app2, app3)
        userDataRepository.sendUserData(
            defaultUserData.copy(
                appSorting = AppSorting.NAME,
                appSortingOrder = SortingOrder.ASCENDING,
                showServiceInfo = true,
            ),
        )
        appStateCache.putAppState(appServiceStatus1, appServiceStatus2, appServiceStatus3)
        appRepository.sendAppList(appList)
        val expectedList = listOf(
            app1.toAppItem(
                packageInfo = packageInfo,
                appServiceStatus = appServiceStatus1,
            ),
            app2.toAppItem(
                packageInfo = packageInfo,
                appServiceStatus = appServiceStatus2,
            ),
            app3.toAppItem(
                packageInfo = packageInfo,
                appServiceStatus = appServiceStatus3,
            ),
        )
        searchAppListUseCase("").test {
            // Cached result
            assertEquals(expectedList, awaitItem())
            // When turning on show service info option, the list will be reloaded
            assertEquals(expectedList, awaitItem())
        }
    }
}
