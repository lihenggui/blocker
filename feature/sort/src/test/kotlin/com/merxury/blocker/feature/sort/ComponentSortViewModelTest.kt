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

package com.merxury.blocker.feature.sort

import com.merxury.blocker.core.model.data.ComponentSortInfo
import com.merxury.blocker.core.model.preference.ComponentShowPriority
import com.merxury.blocker.core.model.preference.ComponentSorting
import com.merxury.blocker.core.model.preference.SortingOrder
import com.merxury.blocker.core.model.preference.UserPreferenceData
import com.merxury.blocker.core.testing.repository.TestUserDataRepository
import com.merxury.blocker.core.testing.repository.defaultUserData
import com.merxury.blocker.core.testing.util.MainDispatcherRule
import com.merxury.blocker.feature.sort.ComponentSortInfoUiState.Success
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals

class ComponentSortViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val userDataRepository = TestUserDataRepository()
    private lateinit var viewModel: ComponentSortViewModel

    @Before
    fun setup() {
        viewModel = ComponentSortViewModel(userDataRepository)
    }

    @Test
    fun componentSortInfoUiState_whenInitial_thenShowDefault() = runTest {
        assertEquals(ComponentSortInfoUiState.Loading, viewModel.componentSortInfoUiState.value)
    }

    @Test
    fun componentSortInfoUiState_whenLoaded_thenShowSuccess() = runTest {
        val collectJob = launch(UnconfinedTestDispatcher()) {
            viewModel.componentSortInfoUiState.collect()
        }

        userDataRepository.sendUserData(defaultUserData)
        assertEquals(
            Success(defaultUserData.toComponentSortInfo()),
            viewModel.componentSortInfoUiState.value,
        )

        collectJob.cancel()
    }

    @Test
    fun componentSortInfoUiState_whenUpdateComponentSorting_thenUpdateComponentSorting() = runTest {
        val collectJob = launch(UnconfinedTestDispatcher()) {
            viewModel.componentSortInfoUiState.collect()
        }

        userDataRepository.sendUserData(defaultUserData)
        viewModel.updateComponentSorting(ComponentSorting.PACKAGE_NAME)
        viewModel.loadComponentSortInfo()
        val updatedUserData = defaultUserData.copy(componentSorting = ComponentSorting.PACKAGE_NAME)
        assertEquals(
            Success(updatedUserData.toComponentSortInfo()),
            viewModel.componentSortInfoUiState.value,
        )

        collectJob.cancel()
    }

    @Test
    fun componentSortInfoUiState_whenUpdateComponentSortingOrder_thenUpdateComponentSortingOrder() = runTest {
        val collectJob = launch(UnconfinedTestDispatcher()) {
            viewModel.componentSortInfoUiState.collect()
        }

        userDataRepository.sendUserData(defaultUserData)
        viewModel.updateComponentSortingOrder(SortingOrder.DESCENDING)
        viewModel.loadComponentSortInfo()
        val updatedUserData =
            defaultUserData.copy(componentSortingOrder = SortingOrder.DESCENDING)
        assertEquals(
            Success(updatedUserData.toComponentSortInfo()),
            viewModel.componentSortInfoUiState.value,
        )

        collectJob.cancel()
    }

    @Test
    fun componentSortInfoUiState_whenUpdateComponentShowPriority_thenUpdateComponentShowPriority() = runTest {
        val collectJob = launch(UnconfinedTestDispatcher()) {
            viewModel.componentSortInfoUiState.collect()
        }

        userDataRepository.sendUserData(defaultUserData)
        viewModel.updateComponentShowPriority(ComponentShowPriority.ENABLED_COMPONENTS_FIRST)
        viewModel.loadComponentSortInfo()
        val updatedUserData =
            defaultUserData.copy(componentShowPriority = ComponentShowPriority.ENABLED_COMPONENTS_FIRST)
        assertEquals(
            Success(updatedUserData.toComponentSortInfo()),
            viewModel.componentSortInfoUiState.value,
        )

        collectJob.cancel()
    }
}

private fun UserPreferenceData.toComponentSortInfo() = ComponentSortInfo(
    sorting = componentSorting,
    order = componentSortingOrder,
    priority = componentShowPriority,
)
