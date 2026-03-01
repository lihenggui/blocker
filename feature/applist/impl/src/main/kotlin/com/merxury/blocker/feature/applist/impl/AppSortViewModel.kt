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

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.merxury.blocker.core.data.respository.userdata.UserDataRepository
import com.merxury.blocker.core.model.data.AppSortInfo
import com.merxury.blocker.core.model.preference.AppSorting
import com.merxury.blocker.core.model.preference.SortingOrder
import com.merxury.blocker.core.model.preference.TopAppType
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AppSortViewModel @Inject constructor(
    private val userDataRepository: UserDataRepository,
) : ViewModel() {

    private val _appSortInfoUiState: MutableStateFlow<AppSortInfoUiState> =
        MutableStateFlow(AppSortInfoUiState.Loading)
    val appSortInfoUiState = _appSortInfoUiState.asStateFlow()

    init {
        viewModelScope.launch {
            userDataRepository.userData
                .map { userData ->
                    AppSortInfoUiState.Success(
                        AppSortInfo(
                            userData.appSorting,
                            userData.appSortingOrder,
                            userData.topAppType,
                        ),
                    )
                }
                .collect { _appSortInfoUiState.value = it }
        }
    }

    fun updateAppSorting(sorting: AppSorting) = viewModelScope.launch {
        userDataRepository.setAppSorting(sorting)
    }

    fun updateAppSortingOrder(order: SortingOrder) = viewModelScope.launch {
        userDataRepository.setAppSortingOrder(order)
    }

    fun updateTopAppType(topAppType: TopAppType) = viewModelScope.launch {
        userDataRepository.setTopAppType(topAppType)
    }
}

sealed interface AppSortInfoUiState {
    data object Loading : AppSortInfoUiState
    data class Success(val appSortInfo: AppSortInfo) : AppSortInfoUiState
}
