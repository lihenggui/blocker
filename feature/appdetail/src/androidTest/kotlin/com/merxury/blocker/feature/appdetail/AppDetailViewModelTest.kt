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

package com.merxury.blocker.feature.appdetail

import android.content.Context
import android.content.pm.PackageManager
import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.ApplicationProvider
import androidx.work.WorkManager
import com.merxury.blocker.core.domain.ZipAllRuleUseCase
import com.merxury.blocker.core.domain.ZipAppRuleUseCase
import com.merxury.blocker.core.domain.components.SearchComponentsUseCase
import com.merxury.blocker.core.domain.controller.GetServiceControllerUseCase
import com.merxury.blocker.core.domain.detail.SearchMatchedRuleInAppUseCase
import com.merxury.blocker.core.domain.model.ComponentSearchResult
import com.merxury.blocker.core.domain.model.MatchedHeaderData
import com.merxury.blocker.core.domain.model.MatchedItem
import com.merxury.blocker.core.extension.getPackageInfoCompat
import com.merxury.blocker.core.model.ComponentType.ACTIVITY
import com.merxury.blocker.core.model.ComponentType.PROVIDER
import com.merxury.blocker.core.model.ComponentType.RECEIVER
import com.merxury.blocker.core.model.ComponentType.SERVICE
import com.merxury.blocker.core.model.data.AppItem
import com.merxury.blocker.core.model.data.ComponentDetail
import com.merxury.blocker.core.model.data.ComponentInfo
import com.merxury.blocker.core.model.data.ControllerType
import com.merxury.blocker.core.model.data.GeneralRule
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
import com.merxury.blocker.core.result.Result
import com.merxury.blocker.core.testing.controller.FakeServiceController
import com.merxury.blocker.core.testing.repository.TestAppRepository
import com.merxury.blocker.core.testing.repository.TestComponentDetailRepository
import com.merxury.blocker.core.testing.repository.TestComponentRepository
import com.merxury.blocker.core.testing.repository.TestGeneralRuleRepository
import com.merxury.blocker.core.testing.repository.TestUserDataRepository
import com.merxury.blocker.core.testing.util.MainDispatcherRule
import com.merxury.blocker.core.testing.util.TestAnalyticsHelper
import com.merxury.blocker.core.ui.AppDetailTabs
import com.merxury.blocker.core.ui.state.toolbar.AppBarUiState
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
    private val generalRuleRepository = TestGeneralRuleRepository()
    private val componentDetailRepository = TestComponentDetailRepository()
    private val serviceController = FakeServiceController()
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
        val getServiceControllerUseCase = GetServiceControllerUseCase(
            userDataRepository = userDataRepository,
            rootServiceController = serviceController,
            shizukuServiceController = serviceController,
        )
        val searchMatchedRuleInAppUseCase = SearchMatchedRuleInAppUseCase(
            componentRepository = componentRepository,
            componentDetailRepository = componentDetailRepository,
            ruleRepository = generalRuleRepository,
            dispatcher = mainDispatcherRule.testDispatcher,
            filesDir = tempFolder.newFolder(),
            ruleBaseFolder = "blocker-general-rule",
        )
        val searchComponents = SearchComponentsUseCase(
            userDataRepository = userDataRepository,
            appRepository = appRepository,
            componentRepository = componentRepository,
            componentDetailRepository = componentDetailRepository,
            getServiceController = getServiceControllerUseCase,
            cpuDispatcher = dispatcher,
        )

        viewModel = AppDetailViewModel(
            savedStateHandle = savedStateHandle,
            pm = pm,
            userDataRepository = userDataRepository,
            appRepository = appRepository,
            componentRepository = componentRepository,
            componentDetailRepository = componentDetailRepository,
            getServiceController = getServiceControllerUseCase,
            analyticsHelper = analyticsHelper,
            workerManager = WorkManager.getInstance(context),
            searchMatchedRuleInAppUseCase = searchMatchedRuleInAppUseCase,
            zipAppRuleUseCase = zipAppRuleUseCase,
            zipAllRuleUseCase = zipAllRuleUseCase,
            ioDispatcher = dispatcher,
            cpuDispatcher = dispatcher,
            mainDispatcher = dispatcher,
            searchComponents = searchComponents,
        )
    }

    @Test
    fun stateIsInitiallyEmpty() = runTest {
        assertEquals(
            AppInfoUiState(AppItem("")),
            viewModel.appInfoUiState.value,
        )
    }

    @Test
    fun stateIsDefaultWhenNotUpdate() = runTest {
        val collectJob1 = launch(UnconfinedTestDispatcher()) {
            viewModel.appInfoUiState.collect()
        }
        val collectJob2 = launch(UnconfinedTestDispatcher()) { viewModel.appBarUiState.collect() }
        val collectJob3 = launch(UnconfinedTestDispatcher()) { viewModel.tabState.collect() }

        assertEquals(AppInfoUiState(AppItem("")), viewModel.appInfoUiState.value)
        assertEquals(AppBarUiState(), viewModel.appBarUiState.value)
        assertEquals(AppDetailTabs.Info, viewModel.tabState.value.selectedItem)

        collectJob1.cancel()
        collectJob2.cancel()
        collectJob3.cancel()
    }

    @Test
    fun stateIsLoadingWhenDataLoading() = runTest {
        val collectJob = launch(UnconfinedTestDispatcher()) {
            viewModel.appInfoUiState.collect()
        }
        assertEquals(
            Result.Loading,
            viewModel.appInfoUiState.value.componentSearchUiState,
        )
        assertEquals(
            Result.Loading,
            viewModel.appInfoUiState.value.matchedRuleUiState,
        )
        collectJob.cancel()
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
        componentRepository.sendComponentList(sampleComponentList)
        componentDetailRepository.sendComponentDetail(sampleComponentDetailList)
        viewModel.loadComponentList()
        viewModel.updateComponentList()
        generalRuleRepository.sendRuleList(sampleRuleList)
        viewModel.loadMatchedRule()

        assertEquals(
            sampleAppList.first().toAppItem(packageInfo),
            viewModel.appInfoUiState.value.appInfo,
        )
        assertEquals(
            Result.Success(
                ComponentSearchResult(
                    app = sampleAppList.first().toAppItem(packageInfo),
                    activity = sampleComponentList.filter { it.type == ACTIVITY },
                    service = sampleComponentList.filter { it.type == SERVICE },
                    receiver = sampleComponentList.filter { it.type == RECEIVER },
                    provider = sampleComponentList.filter { it.type == PROVIDER },
                ),
            ),
            viewModel.appInfoUiState.value.componentSearchUiState,
        )
        assertEquals(
            Result.Success(
                listOf(
                    MatchedItem(
                        header = MatchedHeaderData(
                            title = "Android WorkerManager",
                            uniqueId = "2",
                        ),
                        componentList = sampleComponentList,
                    ),
                ),
            ),
            viewModel.appInfoUiState.value.matchedRuleUiState,
        )

        collectJob.cancel()
    }

    @Test
    fun selectedTabUpdateWhenSwitchTabOrTabChanged() = runTest {
        val collectJob1 = launch(UnconfinedTestDispatcher()) { viewModel.tabState.collect() }
        viewModel.switchTab(AppDetailTabs.Receiver)

        assertEquals(AppDetailTabs.Receiver, viewModel.tabState.value.selectedItem)
        viewModel.changeSearchMode(true)

        collectJob1.cancel()
    }

    @Test
    fun tabAndAppBarUpdateWhenSwitchTabAndSearch() = runTest {
        val collectJob1 = launch(UnconfinedTestDispatcher()) { viewModel.tabState.collect() }
        val collectJob2 = launch(UnconfinedTestDispatcher()) { viewModel.appBarUiState.collect() }
        val collectJob = launch(UnconfinedTestDispatcher()) {
            viewModel.appInfoUiState.collect()
        }

        viewModel.switchTab(AppDetailTabs.Receiver)
        assertEquals(AppDetailTabs.Receiver, viewModel.tabState.value.selectedItem)

        appRepository.sendAppList(sampleAppList)
        viewModel.loadAppInfo()
        componentRepository.sendComponentList(sampleComponentList)
        componentDetailRepository.sendComponentDetail(sampleComponentDetailList)
        viewModel.loadComponentList()
        viewModel.updateComponentList()
        viewModel.changeSearchMode(true)
        viewModel.search("test123456")
        assertEquals(AppDetailTabs.Info, viewModel.tabState.value.selectedItem)

        collectJob1.cancel()
        collectJob2.cancel()
        collectJob.cancel()
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

private val sampleComponentList = listOf(
    ComponentInfo(
        simpleName = "ExampleActivity",
        name = "com.merxury.blocker.feature.appdetail.component.ExampleActivity",
        packageName = "com.merxury.blocker",
        description = "An example activity",
        type = ACTIVITY,
        pmBlocked = true,
        ifwBlocked = true,
        isRunning = true,
    ),
    ComponentInfo(
        name = "ComponentActivity",
        simpleName = "ComponentActivity",
        packageName = "com.merxury.blocker",
        description = "An example activity",
        pmBlocked = false,
        ifwBlocked = true,
        isRunning = false,
        type = ACTIVITY,
    ),
    ComponentInfo(
        name = "AlarmManagerSchedulerBroadcast",
        simpleName = "AlarmManagerSchedulerBroadcast",
        packageName = "com.merxury.blocker",
        description = "An example activity",
        pmBlocked = false,
        type = RECEIVER,
    ),
)

private val sampleComponentDetailList = listOf(
    ComponentDetail(
        name = "com.merxury.blocker.feature.appdetail.component.ExampleActivity",
        sdkName = "ExampleActivity",
        description = "An example activity",
        disableEffect = "Disable effect",
        contributor = "Contributor",
        addedVersion = "1.0.0",
        removedVersion = "2.0.0",
        recommendToBlock = true,
        lastUpdateTime = System.now(),
    ),
    ComponentDetail(
        name = "ComponentActivity",
        sdkName = "ComponentActivity",
        description = "An example activity",
        disableEffect = "Disable effect",
        contributor = "Contributor",
        addedVersion = "1.0.0",
        removedVersion = "2.0.0",
        recommendToBlock = true,
        lastUpdateTime = System.now(),
    ),
    ComponentDetail(
        name = "AlarmManagerSchedulerBroadcast",
        sdkName = "AlarmManagerSchedulerBroadcast",
        description = "An example activity",
        disableEffect = "Disable effect",
        contributor = "Contributor",
        addedVersion = "1.0.0",
        removedVersion = "2.0.0",
        recommendToBlock = true,
        lastUpdateTime = System.now(),
    ),
)

private val sampleRuleList = listOf(
    GeneralRule(
        id = 1,
        name = "AWS SDK for Kotlin (Developer Preview)",
        iconUrl = null,
        company = "Amazon",
        description = "The AWS SDK for Kotlin simplifies the use of AWS services by " +
            "providing a set of libraries that are consistent and familiar for " +
            "Kotlin developers. All AWS SDKs support API lifecycle considerations " +
            "such as credential management, retries, data marshaling, and serialization.",
        sideEffect = "Unknown",
        safeToBlock = true,
        contributors = listOf("Online contributor"),
        searchKeyword = listOf("androidx.google.example1"),
    ),
    GeneralRule(
        id = 2,
        name = "Android WorkerManager",
        iconUrl = null,
        company = "Google",
        description = "WorkManager is the recommended solution for persistent work. " +
            "Work is persistent when it remains scheduled through app restarts and " +
            "system reboots. Because most background processing is best accomplished " +
            "through persistent work, WorkManager is the primary recommended API for " +
            "background processing.",
        sideEffect = "Background works won't be able to execute",
        safeToBlock = false,
        contributors = listOf("Google"),
        searchKeyword = listOf(
            "androidx.google.example1",
            "com.merxury.blocker.feature.appdetail.component.ExampleActivity",
            "ComponentActivity",
            "AlarmManagerSchedulerBroadcast",
        ),
    ),
    GeneralRule(
        id = 3,
        name = "Android WorkerManager Test",
    ),
)
