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

import android.app.Application
import android.content.pm.PackageManager
import androidx.lifecycle.SavedStateHandle
import com.merxury.blocker.core.controllers.shizuku.ShizukuInitializer
import com.merxury.blocker.core.model.data.InstalledApp
import com.merxury.blocker.core.testing.repository.TestAppRepository
import com.merxury.blocker.core.testing.repository.TestComponentDetailRepository
import com.merxury.blocker.core.testing.repository.TestComponentRepository
import com.merxury.blocker.core.testing.repository.TestUserDataRepository
import com.merxury.blocker.core.testing.util.MainDispatcherRule
import com.merxury.blocker.core.testing.util.TestAnalyticsHelper
import com.merxury.blocker.core.ui.applist.model.toAppItem
import com.merxury.blocker.core.ui.bottomsheet.ComponentSortInfoUiState
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
    private val savedStateHandle = SavedStateHandle()
    private val appContext = Application()
    private val pm: PackageManager = appContext.packageManager
    private val userDataRepository = TestUserDataRepository()
    private val appRepository = TestAppRepository()
    private val componentRepository = TestComponentRepository()
    private val componentDetailRepository = TestComponentDetailRepository()
    private val shizukuInitializer: ShizukuInitializer = ShizukuInitializer(appContext)
    private val ioDispatcher: CoroutineDispatcher = mainDispatcherRule.testDispatcher
    private val cpuDispatcher: CoroutineDispatcher = mainDispatcherRule.testDispatcher
    private lateinit var viewModel: AppDetailViewModel

    @Before
    fun setup() {
        viewModel = AppDetailViewModel(
            appContext = appContext,
            savedStateHandle = savedStateHandle,
            pm = pm,
            userDataRepository = userDataRepository,
            appRepository = appRepository,
            componentRepository = componentRepository,
            componentDetailRepository = componentDetailRepository,
            shizukuInitializer = shizukuInitializer,
            analyticsHelper = analyticsHelper,
            ioDispatcher = ioDispatcher,
            cpuDispatcher = cpuDispatcher,
        )
    }

    @Test
    fun stateIsInitiallyLoading() = runTest {
        assertEquals(
            AppInfoUiState.Loading,
            viewModel.appInfoUiState.value,
        )
        assertEquals(ComponentSortInfoUiState.Loading, viewModel.componentSortInfoUiState.value)
    }

    @Test
    fun stateIsLoadingWhenDataAreLoading() = runTest {
        val collectJob1 =
            launch(UnconfinedTestDispatcher()) { viewModel.appInfoUiState.collect() }
        val collectJob2 = launch(UnconfinedTestDispatcher()) { viewModel.componentSortInfoUiState.collect() }

        assertEquals(
            AppInfoUiState.Loading,
            viewModel.appInfoUiState.value,
        )
        assertEquals(ComponentSortInfoUiState.Loading, viewModel.componentSortInfoUiState.value)

        collectJob1.cancel()
        collectJob2.cancel()
    }

    @Test
    fun showAppInfoWhenGetAppInfo() = runTest {
        val collectJob1 =
            launch(UnconfinedTestDispatcher()) { viewModel.appInfoUiState.collect() }
        val collectJob2 = launch(UnconfinedTestDispatcher()) { viewModel.componentSortInfoUiState.collect() }

        appRepository.sendAppList(sampleAppList)
        viewModel.loadAppInfo()
        assertEquals(
            AppInfoUiState.Success(
                appInfo = sampleAppList.first().toAppItem(),
                appIcon = null,
            ),
            viewModel.appInfoUiState.value,
        )
        assertEquals(ComponentSortInfoUiState.Loading, viewModel.componentSortInfoUiState.value)

        collectJob1.cancel()
        collectJob2.cancel()
    }
}

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
