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

package com.merxury.blocker.feature.applist.impl

import com.merxury.blocker.core.model.data.AppSortInfo
import com.merxury.blocker.core.model.preference.AppSorting
import com.merxury.blocker.core.model.preference.SortingOrder
import com.merxury.blocker.core.model.preference.TopAppType
import com.merxury.blocker.core.model.preference.UserPreferenceData
import com.merxury.blocker.core.testing.repository.TestUserDataRepository
import com.merxury.blocker.core.testing.repository.defaultUserData
import com.merxury.blocker.core.testing.util.MainDispatcherRule
import com.merxury.blocker.feature.applist.impl.AppSortInfoUiState.Success
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals

class AppSortViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val userDataRepository = TestUserDataRepository()
    private lateinit var viewModel: AppSortViewModel

    @Before
    fun setup() {
        viewModel = AppSortViewModel(userDataRepository)
    }

    @Test
    fun appSortInfoUiState_whenInitial_thenShowDefault() = runTest {
        assertEquals(AppSortInfoUiState.Loading, viewModel.appSortInfoUiState.value)
    }

    @Test
    fun appSortInfoUiState_whenLoaded_thenShowSuccess() = runTest {
        val collectJob = launch(UnconfinedTestDispatcher()) {
            viewModel.appSortInfoUiState.collect()
        }

        userDataRepository.sendUserData(defaultUserData)
        assertEquals(
            Success(defaultUserData.toAppSortInfo()),
            viewModel.appSortInfoUiState.value,
        )

        collectJob.cancel()
    }

    @Test
    fun appSortInfoUiState_whenUpdateAppSorting_thenUpdateAppSorting() = runTest {
        val collectJob = launch(UnconfinedTestDispatcher()) {
            viewModel.appSortInfoUiState.collect()
        }

        userDataRepository.sendUserData(defaultUserData)
        viewModel.updateAppSorting(AppSorting.LAST_UPDATE_TIME)
        val updatedUserData = defaultUserData.copy(appSorting = AppSorting.LAST_UPDATE_TIME)
        assertEquals(
            Success(updatedUserData.toAppSortInfo()),
            viewModel.appSortInfoUiState.value,
        )

        collectJob.cancel()
    }

    @Test
    fun appSortInfoUiState_whenUpdateAppSortingOrder_thenUpdateAppSortingOrder() = runTest {
        val collectJob = launch(UnconfinedTestDispatcher()) {
            viewModel.appSortInfoUiState.collect()
        }

        userDataRepository.sendUserData(defaultUserData)
        viewModel.updateAppSortingOrder(SortingOrder.DESCENDING)
        val updatedUserData = defaultUserData.copy(appSortingOrder = SortingOrder.DESCENDING)
        assertEquals(
            Success(updatedUserData.toAppSortInfo()),
            viewModel.appSortInfoUiState.value,
        )

        collectJob.cancel()
    }

    @Test
    fun appSortInfoUiState_whenUpdateTopAppType_thenUpdateTopAppType() = runTest {
        val collectJob = launch(UnconfinedTestDispatcher()) {
            viewModel.appSortInfoUiState.collect()
        }

        userDataRepository.sendUserData(defaultUserData)
        viewModel.updateTopAppType(TopAppType.RUNNING)
        val updatedUserData = defaultUserData.copy(topAppType = TopAppType.RUNNING)
        assertEquals(
            Success(updatedUserData.toAppSortInfo()),
            viewModel.appSortInfoUiState.value,
        )

        collectJob.cancel()
    }
}

private fun UserPreferenceData.toAppSortInfo() = AppSortInfo(
    sorting = appSorting,
    order = appSortingOrder,
    topAppType = topAppType,
)
