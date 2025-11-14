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

package com.merxury.blocker.feature.debloater

import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.pm.PackageManager.PackageInfoFlags
import app.cash.turbine.test
import com.merxury.blocker.core.analytics.StubAnalyticsHelper
import com.merxury.blocker.core.data.respository.debloater.DebloatableComponentRepository
import com.merxury.blocker.core.data.util.PermissionStatus
import com.merxury.blocker.core.database.debloater.DebloatableComponentEntity
import com.merxury.blocker.core.model.ComponentType
import com.merxury.blocker.core.model.data.ControllerType
import com.merxury.blocker.core.model.data.IntentFilterDataInfo
import com.merxury.blocker.core.model.data.IntentFilterInfo
import com.merxury.blocker.core.result.Result
import com.merxury.blocker.core.testing.repository.TestComponentRepository
import com.merxury.blocker.core.testing.repository.TestUserDataRepository
import com.merxury.blocker.core.testing.repository.defaultUserData
import com.merxury.blocker.core.testing.util.MainDispatcherRule
import com.merxury.blocker.core.testing.util.TestPermissionMonitor
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import kotlin.test.assertEquals
import kotlin.test.assertIs

class DebloaterViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val userDataRepository = TestUserDataRepository()
    private val componentRepository = TestComponentRepository()
    private val permissionMonitor = TestPermissionMonitor()
    private val dispatcher: CoroutineDispatcher = mainDispatcherRule.testDispatcher
    private val analyticsHelper = StubAnalyticsHelper()
    private val debloatableFlow = MutableStateFlow<List<DebloatableComponentEntity>>(emptyList())
    private val debloatableComponentRepository = object : DebloatableComponentRepository {
        override fun getDebloatableComponent() = debloatableFlow
        override fun updateDebloatableComponent() = flowOf(Result.Success(Unit))
    }
    private val appInfo = mock<ApplicationInfo> {
        on { loadLabel(any()) } doReturn "Test App"
    }
    private val packageInfo = mock<PackageInfo>().apply {
        applicationInfo = appInfo
    }
    private val pm = mock<PackageManager> {
        on { getPackageInfo(any<String>(), any<Int>()) } doReturn packageInfo
        on { getPackageInfo(any<String>(), any<PackageInfoFlags>()) } doReturn packageInfo
    }

    private lateinit var viewModel: DebloaterViewModel

    @Before
    fun setup() {
        debloatableFlow.value = sampleDebloatableComponents
        viewModel = DebloaterViewModel(
            debloatableComponentRepository = debloatableComponentRepository,
            componentRepository = componentRepository,
            userDataRepository = userDataRepository,
            permissionMonitor = permissionMonitor,
            pm = pm,
            analyticsHelper = analyticsHelper,
            ioDispatcher = dispatcher,
        )
    }

    @Test
    fun givenInitialState_whenObserveDebloatableUiState_thenShowLoading() = runTest {
        viewModel.debloatableUiState.test {
            val initial = awaitItem()
            assertIs<Result.Success<List<MatchedTarget>>>(initial)
            assertEquals(1, initial.data.size)
        }
    }

    @Test
    fun givenInitialState_whenObserveSearchQuery_thenEmpty() {
        assertEquals("", viewModel.searchQuery.value)
    }

    @Test
    fun givenInitialState_whenObserveErrorState_thenNull() {
        assertEquals(null, viewModel.errorState.value)
    }

    @Test
    fun givenInitialState_whenObserveHasRootPermission_thenFalse() {
        assertEquals(false, viewModel.hasRootPermission.value)
    }

    @Test
    fun givenPermissionGranted_whenObserveHasRootPermission_thenTrue() = runTest {
        permissionMonitor.setPermission(PermissionStatus.ROOT_USER)
        val testViewModel = DebloaterViewModel(
            debloatableComponentRepository = debloatableComponentRepository,
            componentRepository = componentRepository,
            userDataRepository = userDataRepository,
            permissionMonitor = permissionMonitor,
            pm = pm,
            analyticsHelper = analyticsHelper,
            ioDispatcher = dispatcher,
        )
        testViewModel.hasRootPermission.test {
            assertEquals(true, awaitItem())
        }
    }

    @Test
    fun givenSearchQuery_whenUpdateSearchQuery_thenReflectNewValue() {
        viewModel.updateSearchQuery("test")
        assertEquals("test", viewModel.searchQuery.value)
    }

    @Test
    fun givenDebloatableComponentData_whenReceiveData_thenShowSuccess() = runTest {
        viewModel.debloatableUiState.test {
            userDataRepository.sendUserData(defaultUserData)

            val result = awaitItem()
            assertIs<Result.Success<List<MatchedTarget>>>(result)
            assertEquals(1, result.data.size)
            assertEquals("Test App", result.data[0].header.title)
            assertEquals(2, result.data[0].targets.size)
        }
    }

    @Test
    fun givenDebloatableComponentData_whenSearchByAppLabel_thenFilterByAppLabel() = runTest {
        userDataRepository.sendUserData(defaultUserData)
        testScheduler.advanceUntilIdle()

        viewModel.updateSearchQuery("Test")
        testScheduler.advanceUntilIdle()

        val result = viewModel.debloatableUiState.value
        assertIs<Result.Success<List<MatchedTarget>>>(result)
        assertEquals(1, result.data.size)
        assertEquals("Test App", result.data[0].header.title)
    }

    @Test
    fun givenDebloatableComponentData_whenSearchByComponentName_thenFilterByComponentName() = runTest {
        viewModel.debloatableUiState.test {
            userDataRepository.sendUserData(defaultUserData)

            awaitItem()

            viewModel.updateSearchQuery("ShareActivity")
            val result = awaitItem()
            assertIs<Result.Success<List<MatchedTarget>>>(result)
            assertEquals(1, result.data.size)
            assertEquals(1, result.data[0].targets.size)
            assertEquals("ShareActivity", result.data[0].targets[0].entity.simpleName)
        }
    }

    @Test
    fun givenDebloatableComponentData_whenSearchWithNoMatch_thenShowEmptyList() = runTest {
        viewModel.debloatableUiState.test {
            userDataRepository.sendUserData(defaultUserData)

            awaitItem()

            viewModel.updateSearchQuery("NonExistentApp")
            val result = awaitItem()
            assertIs<Result.Success<List<MatchedTarget>>>(result)
            assertEquals(0, result.data.size)
        }
    }

    @Test
    fun givenIFWController_whenDisableComponent_thenUpdateIfwBlocked() = runTest {
        viewModel.debloatableUiState.test {
            userDataRepository.sendUserData(defaultUserData.copy(controllerType = ControllerType.IFW))

            val initialResult = awaitItem()
            assertIs<Result.Success<List<MatchedTarget>>>(initialResult)
            assertEquals(false, initialResult.data[0].targets[0].entity.ifwBlocked)

            viewModel.controlComponent(sampleDebloatableComponents[0], enabled = false)

            val updatedResult = awaitItem()
            assertIs<Result.Success<List<MatchedTarget>>>(updatedResult)
            assertEquals(true, updatedResult.data[0].targets[0].entity.ifwBlocked)
        }
    }

    @Test
    fun givenIFWControllerWithBlockedComponent_whenEnableComponent_thenUpdateIfwBlocked() = runTest {
        val blockedTargets = sampleDebloatableComponents.map { it.copy(ifwBlocked = true) }
        debloatableFlow.value = blockedTargets
        val testViewModel = DebloaterViewModel(
            debloatableComponentRepository = debloatableComponentRepository,
            componentRepository = componentRepository,
            userDataRepository = userDataRepository,
            permissionMonitor = permissionMonitor,
            pm = pm,
            analyticsHelper = analyticsHelper,
            ioDispatcher = dispatcher,
        )

        testViewModel.debloatableUiState.test {
            userDataRepository.sendUserData(defaultUserData.copy(controllerType = ControllerType.IFW))

            val initialResult = awaitItem()
            assertIs<Result.Success<List<MatchedTarget>>>(initialResult)
            assertEquals(true, initialResult.data[0].targets[0].entity.ifwBlocked)

            testViewModel.controlComponent(blockedTargets[0], enabled = true)

            val updatedResult = awaitItem()
            assertIs<Result.Success<List<MatchedTarget>>>(updatedResult)
            assertEquals(false, updatedResult.data[0].targets[0].entity.ifwBlocked)
        }
    }

    @Test
    fun givenPMController_whenDisableComponent_thenUpdatePmBlocked() = runTest {
        viewModel.debloatableUiState.test {
            userDataRepository.sendUserData(defaultUserData.copy(controllerType = ControllerType.PM))

            val initialResult = awaitItem()
            assertIs<Result.Success<List<MatchedTarget>>>(initialResult)
            assertEquals(false, initialResult.data[0].targets[0].entity.pmBlocked)

            viewModel.controlComponent(sampleDebloatableComponents[0], enabled = false)

            val updatedResult = awaitItem()
            assertIs<Result.Success<List<MatchedTarget>>>(updatedResult)
            assertEquals(true, updatedResult.data[0].targets[0].entity.pmBlocked)
        }
    }

    @Test
    fun givenPMControllerWithBlockedComponent_whenEnableComponent_thenUpdatePmBlocked() = runTest {
        val blockedTargets = sampleDebloatableComponents.map { it.copy(pmBlocked = true) }
        debloatableFlow.value = blockedTargets
        val testViewModel = DebloaterViewModel(
            debloatableComponentRepository = debloatableComponentRepository,
            componentRepository = componentRepository,
            userDataRepository = userDataRepository,
            permissionMonitor = permissionMonitor,
            pm = pm,
            analyticsHelper = analyticsHelper,
            ioDispatcher = dispatcher,
        )

        testViewModel.debloatableUiState.test {
            userDataRepository.sendUserData(defaultUserData.copy(controllerType = ControllerType.PM))

            val initialResult = awaitItem()
            assertIs<Result.Success<List<MatchedTarget>>>(initialResult)
            assertEquals(true, initialResult.data[0].targets[0].entity.pmBlocked)

            testViewModel.controlComponent(blockedTargets[0], enabled = true)

            val updatedResult = awaitItem()
            assertIs<Result.Success<List<MatchedTarget>>>(updatedResult)
            assertEquals(false, updatedResult.data[0].targets[0].entity.pmBlocked)
        }
    }

    @Test
    fun givenIFWController_whenBatchDisableComponents_thenUpdateAll() = runTest {
        viewModel.debloatableUiState.test {
            userDataRepository.sendUserData(defaultUserData.copy(controllerType = ControllerType.IFW))

            val initialResult = awaitItem()
            assertIs<Result.Success<List<MatchedTarget>>>(initialResult)
            assertEquals(false, initialResult.data[0].targets[0].entity.ifwBlocked)
            assertEquals(false, initialResult.data[0].targets[1].entity.ifwBlocked)

            viewModel.controlAllComponents(sampleDebloatableComponents, enable = false)

            val updatedResult = awaitItem()
            assertIs<Result.Success<List<MatchedTarget>>>(updatedResult)
            assertEquals(true, updatedResult.data[0].targets[0].entity.ifwBlocked)
            assertEquals(true, updatedResult.data[0].targets[1].entity.ifwBlocked)
        }
    }

    @Test
    fun givenIFWControllerWithBlockedComponents_whenBatchEnableComponents_thenUpdateAll() = runTest {
        val blockedTargets = sampleDebloatableComponents.map { it.copy(ifwBlocked = true) }
        debloatableFlow.value = blockedTargets
        val testViewModel = DebloaterViewModel(
            debloatableComponentRepository = debloatableComponentRepository,
            componentRepository = componentRepository,
            userDataRepository = userDataRepository,
            permissionMonitor = permissionMonitor,
            pm = pm,
            analyticsHelper = analyticsHelper,
            ioDispatcher = dispatcher,
        )

        testViewModel.debloatableUiState.test {
            userDataRepository.sendUserData(defaultUserData.copy(controllerType = ControllerType.IFW))

            val initialResult = awaitItem()
            assertIs<Result.Success<List<MatchedTarget>>>(initialResult)
            assertEquals(true, initialResult.data[0].targets[0].entity.ifwBlocked)
            assertEquals(true, initialResult.data[0].targets[1].entity.ifwBlocked)

            testViewModel.controlAllComponents(blockedTargets, enable = true)

            val updatedResult = awaitItem()
            assertIs<Result.Success<List<MatchedTarget>>>(updatedResult)
            assertEquals(false, updatedResult.data[0].targets[0].entity.ifwBlocked)
            assertEquals(false, updatedResult.data[0].targets[1].entity.ifwBlocked)
        }
    }

    @Test
    fun givenErrorState_whenDismissError_thenNull() = runTest {
        assertEquals(null, viewModel.errorState.value)
        viewModel.dismissError()
        assertEquals(null, viewModel.errorState.value)
    }

    @Test
    fun givenInitialState_whenObserveComponentTypeFilter_thenEmpty() {
        assertEquals(
            emptySet(),
            viewModel.componentTypeFilter.value,
        )
    }

    @Test
    fun givenComponentTypeFilter_whenUpdateComponentTypeFilter_thenReflectNewValue() {
        val types = setOf(ComponentClassification.SHAREABLE, ComponentClassification.LAUNCHER)
        viewModel.updateComponentTypeFilter(types)
        assertEquals(types, viewModel.componentTypeFilter.value)
    }

    @Test
    fun givenDebloatableComponentData_whenFilterBySharableType_thenShowOnlyShareableComponents() = runTest {
        debloatableFlow.value = sampleComponentsWithVariousTypes
        val testViewModel = DebloaterViewModel(
            debloatableComponentRepository = debloatableComponentRepository,
            componentRepository = componentRepository,
            userDataRepository = userDataRepository,
            permissionMonitor = permissionMonitor,
            pm = pm,
            analyticsHelper = analyticsHelper,
            ioDispatcher = dispatcher,
        )

        testViewModel.debloatableUiState.test {
            userDataRepository.sendUserData(defaultUserData)

            awaitItem()

            testViewModel.updateComponentTypeFilter(setOf(ComponentClassification.SHAREABLE))
            val result = awaitItem()
            assertIs<Result.Success<List<MatchedTarget>>>(result)
            assertEquals(1, result.data.size)
            assertEquals(1, result.data[0].targets.size)
            assertEquals("ShareActivity", result.data[0].targets[0].entity.simpleName)
        }
    }

    @Test
    fun givenDebloatableComponentData_whenFilterByLauncherType_thenShowOnlyLauncherComponents() = runTest {
        debloatableFlow.value = sampleComponentsWithVariousTypes
        val testViewModel = DebloaterViewModel(
            debloatableComponentRepository = debloatableComponentRepository,
            componentRepository = componentRepository,
            userDataRepository = userDataRepository,
            permissionMonitor = permissionMonitor,
            pm = pm,
            analyticsHelper = analyticsHelper,
            ioDispatcher = dispatcher,
        )

        testViewModel.debloatableUiState.test {
            userDataRepository.sendUserData(defaultUserData)

            awaitItem()

            testViewModel.updateComponentTypeFilter(setOf(ComponentClassification.LAUNCHER))
            val result = awaitItem()
            assertIs<Result.Success<List<MatchedTarget>>>(result)
            assertEquals(1, result.data.size)
            assertEquals(1, result.data[0].targets.size)
            assertEquals("MainActivity", result.data[0].targets[0].entity.simpleName)
        }
    }

    @Test
    fun givenDebloatableComponentData_whenFilterByDeeplinkType_thenShowOnlyDeeplinkComponents() = runTest {
        debloatableFlow.value = sampleComponentsWithVariousTypes
        val testViewModel = DebloaterViewModel(
            debloatableComponentRepository = debloatableComponentRepository,
            componentRepository = componentRepository,
            userDataRepository = userDataRepository,
            permissionMonitor = permissionMonitor,
            pm = pm,
            analyticsHelper = analyticsHelper,
            ioDispatcher = dispatcher,
        )

        testViewModel.debloatableUiState.test {
            userDataRepository.sendUserData(defaultUserData)

            awaitItem()

            testViewModel.updateComponentTypeFilter(setOf(ComponentClassification.DEEPLINK))
            val result = awaitItem()
            assertIs<Result.Success<List<MatchedTarget>>>(result)
            assertEquals(1, result.data.size)
            assertEquals(1, result.data[0].targets.size)
            assertEquals("DeeplinkActivity", result.data[0].targets[0].entity.simpleName)
        }
    }

    @Test
    fun givenDebloatableComponentData_whenFilterByExplicitType_thenShowOnlyExplicitComponents() = runTest {
        val testViewModel = DebloaterViewModel(
            debloatableComponentRepository = debloatableComponentRepository,
            componentRepository = componentRepository,
            userDataRepository = userDataRepository,
            permissionMonitor = permissionMonitor,
            pm = pm,
            analyticsHelper = analyticsHelper,
            ioDispatcher = dispatcher,
        )

        testViewModel.debloatableUiState.test {
            debloatableFlow.value = sampleComponentsWithVariousTypes
            userDataRepository.sendUserData(defaultUserData)

            val initial = awaitItem()
            assertIs<Result.Success<List<MatchedTarget>>>(initial)

            testViewModel.updateComponentTypeFilter(setOf(ComponentClassification.EXPORTED_NO_PERM))
            val result = awaitItem()
            assertIs<Result.Success<List<MatchedTarget>>>(result)
            assertEquals(1, result.data.size)
            assertEquals(4, result.data[0].targets.size)
        }
    }

    @Test
    fun givenDebloatableComponentData_whenFilterByMultipleTypes_thenShowMatchingComponents() = runTest {
        debloatableFlow.value = sampleComponentsWithVariousTypes
        val testViewModel = DebloaterViewModel(
            debloatableComponentRepository = debloatableComponentRepository,
            componentRepository = componentRepository,
            userDataRepository = userDataRepository,
            permissionMonitor = permissionMonitor,
            pm = pm,
            analyticsHelper = analyticsHelper,
            ioDispatcher = dispatcher,
        )

        testViewModel.debloatableUiState.test {
            userDataRepository.sendUserData(defaultUserData)

            awaitItem()

            testViewModel.updateComponentTypeFilter(
                setOf(ComponentClassification.SHAREABLE, ComponentClassification.LAUNCHER),
            )
            val result = awaitItem()
            assertIs<Result.Success<List<MatchedTarget>>>(result)
            assertEquals(1, result.data.size)
            assertEquals(2, result.data[0].targets.size)
        }
    }

    @Test
    fun givenDebloatableComponentData_whenFilterByTypeAndSearch_thenApplyBothFilters() = runTest {
        val testViewModel = DebloaterViewModel(
            debloatableComponentRepository = debloatableComponentRepository,
            componentRepository = componentRepository,
            userDataRepository = userDataRepository,
            permissionMonitor = permissionMonitor,
            pm = pm,
            analyticsHelper = analyticsHelper,
            ioDispatcher = dispatcher,
        )

        testViewModel.debloatableUiState.test {
            debloatableFlow.value = sampleComponentsWithVariousTypes
            userDataRepository.sendUserData(defaultUserData)

            val initial = awaitItem()
            assertIs<Result.Success<List<MatchedTarget>>>(initial)

            testViewModel.updateSearchQuery("Share")
            awaitItem()

            testViewModel.updateComponentTypeFilter(setOf(ComponentClassification.SHAREABLE))
            val result = awaitItem()
            assertIs<Result.Success<List<MatchedTarget>>>(result)
            assertEquals(1, result.data.size)
            assertEquals(1, result.data[0].targets.size)
            assertEquals("ShareActivity", result.data[0].targets[0].entity.simpleName)
        }
    }

    @Test
    fun givenDebloatableComponentData_whenFilterByTypeAndSearchWithNoMatch_thenShowEmpty() = runTest {
        debloatableFlow.value = sampleComponentsWithVariousTypes
        val testViewModel = DebloaterViewModel(
            debloatableComponentRepository = debloatableComponentRepository,
            componentRepository = componentRepository,
            userDataRepository = userDataRepository,
            permissionMonitor = permissionMonitor,
            pm = pm,
            analyticsHelper = analyticsHelper,
            ioDispatcher = dispatcher,
        )

        testViewModel.debloatableUiState.test {
            userDataRepository.sendUserData(defaultUserData)

            awaitItem()

            testViewModel.updateSearchQuery("Main")
            awaitItem()

            testViewModel.updateComponentTypeFilter(setOf(ComponentClassification.SHAREABLE))
            val result = awaitItem()
            assertIs<Result.Success<List<MatchedTarget>>>(result)
            assertEquals(0, result.data.size)
        }
    }

    @Test
    fun givenDebloatableComponentData_whenFilterByEmptySet_thenShowAllComponents() = runTest {
        debloatableFlow.value = sampleComponentsWithVariousTypes
        val testViewModel = DebloaterViewModel(
            debloatableComponentRepository = debloatableComponentRepository,
            componentRepository = componentRepository,
            userDataRepository = userDataRepository,
            permissionMonitor = permissionMonitor,
            pm = pm,
            analyticsHelper = analyticsHelper,
            ioDispatcher = dispatcher,
        )

        testViewModel.debloatableUiState.test {
            userDataRepository.sendUserData(defaultUserData)

            val initialResult = awaitItem()
            assertIs<Result.Success<List<MatchedTarget>>>(initialResult)
            assertEquals(4, initialResult.data[0].targets.size)

            testViewModel.updateComponentTypeFilter(setOf(ComponentClassification.LAUNCHER))
            val filteredResult = awaitItem()
            assertIs<Result.Success<List<MatchedTarget>>>(filteredResult)
            assertEquals(1, filteredResult.data[0].targets.size)

            testViewModel.updateComponentTypeFilter(emptySet())
            val resetResult = awaitItem()
            assertIs<Result.Success<List<MatchedTarget>>>(resetResult)
            assertEquals(4, resetResult.data[0].targets.size)
        }
    }
}

