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

package com.merxury.blocker.feature.search

import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.pm.PackageManager.PackageInfoFlags
import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.merxury.blocker.core.domain.InitializeDatabaseUseCase
import com.merxury.blocker.core.domain.SearchGeneralRuleUseCase
import com.merxury.blocker.core.domain.applist.SearchAppListUseCase
import com.merxury.blocker.core.domain.controller.GetAppControllerUseCase
import com.merxury.blocker.core.domain.controller.GetServiceControllerUseCase
import com.merxury.blocker.core.model.ComponentType.ACTIVITY
import com.merxury.blocker.core.model.ComponentType.PROVIDER
import com.merxury.blocker.core.model.ComponentType.RECEIVER
import com.merxury.blocker.core.model.ComponentType.SERVICE
import com.merxury.blocker.core.model.data.AppItem
import com.merxury.blocker.core.model.data.ComponentInfo
import com.merxury.blocker.core.model.data.FilteredComponent
import com.merxury.blocker.core.model.data.GeneralRule
import com.merxury.blocker.core.model.data.InstalledApp
import com.merxury.blocker.core.model.data.toAppItem
import com.merxury.blocker.core.testing.controller.FakeAppController
import com.merxury.blocker.core.testing.controller.FakeServiceController
import com.merxury.blocker.core.testing.data.TestAppStateCache
import com.merxury.blocker.core.testing.repository.TestAppPropertiesRepository
import com.merxury.blocker.core.testing.repository.TestAppRepository
import com.merxury.blocker.core.testing.repository.TestComponentRepository
import com.merxury.blocker.core.testing.repository.TestGeneralRuleRepository
import com.merxury.blocker.core.testing.repository.TestUserDataRepository
import com.merxury.blocker.core.testing.repository.defaultUserData
import com.merxury.blocker.core.testing.util.MainDispatcherRule
import com.merxury.blocker.core.testing.util.TestAnalyticsHelper
import com.merxury.blocker.core.ui.SearchScreenTabs
import com.merxury.blocker.core.ui.TabState
import junit.framework.TestCase.assertEquals
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

class SearchViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @get:Rule
    val tempFolder: TemporaryFolder = TemporaryFolder.builder()
        .assureDeletion()
        .build()

    private val analyticsHelper = TestAnalyticsHelper()
    private val userDataRepository = TestUserDataRepository()
    private val appRepository = TestAppRepository()
    private val appPropertiesRepository = TestAppPropertiesRepository()
    private val appStateCache = TestAppStateCache()
    private val componentRepository = TestComponentRepository()
    private val appController = FakeAppController()
    private val generalRuleRepository = TestGeneralRuleRepository()
    private val serviceController = FakeServiceController()
    private val dispatcher: CoroutineDispatcher = mainDispatcherRule.testDispatcher
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
    private val savedStateHandle = SavedStateHandle()
    private lateinit var viewModel: SearchViewModel

    @Before
    fun setup() {
        val initializeDatabase = InitializeDatabaseUseCase(
            appRepository = appRepository,
            componentRepository = componentRepository,
            appPropertiesRepository = appPropertiesRepository,
        )
        val getAppControllerUseCase = GetAppControllerUseCase(
            userDataRepository = userDataRepository,
            rootAppController = appController,
            shizukuAppController = appController,
        )
        val getServiceController = GetServiceControllerUseCase(
            userDataRepository = userDataRepository,
            rootServiceController = serviceController,
            shizukuServiceController = serviceController,
        )
        val searchAppList = SearchAppListUseCase(
            pm = pm,
            appRepository = appRepository,
            userDataRepository = userDataRepository,
            appStateCache = appStateCache,
            getAppController = getAppControllerUseCase,
            getServiceController = getServiceController,
            cpuDispatcher = dispatcher,
        )
        val searchGeneralRuleUseCase = SearchGeneralRuleUseCase(
            generalRuleRepository = generalRuleRepository,
            userDataRepository = userDataRepository,
            filesDir = tempFolder.newFolder(),
            ruleBaseFolder = tempFolder.newFolder().absolutePath,
        )
        viewModel = SearchViewModel(
            analyticsHelper = analyticsHelper,
            userDataRepository = userDataRepository,
            appRepository = appRepository,
            componentRepository = componentRepository,
            pm = pm,
            initializeDatabase = initializeDatabase,
            searchAppList = searchAppList,
            searchRule = searchGeneralRuleUseCase,
            getAppController = getAppControllerUseCase,
            savedStateHandle = savedStateHandle,
            ioDispatcher = dispatcher,
        )
    }

    @Test
    fun searchUiState_whenInitial_thenShowDefault() {
        assertEquals(SearchUiState(), viewModel.searchUiState.value)
    }

    @Test
    fun localSearchUiState_whenInitial_thenShowDefault() {
        assertEquals(LocalSearchUiState.Idle, viewModel.localSearchUiState.value)
    }

    @Test
    fun tabState_whenInitial_thenShowDefault() {
        assertEquals(
            TabState(
                items = listOf(
                    SearchScreenTabs.App(),
                    SearchScreenTabs.Component(),
                    SearchScreenTabs.Rule(),
                ),
                selectedItem = SearchScreenTabs.App(),
            ),
            viewModel.tabState.value,
        )
    }

    @Test
    fun searchUiState_whenSearch_thenUpdateKeyword() = runTest {
        val collectJob = launch(UnconfinedTestDispatcher()) { viewModel.searchUiState.collect() }
        viewModel.search("blocker")
        viewModel.load()
        assertEquals(
            SearchUiState(keyword = "blocker"),
            viewModel.searchUiState.value,
        )
        collectJob.cancel()
    }

    @Test
    fun localSearchUiState_whenSearch_thenShowResult() = runTest {
        viewModel.localSearchUiState.test {
            userDataRepository.sendUserData(defaultUserData)
            appRepository.sendAppList(sampleAppList)
            componentRepository.sendComponentList(sampleComponentList)
            generalRuleRepository.sendRuleList(sampleRuleList)
            viewModel.search("blocker")
            viewModel.load()
            val matchedAppList: List<AppItem> =
                sampleAppList.filter { it.label.contains("blocker") }
                    .map { it.toAppItem().copy(packageInfo = packageInfo) }
            val matchedComponentList = listOf(
                FilteredComponent(
                    app = sampleAppList[0].toAppItem().copy(packageInfo = packageInfo),
                    activity = sampleComponentList.filter { it.packageName == sampleAppList[0].packageName && it.type == ACTIVITY },
                    service = sampleComponentList.filter { it.packageName == sampleAppList[0].packageName && it.type == SERVICE },
                    receiver = sampleComponentList.filter { it.packageName == sampleAppList[0].packageName && it.type == RECEIVER },
                    provider = sampleComponentList.filter { it.packageName == sampleAppList[0].packageName && it.type == PROVIDER },
                ),
            )
            val searchedRuleList = sampleRuleList.filter { it.name.contains("blocker") }
            assertEquals(LocalSearchUiState.Idle, awaitItem())
            assertEquals(LocalSearchUiState.Loading, awaitItem())
            assertEquals(
                LocalSearchUiState.Success(
                    searchKeyword = listOf("blocker"),
                    appTabUiState = AppTabUiState(
                        list = matchedAppList,
                    ),
                    componentTabUiState = ComponentTabUiState(
                        list = matchedComponentList,
                    ),
                    ruleTabUiState = RuleTabUiState(
                        matchedRules = searchedRuleList.filter { it.matchedAppCount > 0 },
                        unmatchedRules = searchedRuleList.filter { it.matchedAppCount == 0 },
                    ),
                ),
                awaitItem(),
            )
        }
    }

    @Test
    fun tabState_whenSearch_thenUpdateItems() = runTest {
        val collectJob = launch(UnconfinedTestDispatcher()) { viewModel.searchUiState.collect() }
        userDataRepository.sendUserData(defaultUserData)
        appRepository.sendAppList(sampleAppList)
        componentRepository.sendComponentList(sampleComponentList)
        generalRuleRepository.sendRuleList(sampleRuleList)
        viewModel.search(SEARCH_KEYWORD)
        viewModel.load()
        val matchedAppList: List<AppItem> =
            sampleAppList.filter { it.label.contains(SEARCH_KEYWORD) }
                .map { it.toAppItem().copy(packageInfo = packageInfo) }
        val matchedComponentList = listOf(
            FilteredComponent(
                app = sampleAppList[0].toAppItem().copy(packageInfo = packageInfo),
                activity = sampleComponentList.filter { it.packageName == sampleAppList[0].packageName && it.type == ACTIVITY },
                service = sampleComponentList.filter { it.packageName == sampleAppList[0].packageName && it.type == SERVICE },
                receiver = sampleComponentList.filter { it.packageName == sampleAppList[0].packageName && it.type == RECEIVER },
                provider = sampleComponentList.filter { it.packageName == sampleAppList[0].packageName && it.type == PROVIDER },
            ),
        )
        val searchedRuleList = sampleRuleList.filter { it.name.contains("blocker") }
        assertEquals(
            TabState(
                items = listOf(
                    SearchScreenTabs.App(
                        count = matchedAppList.size,
                    ),
                    SearchScreenTabs.Component(
                        count = matchedComponentList.size,
                    ),
                    SearchScreenTabs.Rule(
                        count = searchedRuleList.size,
                    ),
                ),
                selectedItem = SearchScreenTabs.App(),
            ),
            viewModel.tabState.value,
        )
        collectJob.cancel()
    }

    @Test
    fun tabState_whenSwitchTab_thenUpdateSelectedItem() = runTest {
        val collectJob = launch(UnconfinedTestDispatcher()) { viewModel.searchUiState.collect() }
        viewModel.switchTab(SearchScreenTabs.Component())
        assertEquals(
            TabState(
                items = listOf(
                    SearchScreenTabs.App(),
                    SearchScreenTabs.Component(),
                    SearchScreenTabs.Rule(),
                ),
                selectedItem = SearchScreenTabs.Component(),
            ),
            viewModel.tabState.value,
        )
        collectJob.cancel()
    }

    @Test
    fun searchUiState_whenSwitchSelectedMode_thenUpdateSelectedMode() = runTest {
        val collectJob = launch(UnconfinedTestDispatcher()) { viewModel.searchUiState.collect() }
        viewModel.switchSelectedMode(true)
        assertEquals(
            SearchUiState(isSelectedMode = true),
            viewModel.searchUiState.value,
        )
        viewModel.switchSelectedMode(false)
        assertEquals(
            SearchUiState(isSelectedMode = false),
            viewModel.searchUiState.value,
        )
        collectJob.cancel()
    }

    @Test
    fun searchUiState_whenExitSelectedMode_thenClearSelectedList() = runTest {
        val collectJob = launch(UnconfinedTestDispatcher()) { viewModel.searchUiState.collect() }

        userDataRepository.sendUserData(defaultUserData)
        appRepository.sendAppList(sampleAppList)
        componentRepository.sendComponentList(sampleComponentList)
        generalRuleRepository.sendRuleList(sampleRuleList)

        val targetItem = FilteredComponent(
            app = sampleAppList[0].toAppItem().copy(packageInfo = packageInfo),
            activity = sampleComponentList.filter { it.packageName == sampleAppList[0].packageName && it.type == ACTIVITY },
            service = sampleComponentList.filter { it.packageName == sampleAppList[0].packageName && it.type == SERVICE },
            receiver = sampleComponentList.filter { it.packageName == sampleAppList[0].packageName && it.type == RECEIVER },
            provider = sampleComponentList.filter { it.packageName == sampleAppList[0].packageName && it.type == PROVIDER },
        )
        viewModel.switchSelectedMode(true)
        viewModel.selectItem(
            item = targetItem,
        )
        viewModel.switchSelectedMode(false)
        assertEquals(
            SearchUiState(
                isSelectedMode = false,
                selectedAppList = emptyList(),
                selectedComponentList = emptyList(),
            ),
            viewModel.searchUiState.value,
        )

        collectJob.cancel()
    }

    @Test
    fun searchUiState_whenSelectDeselectApps_thenUpdateSelectedList() = runTest {
        val collectJob = launch(UnconfinedTestDispatcher()) { viewModel.searchUiState.collect() }

        userDataRepository.sendUserData(defaultUserData)
        appRepository.sendAppList(sampleAppList)
        componentRepository.sendComponentList(sampleComponentList)
        generalRuleRepository.sendRuleList(sampleRuleList)

        val targetItem = FilteredComponent(
            app = sampleAppList[0].toAppItem().copy(packageInfo = packageInfo),
            activity = sampleComponentList.filter { it.packageName == sampleAppList[0].packageName && it.type == ACTIVITY },
            service = sampleComponentList.filter { it.packageName == sampleAppList[0].packageName && it.type == SERVICE },
            receiver = sampleComponentList.filter { it.packageName == sampleAppList[0].packageName && it.type == RECEIVER },
            provider = sampleComponentList.filter { it.packageName == sampleAppList[0].packageName && it.type == PROVIDER },
        )
        viewModel.switchSelectedMode(true)
        viewModel.selectItem(
            item = targetItem,
        )
        assertEquals(
            SearchUiState(
                isSelectedMode = true,
                selectedAppList = listOf(targetItem),
                selectedComponentList = sampleComponentList.filter { it.packageName == sampleAppList[0].packageName },
            ),
            viewModel.searchUiState.value,
        )

        viewModel.deselectItem(
            item = targetItem,
        )
        assertEquals(
            SearchUiState(
                isSelectedMode = true,
                selectedAppList = emptyList(),
                selectedComponentList = emptyList(),
            ),
            viewModel.searchUiState.value,
        )

        collectJob.cancel()
    }

    @Test
    fun searchUiState_whenSelectAll_thenUpdateSelectedList() = runTest {
        val collectJob = launch(UnconfinedTestDispatcher()) { viewModel.searchUiState.collect() }

        userDataRepository.sendUserData(defaultUserData)
        appRepository.sendAppList(sampleAppList)
        componentRepository.sendComponentList(sampleComponentList)
        generalRuleRepository.sendRuleList(sampleRuleList)

        viewModel.search(SEARCH_KEYWORD)
        viewModel.load()
        val matchedAppList = listOf(
            FilteredComponent(
                app = sampleAppList[0].toAppItem().copy(packageInfo = packageInfo),
                activity = sampleComponentList.filter { it.packageName == sampleAppList[0].packageName && it.type == ACTIVITY },
                service = sampleComponentList.filter { it.packageName == sampleAppList[0].packageName && it.type == SERVICE },
                receiver = sampleComponentList.filter { it.packageName == sampleAppList[0].packageName && it.type == RECEIVER },
                provider = sampleComponentList.filter { it.packageName == sampleAppList[0].packageName && it.type == PROVIDER },
            ),
        )
        val matchedComponentList =
            sampleComponentList.filter { it.packageName == sampleAppList[0].packageName }
        viewModel.switchSelectedMode(true)
        viewModel.selectAll()
        assertEquals(
            SearchUiState(
                keyword = SEARCH_KEYWORD,
                isSelectedMode = true,
                selectedAppList = matchedAppList,
                selectedComponentList = matchedComponentList,
            ),
            viewModel.searchUiState.value,
        )

        viewModel.selectAll()
        assertEquals(
            SearchUiState(
                keyword = SEARCH_KEYWORD,
                isSelectedMode = true,
                selectedAppList = emptyList(),
                selectedComponentList = emptyList(),
            ),
            viewModel.searchUiState.value,
        )

        collectJob.cancel()
    }
}

