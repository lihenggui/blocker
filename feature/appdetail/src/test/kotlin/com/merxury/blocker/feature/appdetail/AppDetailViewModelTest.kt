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

import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.pm.PackageManager.PackageInfoFlags
import androidx.lifecycle.SavedStateHandle
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
import com.merxury.blocker.core.model.data.GeneralRule
import com.merxury.blocker.core.model.data.InstalledApp
import com.merxury.blocker.core.model.data.toAppItem
import com.merxury.blocker.core.result.Result
import com.merxury.blocker.core.testing.controller.FakeServiceController
import com.merxury.blocker.core.testing.repository.TestAppRepository
import com.merxury.blocker.core.testing.repository.TestComponentDetailRepository
import com.merxury.blocker.core.testing.repository.TestComponentRepository
import com.merxury.blocker.core.testing.repository.TestGeneralRuleRepository
import com.merxury.blocker.core.testing.repository.TestUserDataRepository
import com.merxury.blocker.core.testing.repository.defaultUserData
import com.merxury.blocker.core.testing.util.MainDispatcherRule
import com.merxury.blocker.core.testing.util.TestAnalyticsHelper
import com.merxury.blocker.core.ui.AppDetailTabs
import com.merxury.blocker.core.ui.state.toolbar.AppBarAction.MORE
import com.merxury.blocker.core.ui.state.toolbar.AppBarAction.SEARCH
import com.merxury.blocker.core.ui.state.toolbar.AppBarUiState
import com.merxury.blocker.feature.appdetail.navigation.KEYWORD_ARG
import com.merxury.blocker.feature.appdetail.navigation.PACKAGE_NAME_ARG
import com.merxury.blocker.feature.appdetail.navigation.TAB_ARG
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
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
    private val workerManager = mock<WorkManager>()

    private lateinit var viewModel: AppDetailViewModel

    @Before
    fun setup() {
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
            workerManager = workerManager,
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
    fun appInfoUiState_whenInitial_thenShowDefault() = runTest {
        assertEquals(
            AppInfoUiState(AppItem("")),
            viewModel.appInfoUiState.value,
        )
    }

    @Test
    fun appBarUiState_whenInitial_thenShowDefault() = runTest {
        assertEquals(AppBarUiState(), viewModel.appBarUiState.value)
    }

    @Test
    fun tabState_whenInitial_thenShowDefault() = runTest {
        assertEquals(AppDetailTabs.Info, viewModel.tabState.value.selectedItem)
    }

    @Test
    fun appInfoUiState_whenSuccess_thenShowData() = runTest {
        val collectJob = launch(UnconfinedTestDispatcher()) {
            viewModel.appInfoUiState.collect()
        }

        appRepository.sendAppList(sampleAppList)
        userDataRepository.sendUserData(defaultUserData)
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
                    app = sampleAppList.first().toAppItem(),
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
                            title = "Test rule 1",
                            uniqueId = "1",
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
    fun tabState_whenSwitchTab_thenUpdateSelectedItem() = runTest {
        val collectJob = launch(UnconfinedTestDispatcher()) { viewModel.tabState.collect() }
        viewModel.switchTab(AppDetailTabs.Receiver)
        assertEquals(AppDetailTabs.Receiver, viewModel.tabState.value.selectedItem)
        collectJob.cancel()
    }

    @Test
    fun appBarUiState_whenSwitchTab_thenUpdateActionButtons() = runTest {
        val collectJob = launch(UnconfinedTestDispatcher()) { viewModel.appBarUiState.collect() }
        viewModel.switchTab(AppDetailTabs.Receiver)
        assertEquals(
            AppBarUiState(
                actions = listOf(
                    SEARCH,
                    MORE,
                ),
            ),
            viewModel.appBarUiState.value,
        )
        collectJob.cancel()
    }

    @Test
    fun appBarUiState_whenSearchNotResult_thenSwitchToInfoTab() = runTest {
        val collectJob1 = launch(UnconfinedTestDispatcher()) { viewModel.appBarUiState.collect() }
        val collectJob2 = launch(UnconfinedTestDispatcher()) { viewModel.tabState.collect() }
        viewModel.switchTab(AppDetailTabs.Receiver)
        viewModel.search("123")
        viewModel.changeSearchMode(true)
        viewModel.loadTabInfo()
        assertEquals(
            AppBarUiState(
                isSearchMode = true,
                keyword = "123",
            ),
            viewModel.appBarUiState.value,
        )
        assertEquals(AppDetailTabs.Info, viewModel.tabState.value.selectedItem)
        collectJob1.cancel()
        collectJob2.cancel()
    }

    @Test
    fun appBarUiState_whenSearchResult_thenShowSearchResult() = runTest {
        val collectJob = launch(UnconfinedTestDispatcher()) { viewModel.appInfoUiState.collect() }
        appRepository.sendAppList(sampleAppList)
        userDataRepository.sendUserData(defaultUserData)
        viewModel.loadAppInfo()
        componentRepository.sendComponentList(sampleComponentList)
        componentDetailRepository.sendComponentDetail(sampleComponentDetailList)
        viewModel.loadComponentList()
        viewModel.search("Activity")
        viewModel.changeSearchMode(true)
        viewModel.loadComponentList()
        assertEquals(
            Result.Success(
                ComponentSearchResult(
                    app = sampleAppList.first().toAppItem(),
                    activity = sampleComponentList.filter { it.type == ACTIVITY },
                ),
            ),
            viewModel.appInfoUiState.value.componentSearchUiState,
        )
        collectJob.cancel()
    }

    @Test
    fun appBarUiState_whenSwitchSelectedMode_thenUpdateSelectedMode() = runTest {
        val collectJob = launch(UnconfinedTestDispatcher()) { viewModel.appBarUiState.collect() }
        viewModel.switchSelectedMode(true)
        assertEquals(
            AppBarUiState(
                isSelectedMode = true,
            ),
            viewModel.appBarUiState.value,
        )
        collectJob.cancel()
    }
}

private val sampleAppList = listOf(
    InstalledApp(
        label = "App1",
        packageName = "com.merxury.test1",
    ),
    InstalledApp(
        label = "App2",
        packageName = "com.merxury.test2",
    ),
    InstalledApp(
        label = "App3",
        packageName = "com.merxury.test3",
    ),
)

private val sampleComponentList = listOf(
    ComponentInfo(
        simpleName = "Activity1",
        name = "com.merxury.blocker.test.activity1",
        packageName = "com.merxury.test1",
        type = ACTIVITY,
        pmBlocked = false,
        description = "An example activity",
    ),
    ComponentInfo(
        simpleName = "Service1",
        name = "com.merxury.blocker.test.service1",
        packageName = "com.merxury.test1",
        pmBlocked = false,
        type = SERVICE,
        description = "An example service",
    ),
    ComponentInfo(
        simpleName = "Receiver1",
        name = "com.merxury.blocker.test.receiver1",
        packageName = "com.merxury.test1",
        pmBlocked = false,
        type = RECEIVER,
        description = "An example receiver",
    ),
    ComponentInfo(
        simpleName = "Provider1",
        name = "com.merxury.blocker.test.provider1",
        packageName = "com.merxury.test1",
        pmBlocked = false,
        type = PROVIDER,
        description = "An example provider",
    ),
)

private val sampleComponentDetailList = listOf(
    ComponentDetail(
        name = "com.merxury.blocker.test.activity1",
        description = "An example activity",
    ),
    ComponentDetail(
        name = "com.merxury.blocker.test.service1",
        description = "An example service",
    ),
    ComponentDetail(
        name = "com.merxury.blocker.test.receiver1",
        description = "An example receiver",
    ),
    ComponentDetail(
        name = "com.merxury.blocker.test.provider1",
        description = "An example provider",
    ),
)

private val sampleRuleList = listOf(
    GeneralRule(
        id = 1,
        name = "Test rule 1",
        searchKeyword = listOf("activity", "service", "receiver", "provider"),
    ),
    GeneralRule(
        id = 2,
        name = "Test rule 2",
        searchKeyword = listOf("test2"),
    ),
    GeneralRule(
        id = 3,
        name = "Test rule 3",
        searchKeyword = listOf("test3"),
    ),
)