private val sampleDebloatableComponents = listOf(
    DebloatableComponentEntity(
        packageName = "com.example.app",
        componentName = "com.example.app.ShareActivity",
        simpleName = "ShareActivity",
        displayName = "Share",
        ifwBlocked = false,
        pmBlocked = false,
        exported = true,
        type = ComponentType.ACTIVITY,
        intentFilters = listOf(
            IntentFilterInfo(
                actions = listOf("android.intent.action.SEND"),
                categories = listOf("android.intent.category.DEFAULT"),
                data = listOf(IntentFilterDataInfo(mimeType = "text/plain")),
            ),
        ),
    ),
    DebloatableComponentEntity(
        packageName = "com.example.app",
        componentName = "com.example.app.MainActivity",
        simpleName = "MainActivity",
        displayName = "Main",
        ifwBlocked = false,
        pmBlocked = false,
        exported = true,
        type = ComponentType.ACTIVITY,
        intentFilters = listOf(
            IntentFilterInfo(
                actions = listOf("android.intent.action.MAIN"),
                categories = listOf("android.intent.category.LAUNCHER"),
                data = emptyList(),
            ),
        ),
    ),
)

private val sampleComponentsWithVariousTypes = listOf(
    DebloatableComponentEntity(
        packageName = "com.example.app",
        componentName = "com.example.app.ShareActivity",
        simpleName = "ShareActivity",
        displayName = "Share",
        ifwBlocked = false,
        pmBlocked = false,
        exported = true,
        type = ComponentType.ACTIVITY,
        intentFilters = listOf(
            IntentFilterInfo(
                actions = listOf("android.intent.action.SEND"),
                categories = listOf("android.intent.category.DEFAULT"),
                data = listOf(IntentFilterDataInfo(mimeType = "text/plain")),
            ),
        ),
    ),
    DebloatableComponentEntity(
        packageName = "com.example.app",
        componentName = "com.example.app.MainActivity",
        simpleName = "MainActivity",
        displayName = "Main",
        ifwBlocked = false,
        pmBlocked = false,
        exported = true,
        type = ComponentType.ACTIVITY,
        intentFilters = listOf(
            IntentFilterInfo(
                actions = listOf("android.intent.action.MAIN"),
                categories = listOf("android.intent.category.LAUNCHER"),
                data = emptyList(),
            ),
        ),
    ),
    DebloatableComponentEntity(
        packageName = "com.example.app",
        componentName = "com.example.app.DeeplinkActivity",
        simpleName = "DeeplinkActivity",
        displayName = "Deeplink",
        ifwBlocked = false,
        pmBlocked = false,
        exported = true,
        type = ComponentType.ACTIVITY,
        intentFilters = listOf(
            IntentFilterInfo(
                actions = listOf("android.intent.action.VIEW"),
                categories = listOf(
                    "android.intent.category.DEFAULT",
                    "android.intent.category.BROWSABLE",
                ),
                data = listOf(
                    IntentFilterDataInfo(
                        scheme = "https",
                        host = "example.com",
                    ),
                ),
            ),
        ),
    ),
    DebloatableComponentEntity(
        packageName = "com.example.app",
        componentName = "com.example.app.ExplicitActivity",
        simpleName = "ExplicitActivity",
        displayName = "Explicit",
        ifwBlocked = false,
        pmBlocked = false,
        exported = true,
        type = ComponentType.ACTIVITY,
        intentFilters = emptyList(),
    ),
)