private const val SEARCH_KEYWORD = "blocker"

private val sampleAppList = listOf(
    InstalledApp(
        label = "blocker",
        packageName = "com.merxury.blocker.test1",
    ),
    InstalledApp(
        label = "blocker1",
        packageName = "com.merxury.blocker1.test2",
    ),
    InstalledApp(
        label = "App3",
        packageName = "com.merxury.test3",
    ),
)

private val sampleComponentList = listOf(
    ComponentInfo(
        simpleName = "Activity1",
        name = "com.merxury.blocker.test1.activity1",
        packageName = "com.merxury.blocker.test1",
        type = ACTIVITY,
        description = "An example activity",
    ),
    ComponentInfo(
        simpleName = "Service1",
        name = "com.merxury.blocker.test1.service1",
        packageName = "com.merxury.blocker.test1",
        type = SERVICE,
        description = "An example service",
        pmBlocked = true,
    ),
    ComponentInfo(
        simpleName = "Service2",
        name = "com.merxury.test.service2",
        packageName = "com.merxury.test1",
        type = SERVICE,
        description = "An example service",
    ),
    ComponentInfo(
        simpleName = "Receiver1",
        name = "com.merxury.blocker.test.receiver1",
        packageName = "com.merxury.test1",
        type = RECEIVER,
        description = "An example receiver",
    ),
    ComponentInfo(
        simpleName = "Provider1",
        name = "com.merxury.test.provider1",
        packageName = "com.merxury.test1",
        type = PROVIDER,
        description = "An example provider",
    ),
)

private val sampleRuleList = listOf(
    GeneralRule(
        id = 1,
        name = "Test rule blocker",
        searchKeyword = listOf("activity", "service", "receiver", "provider"),
        matchedAppCount = 1,
    ),
    GeneralRule(
        id = 2,
        name = "Test rule 2 blocker",
        searchKeyword = listOf("test2"),
    ),
    GeneralRule(
        id = 3,
        name = "Test rule 3",
        searchKeyword = listOf("test3"),
    ),
)
