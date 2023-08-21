/*
 * Copyright 2023 Blocker
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

package com.merxury.blocker.feature.appdetail

import android.content.Context
import android.content.pm.PackageManager
import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.ApplicationProvider
import androidx.work.WorkManager
import com.merxury.blocker.core.extension.getPackageInfoCompat
import com.merxury.blocker.core.model.ComponentType
import com.merxury.blocker.core.model.data.ComponentInfo
import com.merxury.blocker.core.model.data.ControllerType
import com.merxury.blocker.core.model.data.InstalledApp
import com.merxury.blocker.core.model.preference.AppSorting
import com.merxury.blocker.core.model.preference.ComponentShowPriority
import com.merxury.blocker.core.model.preference.ComponentShowPriority.ENABLED_COMPONENTS_FIRST
import com.merxury.blocker.core.model.preference.ComponentSorting.COMPONENT_NAME
import com.merxury.blocker.core.model.preference.ComponentSorting.PACKAGE_NAME
import com.merxury.blocker.core.model.preference.DarkThemeConfig
import com.merxury.blocker.core.model.preference.RuleServerProvider
import com.merxury.blocker.core.model.preference.SortingOrder
import com.merxury.blocker.core.model.preference.SortingOrder.ASCENDING
import com.merxury.blocker.core.model.preference.UserPreferenceData
import com.merxury.blocker.core.testing.controller.FakeShizukuInitializer
import com.merxury.blocker.core.testing.repository.TestAppRepository
import com.merxury.blocker.core.testing.repository.TestComponentDetailRepository
import com.merxury.blocker.core.testing.repository.TestComponentRepository
import com.merxury.blocker.core.testing.repository.TestUserDataRepository
import com.merxury.blocker.core.testing.util.MainDispatcherRule
import com.merxury.blocker.core.testing.util.TestAnalyticsHelper
import com.merxury.blocker.core.ui.AppDetailTabs
import com.merxury.blocker.core.ui.applist.model.toAppItem
import com.merxury.blocker.core.ui.bottomsheet.ComponentSortInfo
import com.merxury.blocker.core.ui.bottomsheet.ComponentSortInfoUiState
import com.merxury.blocker.core.ui.state.toolbar.AppBarAction.MORE
import com.merxury.blocker.core.ui.state.toolbar.AppBarAction.SEARCH
import com.merxury.blocker.core.ui.state.toolbar.AppBarUiState
import com.merxury.blocker.feature.appdetail.AppInfoUiState.Loading
import com.merxury.blocker.feature.appdetail.AppInfoUiState.Success
import com.merxury.blocker.feature.appdetail.navigation.keywordArg
import com.merxury.blocker.feature.appdetail.navigation.packageNameArg
import com.merxury.blocker.feature.appdetail.navigation.tabArg
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock.System
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals

class AppDetailViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val analyticsHelper = TestAnalyticsHelper()
    private val userDataRepository = TestUserDataRepository()
    private val appRepository = TestAppRepository()
    private val componentRepository = TestComponentRepository()
    private val componentDetailRepository = TestComponentDetailRepository()
    private val shizukuInitializer = FakeShizukuInitializer()
    private val ioDispatcher: CoroutineDispatcher = mainDispatcherRule.testDispatcher
    private val cpuDispatcher: CoroutineDispatcher = mainDispatcherRule.testDispatcher
    private val savedStateHandle = SavedStateHandle(
        mapOf(
            packageNameArg to sampleAppList.first().packageName,
            tabArg to AppDetailTabs.INFO,
            keywordArg to "",
        ),
    )
    private lateinit var viewModel: AppDetailViewModel
    private lateinit var context: Context
    private lateinit var pm: PackageManager

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        pm = context.packageManager
        viewModel = AppDetailViewModel(
            savedStateHandle = savedStateHandle,
            pm = pm,
            userDataRepository = userDataRepository,
            appRepository = appRepository,
            componentRepository = componentRepository,
            componentDetailRepository = componentDetailRepository,
            shizukuInitializer = shizukuInitializer,
            analyticsHelper = analyticsHelper,
            workerManager = WorkManager.getInstance(context),
            ioDispatcher = ioDispatcher,
            cpuDispatcher = cpuDispatcher,
        )
    }

    @Test
    fun stateIsInitiallyLoading() = runTest {
        assertEquals(
            Loading,
            viewModel.appInfoUiState.value,
        )
        assertEquals(ComponentSortInfoUiState.Loading, viewModel.componentSortInfoUiState.value)
    }

    @Test
    fun stateIsLoadingWhenDataAreLoading() = runTest {
        val collectJob1 =
            launch(UnconfinedTestDispatcher()) { viewModel.appInfoUiState.collect() }
        val collectJob2 =
            launch(UnconfinedTestDispatcher()) { viewModel.componentSortInfoUiState.collect() }

        assertEquals(
            Loading,
            viewModel.appInfoUiState.value,
        )
        assertEquals(ComponentSortInfoUiState.Loading, viewModel.componentSortInfoUiState.value)

        collectJob1.cancel()
        collectJob2.cancel()
    }

    @Test
    fun stateIsDefaultWhenNotUpdate() = runTest {
        val collectJob1 =
            launch(UnconfinedTestDispatcher()) { viewModel.componentListUiState.collect() }
        val collectJob2 = launch(UnconfinedTestDispatcher()) { viewModel.appBarUiState.collect() }
        val collectJob3 = launch(UnconfinedTestDispatcher()) { viewModel.tabState.collect() }

        assertEquals(0, viewModel.componentListUiState.value.activity.size)
        assertEquals(0, viewModel.componentListUiState.value.provider.size)
        assertEquals(0, viewModel.componentListUiState.value.receiver.size)
        assertEquals(0, viewModel.componentListUiState.value.service.size)
        assertEquals(AppBarUiState(), viewModel.appBarUiState.value)
        assertEquals(AppDetailTabs.Info, viewModel.tabState.value.selectedItem)

        collectJob1.cancel()
        collectJob2.cancel()
        collectJob3.cancel()
    }

    @Test
    fun stateIsSuccessWhenGetData() = runTest {
        val collectJob1 =
            launch(UnconfinedTestDispatcher()) { viewModel.appInfoUiState.collect() }
        val collectJob2 =
            launch(UnconfinedTestDispatcher()) { viewModel.componentSortInfoUiState.collect() }

        appRepository.sendAppList(sampleAppList)
        userDataRepository.sendUserData(sampleUserData)
        val packageName = sampleAppList.first().packageName
        val packageInfo = pm.getPackageInfoCompat(packageName, 0)
        viewModel.loadAppInfo()
        viewModel.loadComponentSortInfo()

        assertEquals(
            Success(
                appInfo = sampleAppList.first().toAppItem(packageInfo),
                iconBasedTheming = null,
            ),
            viewModel.appInfoUiState.value,
        )
        assertEquals(
            ComponentSortInfoUiState.Success(
                ComponentSortInfo(
                    sorting = sampleUserData.componentSorting,
                    order = sampleUserData.componentSortingOrder,
                    priority = sampleUserData.componentShowPriority,
                ),
            ),
            viewModel.componentSortInfoUiState.value,
        )

        collectJob1.cancel()
        collectJob2.cancel()
    }

    @Test
    fun componentSortInfoUiStateUpdateAfterChanged() = runTest {
        val collectJob =
            launch(UnconfinedTestDispatcher()) { viewModel.componentSortInfoUiState.collect() }

        userDataRepository.sendUserData(sampleUserData)
        viewModel.loadComponentSortInfo()

        assertEquals(
            ComponentSortInfoUiState.Success(
                ComponentSortInfo(
                    sorting = sampleUserData.componentSorting,
                    order = sampleUserData.componentSortingOrder,
                    priority = sampleUserData.componentShowPriority,
                ),
            ),
            viewModel.componentSortInfoUiState.value,
        )

        viewModel.updateComponentSorting(sorting = COMPONENT_NAME)
        viewModel.updateComponentSortingOrder(order = ASCENDING)
        viewModel.updateComponentShowPriority(priority = ENABLED_COMPONENTS_FIRST)
        viewModel.loadComponentSortInfo()

        assertEquals(
            ComponentSortInfoUiState.Success(
                ComponentSortInfo(
                    sorting = COMPONENT_NAME,
                    order = ASCENDING,
                    priority = ENABLED_COMPONENTS_FIRST,
                ),
            ),
            viewModel.componentSortInfoUiState.value,
        )

        collectJob.cancel()
    }

    @Test
    fun tabAndAppBarWhenHasSearchKeyword() = runTest {
        val collectJob1 = launch(UnconfinedTestDispatcher()) { viewModel.appBarUiState.collect() }
        val collectJob2 = launch(UnconfinedTestDispatcher()) { viewModel.tabState.collect() }
        /*
                savedStateHandle[tabArg] = RECEIVER
                savedStateHandle[keywordArg] = "receiver"
                viewModel.updateSearchKeyword()
                viewModel.loadTabInfo()
                assertEquals(
                    AppBarUiState(
                        keyword = TextFieldValue("receiver"),
                        isSearchMode = true,
                        actions = listOf(
                            SEARCH, MORE,
                        ),
                    ),
                    viewModel.appBarUiState.value,
                )
                assertEquals(
                    Receiver,
                    viewModel.tabState.value.selectedItem,
                )
        */
        collectJob1.cancel()
        collectJob2.cancel()
    }

    @Test
    fun componentListUpdateWhenGetData() = runTest {
        val collectJob =
            launch(UnconfinedTestDispatcher()) { viewModel.componentListUiState.collect() }

        componentRepository.sendComponentList(componentList)
        viewModel.loadComponentList()
        /*
                assertEquals(1, viewModel.componentListUiState.value.activity.size)
                assertEquals(1, viewModel.componentListUiState.value.provider.size)
                assertEquals(1, viewModel.componentListUiState.value.receiver.size)
                assertEquals(1, viewModel.componentListUiState.value.service.size)
        */
        collectJob.cancel()
    }

    @Test
    fun tabAndAppBarWhenSwitchTab() = runTest {
        val collectJob1 = launch(UnconfinedTestDispatcher()) { viewModel.tabState.collect() }
        val collectJob2 = launch(UnconfinedTestDispatcher()) { viewModel.appBarUiState.collect() }

        viewModel.switchTab(AppDetailTabs.Receiver)

        assertEquals(AppDetailTabs.Receiver, viewModel.tabState.value.selectedItem)
        assertEquals(
            AppBarUiState(
                actions = listOf(
                    SEARCH, MORE,
                ),
            ),
            viewModel.appBarUiState.value,
        )

        collectJob1.cancel()
        collectJob2.cancel()
    }
}

