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

package com.merxury.blocker.feature.sharefilter

import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.pm.PackageManager.PackageInfoFlags
import app.cash.turbine.test
import com.merxury.blocker.core.data.test.repository.FakeShareTargetRepository
import com.merxury.blocker.core.data.util.PermissionStatus
import com.merxury.blocker.core.database.sharetarget.ShareTargetActivityEntity
import com.merxury.blocker.core.model.data.ControllerType
import com.merxury.blocker.core.model.data.IntentFilterDataInfo
import com.merxury.blocker.core.model.data.IntentFilterInfo
import com.merxury.blocker.core.result.Result
import com.merxury.blocker.core.testing.repository.TestComponentRepository
import com.merxury.blocker.core.testing.repository.TestUserDataRepository
import com.merxury.blocker.core.testing.repository.defaultUserData
import com.merxury.blocker.core.testing.util.MainDispatcherRule
import com.merxury.blocker.core.testing.util.TestAnalyticsHelper
import com.merxury.blocker.core.testing.util.TestPermissionMonitor
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import kotlin.test.assertEquals
import kotlin.test.assertIs

class ShareFilterViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val analyticsHelper = TestAnalyticsHelper()
    private val userDataRepository = TestUserDataRepository()
    private val componentRepository = TestComponentRepository()
    private val permissionMonitor = TestPermissionMonitor()
    private val dispatcher: CoroutineDispatcher = mainDispatcherRule.testDispatcher
    private val shareTargetsFlow = MutableStateFlow<List<ShareTargetActivityEntity>>(emptyList())
    private val shareTargetRepository = object : FakeShareTargetRepository() {
        override fun getShareTargetActivities() = shareTargetsFlow
    }
    private val appInfo = mock<ApplicationInfo> {
        on { loadLabel(any()) } doReturn "Test App"
    }
    private val packageInfo = mock<PackageInfo> {
        on { toString() } doReturn "MockedPackageInfo"
        on { applicationInfo } doReturn appInfo
    }
    private val pm = mock<PackageManager> {
        on { getPackageInfo(any<String>(), any<Int>()) } doReturn packageInfo
        on { getPackageInfo(any<String>(), any<PackageInfoFlags>()) } doReturn packageInfo
    }

    private lateinit var viewModel: ShareFilterViewModel

    @Before
    fun setup() {
        viewModel = ShareFilterViewModel(
            shareTargetRepository = shareTargetRepository,
            componentRepository = componentRepository,
            userDataRepository = userDataRepository,
            permissionMonitor = permissionMonitor,
            analyticsHelper = analyticsHelper,
            pm = pm,
            ioDispatcher = dispatcher,
        )
    }

    @Test
    fun givenInitialState_whenObserveShareTargetsUiState_thenShowLoading() {
        assertIs<Result.Loading>(viewModel.shareTargetsUiState.value)
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
        viewModel.hasRootPermission.test {
            assertEquals(false, awaitItem())
            permissionMonitor.setPermission(PermissionStatus.ROOT_USER)
            assertEquals(true, awaitItem())
        }
    }

    @Test
    fun givenSearchQuery_whenUpdateSearchQuery_thenReflectNewValue() {
        viewModel.updateSearchQuery("test")
        assertEquals("test", viewModel.searchQuery.value)
    }

    @Test
    fun givenShareTargetData_whenReceiveData_thenShowSuccess() = runTest {
        viewModel.shareTargetsUiState.test {
            userDataRepository.sendUserData(defaultUserData)
            shareTargetsFlow.emit(sampleShareTargets)

            assertEquals(Result.Loading, awaitItem())
            val result = awaitItem()
            assertIs<Result.Success<List<MatchedShareTarget>>>(result)
            assertEquals(1, result.data.size)
            assertEquals("Test App", result.data[0].header.title)
            assertEquals(2, result.data[0].shareTargets.size)
        }
    }

    @Test
    fun givenShareTargetData_whenSearchByAppLabel_thenFilterByAppLabel() = runTest {
        viewModel.shareTargetsUiState.test {
            userDataRepository.sendUserData(defaultUserData)
            shareTargetsFlow.emit(sampleShareTargets)

            assertEquals(Result.Loading, awaitItem())
            assertIs<Result.Success<List<MatchedShareTarget>>>(awaitItem())

            viewModel.updateSearchQuery("Test")
            val result = awaitItem()
            assertIs<Result.Success<List<MatchedShareTarget>>>(result)
            assertEquals(1, result.data.size)
        }
    }

    @Test
    fun givenShareTargetData_whenSearchByComponentName_thenFilterByComponentName() = runTest {
        viewModel.shareTargetsUiState.test {
            userDataRepository.sendUserData(defaultUserData)
            shareTargetsFlow.emit(sampleShareTargets)

            assertEquals(Result.Loading, awaitItem())
            assertIs<Result.Success<List<MatchedShareTarget>>>(awaitItem())

            viewModel.updateSearchQuery("ShareActivity")
            val result = awaitItem()
            assertIs<Result.Success<List<MatchedShareTarget>>>(result)
            assertEquals(1, result.data.size)
            assertEquals(1, result.data[0].shareTargets.size)
            assertEquals("ShareActivity", result.data[0].shareTargets[0].entity.simpleName)
        }
    }

    @Test
    fun givenShareTargetData_whenSearchWithNoMatch_thenShowEmptyList() = runTest {
        viewModel.shareTargetsUiState.test {
            userDataRepository.sendUserData(defaultUserData)
            shareTargetsFlow.emit(sampleShareTargets)

            assertEquals(Result.Loading, awaitItem())
            assertIs<Result.Success<List<MatchedShareTarget>>>(awaitItem())

            viewModel.updateSearchQuery("NonExistentApp")
            val result = awaitItem()
            assertIs<Result.Success<List<MatchedShareTarget>>>(result)
            assertEquals(0, result.data.size)
        }
    }

    @Test
    fun givenIFWController_whenDisableComponent_thenUpdateIfwBlocked() = runTest {
        viewModel.shareTargetsUiState.test {
            userDataRepository.sendUserData(defaultUserData.copy(controllerType = ControllerType.IFW))
            shareTargetsFlow.emit(sampleShareTargets)
            componentRepository.setControlComponentResult(true)

            assertEquals(Result.Loading, awaitItem())
            val initialResult = awaitItem()
            assertIs<Result.Success<List<MatchedShareTarget>>>(initialResult)
            assertEquals(false, initialResult.data[0].shareTargets[0].entity.ifwBlocked)

            viewModel.controlComponent(sampleShareTargets[0], enabled = false)

            val updatedResult = awaitItem()
            assertIs<Result.Success<List<MatchedShareTarget>>>(updatedResult)
            assertEquals(true, updatedResult.data[0].shareTargets[0].entity.ifwBlocked)
        }
    }

    @Test
    fun givenIFWControllerWithBlockedComponent_whenEnableComponent_thenUpdateIfwBlocked() = runTest {
        viewModel.shareTargetsUiState.test {
            val blockedTargets = sampleShareTargets.map { it.copy(ifwBlocked = true) }
            userDataRepository.sendUserData(defaultUserData.copy(controllerType = ControllerType.IFW))
            shareTargetsFlow.emit(blockedTargets)
            componentRepository.setControlComponentResult(true)

            assertEquals(Result.Loading, awaitItem())
            val initialResult = awaitItem()
            assertIs<Result.Success<List<MatchedShareTarget>>>(initialResult)
            assertEquals(true, initialResult.data[0].shareTargets[0].entity.ifwBlocked)

            viewModel.controlComponent(blockedTargets[0], enabled = true)

            val updatedResult = awaitItem()
            assertIs<Result.Success<List<MatchedShareTarget>>>(updatedResult)
            assertEquals(false, updatedResult.data[0].shareTargets[0].entity.ifwBlocked)
        }
    }

    @Test
    fun givenPMController_whenDisableComponent_thenUpdatePmBlocked() = runTest {
        viewModel.shareTargetsUiState.test {
            userDataRepository.sendUserData(defaultUserData.copy(controllerType = ControllerType.PM))
            shareTargetsFlow.emit(sampleShareTargets)
            componentRepository.setControlComponentResult(true)

            assertEquals(Result.Loading, awaitItem())
            val initialResult = awaitItem()
            assertIs<Result.Success<List<MatchedShareTarget>>>(initialResult)
            assertEquals(false, initialResult.data[0].shareTargets[0].entity.pmBlocked)

            viewModel.controlComponent(sampleShareTargets[0], enabled = false)

            val updatedResult = awaitItem()
            assertIs<Result.Success<List<MatchedShareTarget>>>(updatedResult)
            assertEquals(true, updatedResult.data[0].shareTargets[0].entity.pmBlocked)
        }
    }

    @Test
    fun givenPMControllerWithBlockedComponent_whenEnableComponent_thenUpdatePmBlocked() = runTest {
        viewModel.shareTargetsUiState.test {
            val blockedTargets = sampleShareTargets.map { it.copy(pmBlocked = true) }
            userDataRepository.sendUserData(defaultUserData.copy(controllerType = ControllerType.PM))
            shareTargetsFlow.emit(blockedTargets)
            componentRepository.setControlComponentResult(true)

            assertEquals(Result.Loading, awaitItem())
            val initialResult = awaitItem()
            assertIs<Result.Success<List<MatchedShareTarget>>>(initialResult)
            assertEquals(true, initialResult.data[0].shareTargets[0].entity.pmBlocked)

            viewModel.controlComponent(blockedTargets[0], enabled = true)

            val updatedResult = awaitItem()
            assertIs<Result.Success<List<MatchedShareTarget>>>(updatedResult)
            assertEquals(false, updatedResult.data[0].shareTargets[0].entity.pmBlocked)
        }
    }

    @Test
    fun givenIFWController_whenBatchDisableComponents_thenUpdateAll() = runTest {
        viewModel.shareTargetsUiState.test {
            userDataRepository.sendUserData(defaultUserData.copy(controllerType = ControllerType.IFW))
            shareTargetsFlow.emit(sampleShareTargets)
            componentRepository.setControlComponentResult(true)

            assertEquals(Result.Loading, awaitItem())
            val initialResult = awaitItem()
            assertIs<Result.Success<List<MatchedShareTarget>>>(initialResult)
            assertEquals(false, initialResult.data[0].shareTargets[0].entity.ifwBlocked)
            assertEquals(false, initialResult.data[0].shareTargets[1].entity.ifwBlocked)

            viewModel.controlAllComponents(sampleShareTargets, enable = false)

            val updatedResult = awaitItem()
            assertIs<Result.Success<List<MatchedShareTarget>>>(updatedResult)
            assertEquals(true, updatedResult.data[0].shareTargets[0].entity.ifwBlocked)
            assertEquals(true, updatedResult.data[0].shareTargets[1].entity.ifwBlocked)
        }
    }

    @Test
    fun givenIFWControllerWithBlockedComponents_whenBatchEnableComponents_thenUpdateAll() = runTest {
        viewModel.shareTargetsUiState.test {
            val blockedTargets = sampleShareTargets.map { it.copy(ifwBlocked = true) }
            userDataRepository.sendUserData(defaultUserData.copy(controllerType = ControllerType.IFW))
            shareTargetsFlow.emit(blockedTargets)
            componentRepository.setControlComponentResult(true)

            assertEquals(Result.Loading, awaitItem())
            val initialResult = awaitItem()
            assertIs<Result.Success<List<MatchedShareTarget>>>(initialResult)
            assertEquals(true, initialResult.data[0].shareTargets[0].entity.ifwBlocked)
            assertEquals(true, initialResult.data[0].shareTargets[1].entity.ifwBlocked)

            viewModel.controlAllComponents(blockedTargets, enable = true)

            val updatedResult = awaitItem()
            assertIs<Result.Success<List<MatchedShareTarget>>>(updatedResult)
            assertEquals(false, updatedResult.data[0].shareTargets[0].entity.ifwBlocked)
            assertEquals(false, updatedResult.data[0].shareTargets[1].entity.ifwBlocked)
        }
    }

    @Test
    fun givenErrorState_whenDismissError_thenNull() = runTest {
        viewModel.shareTargetsUiState.test {
            userDataRepository.sendUserData(defaultUserData)
            shareTargetsFlow.emit(sampleShareTargets)
            componentRepository.setControlComponentResult(false)

            skipItems(2)

            viewModel.controlComponent(sampleShareTargets[0], enabled = false)
            skipItems(1)

            assertEquals(null, viewModel.errorState.value)

            viewModel.dismissError()
            assertEquals(null, viewModel.errorState.value)
        }
    }
}

private val sampleShareTargets = listOf(
    ShareTargetActivityEntity(
        packageName = "com.example.app",
        componentName = "com.example.app.ShareActivity",
        simpleName = "ShareActivity",
        displayName = "Share",
        ifwBlocked = false,
        pmBlocked = false,
        exported = true,
        intentFilters = listOf(
            IntentFilterInfo(
                actions = listOf("android.intent.action.SEND"),
                categories = listOf("android.intent.category.DEFAULT"),
                data = listOf(IntentFilterDataInfo(mimeType = "text/plain")),
            ),
        ),
    ),
    ShareTargetActivityEntity(
        packageName = "com.example.app",
        componentName = "com.example.app.MainActivity",
        simpleName = "MainActivity",
        displayName = "Main",
        ifwBlocked = false,
        pmBlocked = false,
        exported = true,
        intentFilters = listOf(
            IntentFilterInfo(
                actions = listOf("android.intent.action.MAIN"),
                categories = listOf("android.intent.category.LAUNCHER"),
                data = emptyList(),
            ),
        ),
    ),
)
