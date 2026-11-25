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

package com.merxury.blocker.feature.appdetail.impl.bottomsheet

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.merxury.blocker.core.data.repository.userdata.UserDataRepository
import com.merxury.blocker.core.model.data.ComponentSortInfo
import com.merxury.blocker.core.model.preference.ComponentShowPriority
import com.merxury.blocker.core.model.preference.ComponentSorting
import com.merxury.blocker.core.model.preference.SortingOrder
import com.merxury.blocker.feature.appdetail.impl.bottomsheet.ComponentSortInfoUiState.Loading
import com.merxury.blocker.feature.appdetail.impl.bottomsheet.ComponentSortInfoUiState.Success
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ComponentSortViewModel @Inject constructor(
    private val userDataRepository: UserDataRepository,
) : ViewModel() {

    val uiState: StateFlow<ComponentSortInfoUiState> = userDataRepository.userData
        .map { userData ->
            Success(ComponentSortInfo(userData.componentSorting, userData.componentSortingOrder, userData.componentShowPriority))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = Loading,
        )

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
    data object Loading : ComponentSortInfoUiState
    data class Success(val componentSortInfo: ComponentSortInfo) : ComponentSortInfoUiState
}
