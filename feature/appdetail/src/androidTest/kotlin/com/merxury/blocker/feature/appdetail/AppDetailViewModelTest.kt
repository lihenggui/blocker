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
import com.merxury.blocker.core.domain.ZipAllRuleUseCase
import com.merxury.blocker.core.domain.ZipAppRuleUseCase
import com.merxury.blocker.core.extension.getPackageInfoCompat
import com.merxury.blocker.core.model.data.ControllerType
import com.merxury.blocker.core.model.data.InstalledApp
import com.merxury.blocker.core.model.data.toAppItem
import com.merxury.blocker.core.model.preference.AppSorting
import com.merxury.blocker.core.model.preference.ComponentShowPriority
import com.merxury.blocker.core.model.preference.ComponentSorting.PACKAGE_NAME
import com.merxury.blocker.core.model.preference.DarkThemeConfig
import com.merxury.blocker.core.model.preference.RuleServerProvider
import com.merxury.blocker.core.model.preference.SortingOrder
import com.merxury.blocker.core.model.preference.SortingOrder.ASCENDING
import com.merxury.blocker.core.model.preference.UserPreferenceData
import com.merxury.blocker.core.testing.controller.TestServiceController
import com.merxury.blocker.core.testing.repository.TestAppRepository
import com.merxury.blocker.core.testing.repository.TestComponentDetailRepository
import com.merxury.blocker.core.testing.repository.TestComponentRepository
import com.merxury.blocker.core.testing.repository.TestUserDataRepository
import com.merxury.blocker.core.testing.util.MainDispatcherRule
import com.merxury.blocker.core.testing.util.TestAnalyticsHelper
import com.merxury.blocker.core.ui.AppDetailTabs
import com.merxury.blocker.core.ui.state.toolbar.AppBarAction.MORE
import com.merxury.blocker.core.ui.state.toolbar.AppBarAction.SEARCH
import com.merxury.blocker.core.ui.state.toolbar.AppBarUiState
import com.merxury.blocker.feature.appdetail.AppInfoUiState.Loading
import com.merxury.blocker.feature.appdetail.AppInfoUiState.Success
import com.merxury.blocker.feature.appdetail.navigation.KEYWORD_ARG
import com.merxury.blocker.feature.appdetail.navigation.PACKAGE_NAME_ARG
import com.merxury.blocker.feature.appdetail.navigation.TAB_ARG
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock.System
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import kotlin.test.assertEquals

class AppDetailViewModelTest {
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
    private val componentDetailRepository = TestComponentDetailRepository()
    private val serviceController = TestServiceController()
    private val dispatcher: CoroutineDispatcher = mainDispatcherRule.testDispatcher
    private val savedStateHandle = SavedStateHandle(
        mapOf(
            PACKAGE_NAME_ARG to sampleAppList.first().packageName,
            TAB_ARG to AppDetailTabs.INFO,
            KEYWORD_ARG to "",
        ),
    )
    private lateinit var viewModel: AppDetailViewModel
    private lateinit var context: Context
    private lateinit var pm: PackageManager

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        pm = context.packageManager
        val zipAppRuleUseCase = ZipAppRuleUseCase(
            componentRepository = componentRepository,
            userDataRepository = userDataRepository,
            cacheDir = tempFolder.newFolder(),
            filesDir = tempFolder.newFolder(),
            ruleBaseFolder = "user-generated-rule",
            ioDispatcher = dispatcher,
        )
        val zipAllRuleUseCase = ZipAllRuleUseCase(
            userDataRepository = userDataRepository,
            cacheDir = tempFolder.newFolder(),
            filesDir = tempFolder.newFolder(),
            ruleBaseFolder = "user-generated-rule",
            ioDispatcher = dispatcher,
        )
        viewModel = AppDetailViewModel(
            savedStateHandle = savedStateHandle,
            pm = pm,
            userDataRepository = userDataRepository,
            appRepository = appRepository,
            componentRepository = componentRepository,
            componentDetailRepository = componentDetailRepository,
            shizukuServiceController = serviceController,
            rootApiServiceController = serviceController,
            analyticsHelper = analyticsHelper,
            workerManager = WorkManager.getInstance(context),
            zipAppRuleUseCase = zipAppRuleUseCase,
            zipAllRuleUseCase = zipAllRuleUseCase,
            ioDispatcher = dispatcher,
            cpuDispatcher = dispatcher,
            mainDispatcher = dispatcher,
        )
    }

    @Test
    fun stateIsInitiallyLoading() = runTest {
        assertEquals(
            Loading,
            viewModel.appInfoUiState.value,
        )
    }

    @Test
    fun stateIsLoadingWhenDataAreLoading() = runTest {
        val collectJob = launch(UnconfinedTestDispatcher()) {
            viewModel.appInfoUiState.collect()
        }

        assertEquals(
            Loading,
            viewModel.appInfoUiState.value,
        )

        collectJob.cancel()
    }

    @Test
    fun stateIsDefaultWhenNotUpdate() = runTest {
        val collectJob1 = launch(UnconfinedTestDispatcher()) {
            viewModel.componentListUiState.collect()
        }
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
        val collectJob = launch(UnconfinedTestDispatcher()) {
            viewModel.appInfoUiState.collect()
        }

        appRepository.sendAppList(sampleAppList)
        userDataRepository.sendUserData(sampleUserData)
        val packageName = sampleAppList.first().packageName
        val packageInfo = pm.getPackageInfoCompat(packageName, 0)
        viewModel.loadAppInfo()

        assertEquals(
            Success(
                appInfo = sampleAppList.first().toAppItem(packageInfo),
            ),
            viewModel.appInfoUiState.value,
        )

        collectJob.cancel()
    }

    @Test
    fun tabAndAppBarUpdateWhenSwitchTabAndSearch() = runTest {
        val collectJob1 = launch(UnconfinedTestDispatcher()) { viewModel.tabState.collect() }
        val collectJob2 = launch(UnconfinedTestDispatcher()) { viewModel.appBarUiState.collect() }

        viewModel.switchTab(AppDetailTabs.Receiver)

        assertEquals(AppDetailTabs.Receiver, viewModel.tabState.value.selectedItem)
        viewModel.changeSearchMode(true)
        assertEquals(
            AppBarUiState(
                actions = listOf(
                    SEARCH,
                    MORE,
                ),
                isSearchMode = true,
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
    ruleServerProvider = RuleServerProvider.JIHULAB,
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
    appDisplayLanguage = "",
    libDisplayLanguage = "",
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
