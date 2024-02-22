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

package com.merxury.blocker.feature.generalrules

import android.content.Context
import app.cash.turbine.test
import com.merxury.blocker.core.domain.InitializeRuleStorageUseCase
import com.merxury.blocker.core.domain.SearchGeneralRuleUseCase
import com.merxury.blocker.core.domain.UpdateRuleMatchedAppUseCase
import com.merxury.blocker.core.model.ComponentType.ACTIVITY
import com.merxury.blocker.core.model.ComponentType.PROVIDER
import com.merxury.blocker.core.model.ComponentType.RECEIVER
import com.merxury.blocker.core.model.ComponentType.SERVICE
import com.merxury.blocker.core.model.data.ComponentInfo
import com.merxury.blocker.core.model.data.GeneralRule
import com.merxury.blocker.core.model.data.InstalledApp
import com.merxury.blocker.core.testing.repository.TestAppPropertiesRepository
import com.merxury.blocker.core.testing.repository.TestAppRepository
import com.merxury.blocker.core.testing.repository.TestComponentRepository
import com.merxury.blocker.core.testing.repository.TestGeneralRuleRepository
import com.merxury.blocker.core.testing.repository.TestUserDataRepository
import com.merxury.blocker.core.testing.repository.defaultUserData
import com.merxury.blocker.core.testing.util.MainDispatcherRule
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.mockito.Mockito.mock
import kotlin.test.assertEquals

class GeneralRuleViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @get:Rule
    val tempFolder: TemporaryFolder = TemporaryFolder.builder()
        .assureDeletion()
        .build()

    private val appRepository = TestAppRepository()
    private val appPropertiesRepository = TestAppPropertiesRepository()
    private val generalRuleRepository = TestGeneralRuleRepository()
    private val userDataRepository = TestUserDataRepository()
    private val componentRepository = TestComponentRepository()
    private val dispatcher: CoroutineDispatcher = mainDispatcherRule.testDispatcher
    private lateinit var viewModel: GeneralRulesViewModel
    private val context = mock<Context>()

    @Before
    fun setup() {
        val initGeneralRuleUseCase = InitializeRuleStorageUseCase(
            filesDir = tempFolder.newFolder(),
            ruleBaseFolder = tempFolder.newFolder().absolutePath,
            ioDispatcher = dispatcher,
            appContext = context,
        )
        val searchRule = SearchGeneralRuleUseCase(
            generalRuleRepository = generalRuleRepository,
            userDataRepository = userDataRepository,
            filesDir = tempFolder.newFolder(),
            ruleBaseFolder = tempFolder.newFolder().absolutePath,
        )
        val updateRule = UpdateRuleMatchedAppUseCase(
            generalRuleRepository = generalRuleRepository,
            appRepository = appRepository,
            userDataRepository = userDataRepository,
            componentRepository = componentRepository,
            ioDispatcher = dispatcher,
        )

        viewModel = GeneralRulesViewModel(
            appRepository = appRepository,
            appPropertiesRepository = appPropertiesRepository,
            generalRuleRepository = generalRuleRepository,
            initGeneralRuleUseCase = initGeneralRuleUseCase,
            searchRule = searchRule,
            updateRule = updateRule,
            ioDispatcher = dispatcher,
        )
    }

    @Test
    fun uiState_whenInitial_thenShowLoading() = runTest {
        assertEquals(GeneralRuleUiState.Loading, viewModel.uiState.value)
    }

    @Test
    fun uiState_whenSuccess_thenShowData() = runTest {
        viewModel.uiState.test {
            appRepository.sendAppList(sampleAppList)
            userDataRepository.sendUserData(defaultUserData)
            componentRepository.sendComponentList(sampleComponentList)
            generalRuleRepository.sendRuleList(sampleRuleList)
            assertEquals(GeneralRuleUiState.Loading, awaitItem())
            assertEquals(GeneralRuleUiState.Success(sampleRuleList), awaitItem())
        }
    }
}

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
            "androidx.google.example2",
            "androidx.google.example3",
            "androidx.google.example4",
        ),
    ),
    GeneralRule(
        id = 3,
        name = "Pangolin Advertising SDK",
        iconUrl = "icon/chuanshanjia.svg",
        company = "Beijing Juliang Engine Network Technology Co., Ltd.",
        description = "Pangolin is a global developer growth platform, providing global developers with full life cycle services and growth solutions such as user growth, traffic monetization, and LTV improvement.",
        sideEffect = "Unknown",
        safeToBlock = false,
        contributors = listOf("Tester"),
        searchKeyword = listOf(
            "com.ss.android.socialbase.",
            "com.ss.android.downloadlib.",
            "com.bytedance.embedapplog.",
            "com.bytedance.pangle.",
            "com.bytedance.tea.crash.",
            "com.bytedance.sdk.openadsdk.",
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
