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

package com.merxury.blocker.feature.ruledetail

import android.app.Application
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.pm.PackageManager.PackageInfoFlags
import app.cash.turbine.test
import com.merxury.blocker.core.domain.model.MatchedHeaderData
import com.merxury.blocker.core.domain.model.MatchedItem
import com.merxury.blocker.core.extension.getPackageInfoCompat
import com.merxury.blocker.core.model.ComponentType.ACTIVITY
import com.merxury.blocker.core.model.ComponentType.PROVIDER
import com.merxury.blocker.core.model.ComponentType.RECEIVER
import com.merxury.blocker.core.model.ComponentType.SERVICE
import com.merxury.blocker.core.model.data.ComponentInfo
import com.merxury.blocker.core.model.data.GeneralRule
import com.merxury.blocker.core.model.data.InstalledApp
import com.merxury.blocker.core.result.Result
import com.merxury.blocker.core.testing.repository.TestAppRepository
import com.merxury.blocker.core.testing.repository.TestComponentRepository
import com.merxury.blocker.core.testing.repository.TestGeneralRuleRepository
import com.merxury.blocker.core.testing.repository.TestUserDataRepository
import com.merxury.blocker.core.testing.repository.defaultUserData
import com.merxury.blocker.core.testing.util.MainDispatcherRule
import com.merxury.blocker.core.testing.util.TestAnalyticsHelper
import com.merxury.blocker.core.ui.rule.RuleDetailTabs
import com.merxury.blocker.core.ui.state.toolbar.AppBarAction.MORE
import com.merxury.blocker.core.ui.state.toolbar.AppBarAction.SORT
import com.merxury.blocker.core.ui.state.toolbar.AppBarUiState
import com.merxury.blocker.feature.ruledetail.RuleInfoUiState.Loading
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

/**
 * To learn more about how this test handles Flows created with stateIn, see
 * https://developer.android.com/kotlin/flow/test#statein
 *
 */
class RuleDetailViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @get:Rule
    val tempFolder: TemporaryFolder = TemporaryFolder.builder()
        .assureDeletion()
        .build()

    private val analyticsHelper = TestAnalyticsHelper()
    private val appRepository = TestAppRepository()
    private val generalRuleRepository = TestGeneralRuleRepository()
    private val userDataRepository = TestUserDataRepository()
    private val componentRepository = TestComponentRepository()
    private val dispatcher: CoroutineDispatcher = mainDispatcherRule.testDispatcher
    private val appContext = mock<Application>()
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

    private lateinit var viewModel: RuleDetailViewModel

    @Before
    fun setup() {
        viewModel = RuleDetailViewModel(
            appRepository = appRepository,
            ioDispatcher = dispatcher,
            appContext = appContext,
            pm = pm,
            filesDir = tempFolder.newFolder(),
            ruleBaseFolder = tempFolder.newFolder().absolutePath,
            ruleRepository = generalRuleRepository,
            userDataRepository = userDataRepository,
            componentRepository = componentRepository,
            mainDispatcher = dispatcher,
            analyticsHelper = analyticsHelper,
            ruleId = sampleRuleList.first().id.toString(),
            tab = RuleDetailTabs.APPLICABLE,
        )
    }

    @Test
    fun ruleInfoUiState_whenInitial_thenShowLoading() = runTest {
        assertEquals(Loading, viewModel.ruleInfoUiState.value)
    }

    @Test
    fun tabState_whenInitial_thenShowDefault() = runTest {
        assertEquals(RuleDetailTabs.Applicable, viewModel.tabState.value.selectedItem)
    }

    @Test
    fun appBarUiState_whenInitial_thenShowDefault() = runTest {
        assertEquals(AppBarUiState(actions = listOf(SORT, MORE)), viewModel.appBarUiState.value)
    }

    @Test
    fun ruleInfoUiState_whenSuccess_thenShowData() = runTest {
        viewModel.ruleInfoUiState.test {
            appRepository.sendAppList(sampleAppList)
            userDataRepository.sendUserData(defaultUserData)
            generalRuleRepository.sendRuleList(sampleRuleList)
            componentRepository.sendComponentList(sampleComponentList)
            viewModel.loadData()
            val matchedApps = listOf(
                MatchedItem(
                    header = MatchedHeaderData(
                        title = sampleAppList.first().label,
                        uniqueId = sampleAppList.first().packageName,
                        icon = pm.getPackageInfoCompat(sampleAppList.first().packageName, 0),
                    ),
                    componentList = listOf(sampleComponentList.first()),
                ),
            )
            assertEquals(
                Loading,
                awaitItem(),
            )
            assertEquals(
                RuleInfoUiState.Success(
                    ruleInfo = sampleRuleList.first(),
                    matchedAppsUiState = Result.Loading,
                ),
                awaitItem(),
            )
            awaitItem()
            awaitItem()
            assertEquals(
                RuleInfoUiState.Success(
                    ruleInfo = sampleRuleList.first(),
                    matchedAppsUiState = Result.Success(matchedApps),
                ),
                awaitItem(),
            )
        }
    }

    @Test
    fun tabState_whenSwitchTab_thenUpdateSelectedItem() = runTest {
        val collectJob = launch(UnconfinedTestDispatcher()) { viewModel.tabState.collect() }
        viewModel.switchTab(RuleDetailTabs.Description)
        assertEquals(RuleDetailTabs.Description, viewModel.tabState.value.selectedItem)
        collectJob.cancel()
    }

    @Test
    fun appBarUiState_whenSwitchTab_thenUpdateAppBarActions() = runTest {
        val collectJob = launch(UnconfinedTestDispatcher()) { viewModel.tabState.collect() }
        viewModel.switchTab(RuleDetailTabs.Description)
        assertEquals(listOf(), viewModel.appBarUiState.value.actions)
        collectJob.cancel()
    }

    @Test
    fun sortType_whenInitial_thenDefaultToName() = runTest {
        assertEquals(RuleDetailSortType.NAME, viewModel.sortType.value)
    }

    @Test
    fun sortType_whenUpdated_thenReflectNewValue() = runTest {
        val collectJob = launch(UnconfinedTestDispatcher()) { viewModel.sortType.collect() }
        viewModel.updateSortType(RuleDetailSortType.MOST_MATCHED)
        assertEquals(RuleDetailSortType.MOST_MATCHED, viewModel.sortType.value)
        collectJob.cancel()
    }
}

private val sampleRuleList = listOf(
    GeneralRule(
        id = 1,
        name = "Rule1",
        company = "Rule1 company",
        description = "Rule1 description",
        sideEffect = "Unknown",
        safeToBlock = true,
        contributors = listOf("Online contributor"),
        searchKeyword = listOf(
            "com.merxury.blocker.test.activity1",
        ),
        matchedAppCount = 2,
    ),
    GeneralRule(
        id = 2,
        name = "Rule2",
        company = "Rule2 company",
        description = "Rule2 description",
        sideEffect = "Unknown",
        safeToBlock = false,
        contributors = listOf("Google"),
        searchKeyword = listOf(
            "androidx.google.example1",
            "androidx.google.example2",
            "androidx.google.example3",
            "androidx.google.example4",
            "test.service",
        ),
        matchedAppCount = 13,
    ),
    GeneralRule(
        id = 3,
        name = "Rule3",
        company = "Rule3 company",
        description = "Rule3 description",
        sideEffect = "Unknown",
        safeToBlock = false,
        contributors = listOf("Tester"),
        searchKeyword = listOf(
            "com.ss.android.socialbase.",
            "com.ss.android.downloadlib.",
            "com.example.component.",
            "com.example.component.",
            "com.example.tea.component.",
            "com.example.sdk.component.",
        ),
    ),
)

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
        description = "An example activity",
    ),
    ComponentInfo(
        simpleName = "Service1",
        name = "com.merxury.blocker.test.service1",
        packageName = "com.merxury.test1",
        type = SERVICE,
        description = "An example service",
        pmBlocked = true,
    ),
    ComponentInfo(
        simpleName = "Service2",
        name = "com.merxury.blocker.test.service2",
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
        name = "com.merxury.blocker.test.provider1",
        packageName = "com.merxury.test1",
        type = PROVIDER,
        description = "An example provider",
    ),
)
