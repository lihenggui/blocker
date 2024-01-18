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

package com.merxury.blocker.core.domain.applist

import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import app.cash.turbine.test
import com.merxury.blocker.core.domain.controller.GetAppControllerUseCase
import com.merxury.blocker.core.domain.controller.GetServiceControllerUseCase
import com.merxury.blocker.core.model.data.InstalledApp
import com.merxury.blocker.core.model.data.toAppItem
import com.merxury.blocker.core.model.preference.AppSorting
import com.merxury.blocker.core.model.preference.SortingOrder
import com.merxury.blocker.core.testing.controller.FakeAppController
import com.merxury.blocker.core.testing.controller.FakeServiceController
import com.merxury.blocker.core.testing.data.TestAppStateCache
import com.merxury.blocker.core.testing.repository.TestAppRepository
import com.merxury.blocker.core.testing.repository.TestUserDataRepository
import com.merxury.blocker.core.testing.repository.defaultUserData
import com.merxury.blocker.core.testing.util.MainDispatcherRule
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import org.junit.Rule
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import kotlin.test.Test
import kotlin.test.assertEquals

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
}