private val sampleUserData = UserPreferenceData(
    darkThemeConfig = DarkThemeConfig.DARK,
    useDynamicColor = false,
    controllerType = ControllerType.SHIZUKU,
    ruleServerProvider = RuleServerProvider.GITLAB,
    ruleBackupFolder = "",
    backupSystemApp = true,
    restoreSystemApp = true,
    showSystemApps = true,
    showServiceInfo = true,
    appSorting = AppSorting.LAST_UPDATE_TIME,
    appSortingOrder = SortingOrder.DESCENDING,
    componentShowPriority = ComponentShowPriority.NONE,
    componentSortingOrder = ASCENDING,
    componentSorting = PACKAGE_NAME,
    isFirstTimeInitializationCompleted = true,
    showRunningAppsOnTop = true,
)

private val sampleAppList = listOf(
    InstalledApp(
        label = "App",
        packageName = "com.merxury.blocker",
        versionName = "1.0.0",
        versionCode = 1,
        minSdkVersion = 33,
        targetSdkVersion = 21,
        isSystem = false,
        isEnabled = true,
        firstInstallTime = System.now(),
        lastUpdateTime = System.now(),
    ),
    InstalledApp(
        label = "App",
        packageName = "com.merxury.test",
        versionName = "23.3.2",
        versionCode = 23,
        minSdkVersion = 33,
        targetSdkVersion = 21,
        isSystem = true,
        isEnabled = true,
        firstInstallTime = System.now(),
        lastUpdateTime = System.now(),
    ),
    InstalledApp(
        label = "App",
        packageName = "com.merxury.system",
        versionName = "0.13.2",
        versionCode = 13,
        minSdkVersion = 33,
        targetSdkVersion = 21,
        isSystem = true,
        isEnabled = false,
        firstInstallTime = System.now(),
    ),
)

private val componentList = listOf(
    ComponentInfo(
        name = "Activity",
        simpleName = "Activity",
        packageName = "com.merxury.blocker",
        type = ComponentType.ACTIVITY,
        exported = true,
        pmBlocked = false,
        ifwBlocked = false,
        description = null,
    ),
    ComponentInfo(
        name = "Provider",
        simpleName = "Provider",
        packageName = "com.merxury.blocker",
        type = ComponentType.PROVIDER,
        exported = true,
        pmBlocked = false,
        ifwBlocked = false,
        description = null,
    ),
    ComponentInfo(
        name = "Receiver",
        simpleName = "Receiver",
        packageName = "com.merxury.blocker",
        type = ComponentType.RECEIVER,
        exported = true,
        pmBlocked = false,
        ifwBlocked = false,
        description = null,
    ),
    ComponentInfo(
        name = "Service",
        simpleName = "Service",
        packageName = "com.merxury.blocker",
        type = ComponentType.SERVICE,
        exported = true,
        pmBlocked = false,
        ifwBlocked = false,
        description = null,
    ),
)
