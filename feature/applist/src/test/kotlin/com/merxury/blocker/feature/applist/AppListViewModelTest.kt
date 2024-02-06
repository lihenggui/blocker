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

package com.merxury.blocker.feature.applist

import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.pm.PackageManager.PackageInfoFlags
import com.merxury.blocker.core.domain.InitializeDatabaseUseCase
import com.merxury.blocker.core.domain.applist.SearchAppListUseCase
import com.merxury.blocker.core.domain.controller.GetAppControllerUseCase
import com.merxury.blocker.core.domain.controller.GetServiceControllerUseCase
import com.merxury.blocker.core.testing.controller.FakeAppController
import com.merxury.blocker.core.testing.controller.FakeServiceController
import com.merxury.blocker.core.testing.data.TestAppStateCache
import com.merxury.blocker.core.testing.repository.TestAppPropertiesRepository
import com.merxury.blocker.core.testing.repository.TestAppRepository
import com.merxury.blocker.core.testing.repository.TestComponentRepository
import com.merxury.blocker.core.testing.repository.TestUserDataRepository
import com.merxury.blocker.core.testing.util.MainDispatcherRule
import com.merxury.blocker.core.testing.util.TestAnalyticsHelper
import com.merxury.blocker.core.testing.util.TestPermissionMonitor
import kotlinx.coroutines.CoroutineDispatcher
import org.junit.Before
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock

class AppListViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @get:Rule
    val tempFolder: TemporaryFolder = TemporaryFolder.builder()
        .assureDeletion()
        .build()

    private val analyticsHelper = TestAnalyticsHelper()
    private val userDataRepository = TestUserDataRepository()
    private val appRepository = TestAppRepository()
    private val componentRepository = TestComponentRepository()
    private val appPropertiesRepository = TestAppPropertiesRepository()
    private val appStateCache = TestAppStateCache()
    private val serviceController = FakeServiceController()
    private val dispatcher: CoroutineDispatcher = mainDispatcherRule.testDispatcher
    private val permissionMonitor = TestPermissionMonitor()
    private val appController = FakeAppController()

    private val packageInfo = mock<PackageInfo> {
        on { toString() } doReturn "MockedPackageInfo"
    }
    private val pm = mock<PackageManager> {
        on { getPackageInfo(any<String>(), any<Int>()) } doReturn packageInfo
        on {
            getPackageInfo(
                any<String>(),
                any<PackageInfoFlags>(),
            )
        } doReturn packageInfo
    }
    private lateinit var viewModel: AppListViewModel

    @Before
    fun setup() {
        val initializeDatabaseUseCase = InitializeDatabaseUseCase(
            appRepository = appRepository,
            componentRepository = componentRepository,
            appPropertiesRepository = appPropertiesRepository,
        )
        val getAppController = GetAppControllerUseCase(
            userDataRepository = userDataRepository,
            rootAppController = appController,
            shizukuAppController = appController,
        )
        val getServiceController = GetServiceControllerUseCase(
            userDataRepository = userDataRepository,
            rootServiceController = serviceController,
            shizukuServiceController = serviceController,
        )
        val searchAppListUseCase = SearchAppListUseCase(
            pm = pm,
            userDataRepository = userDataRepository,
            appRepository = appRepository,
            appStateCache = appStateCache,
            getAppController = getAppController,
            getServiceController = getServiceController,
            cpuDispatcher = dispatcher,
        )
        viewModel = AppListViewModel(
            userDataRepository = userDataRepository,
            analyticsHelper = analyticsHelper,
            permissionMonitor = permissionMonitor,
            pm = pm,
            initializeDatabase = initializeDatabaseUseCase,
            searchAppList = searchAppListUseCase,
            getAppController = getAppController,
            ioDispatcher = dispatcher,
            cpuDispatcher = dispatcher,
            mainDispatcher = dispatcher,
            appRepository = appRepository,
        )
    }
}