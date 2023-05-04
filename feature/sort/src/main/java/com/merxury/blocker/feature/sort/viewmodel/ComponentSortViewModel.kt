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

package com.merxury.blocker.feature.sort.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.merxury.blocker.core.data.respository.userdata.UserDataRepository
import com.merxury.blocker.core.model.preference.ComponentShowPriority
import com.merxury.blocker.core.model.preference.ComponentShowPriority.DISABLED_COMPONENTS_FIRST
import com.merxury.blocker.core.model.preference.ComponentSorting
import com.merxury.blocker.core.model.preference.ComponentSorting.COMPONENT_NAME
import com.merxury.blocker.core.model.preference.SortingOrder
import com.merxury.blocker.core.model.preference.SortingOrder.ASCENDING
import com.merxury.blocker.feature.sort.viewmodel.ComponentSortInfoUiState.Loading
import com.merxury.blocker.feature.sort.viewmodel.ComponentSortInfoUiState.Success
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SortViewModel @Inject constructor(
    private val userDataRepository: UserDataRepository,
) : ViewModel() {

    private val _componentSortInfoUiState: MutableStateFlow<ComponentSortInfoUiState> =
        MutableStateFlow(Loading)
    val componentSortInfoUiState = _componentSortInfoUiState.asStateFlow()

    init {
        loadComponentSortInfo()
    }

    private fun loadComponentSortInfo() = viewModelScope.launch {
        val userData = userDataRepository.userData.first()
        val sorting = userData.componentSorting
        val order = userData.componentSortingOrder
        val priority = userData.componentShowPriority
        _componentSortInfoUiState.emit(
            Success(ComponentSortInfo(sorting, order, priority)),
        )
    }

    fun updateComponentSorting(sorting: ComponentSorting) = viewModelScope.launch {
        userDataRepository.setComponentSorting(sorting)
    }

    fun updateComponentSortingOrder(order: SortingOrder) = viewModelScope.launch {
        userDataRepository.setComponentSortingOrder(order)
    }

    fun updateComponentShowPriority(priority: ComponentShowPriority) = viewModelScope.launch {
        userDataRepository.setComponentShowPriority(priority)
    }
}

sealed interface ComponentSortInfoUiState {
    object Loading : ComponentSortInfoUiState
    data class Success(val componentSortInfo: ComponentSortInfo) : ComponentSortInfoUiState
}

data class ComponentSortInfo(
    val sorting: ComponentSorting = COMPONENT_NAME,
    val order: SortingOrder = ASCENDING,
    val priority: ComponentShowPriority = DISABLED_COMPONENTS_FIRST,
)
