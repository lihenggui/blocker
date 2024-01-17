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

package com.merxury.blocker.core.domain.applist

import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import app.cash.turbine.test
import com.merxury.blocker.core.domain.controller.GetAppControllerUseCase
import com.merxury.blocker.core.domain.controller.GetServiceControllerUseCase
import com.merxury.blocker.core.model.data.InstalledApp
import com.merxury.blocker.core.testing.controller.FakeAppController
import com.merxury.blocker.core.testing.controller.FakeServiceController
import com.merxury.blocker.core.testing.data.TestAppStateCache
import com.merxury.blocker.core.testing.repository.TestAppRepository
import com.merxury.blocker.core.testing.repository.TestUserDataRepository
import com.merxury.blocker.core.testing.repository.emptyUserData
import com.merxury.blocker.core.testing.util.MainDispatcherRule
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import kotlin.test.Test
import kotlin.test.assertEquals

class SearchAppListUseCaseTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()
    private val pm = mock<PackageManager> {
        on { getPackageInfo(any<String>(), any<Int>()) } doReturn PackageInfo()
        on { getPackageInfo(any<String>(), any<PackageManager.PackageInfoFlags>()) } doReturn PackageInfo()
    }
    private val userDataRepository = TestUserDataRepository()
    private val appRepository = TestAppRepository()
    private val appStateCache = TestAppStateCache()
    private val appController = FakeAppController()
    private val serviceController = FakeServiceController()
    private val getAppControllerUseCase =
        GetAppControllerUseCase(userDataRepository, appController, appController)
    private val getServiceControllerUseCase =
        GetServiceControllerUseCase(userDataRepository, serviceController, serviceController)
    private val searchAppListUseCase = SearchAppListUseCase(
        pm,
        userDataRepository,
        appRepository,
        appStateCache,
        getAppControllerUseCase,
        getServiceControllerUseCase,
        mainDispatcherRule.testDispatcher,
    )

    @Test
    fun givenEmptyAppList_returnEmptyAppList() = runTest {
        val appList = emptyList<InstalledApp>()
        userDataRepository.sendUserData(emptyUserData)
        appRepository.sendAppList(appList)
        searchAppListUseCase("").test {
            assertEquals(emptyList(), awaitItem())
        }
    }
}
