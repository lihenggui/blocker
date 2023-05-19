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
import com.merxury.blocker.core.model.preference.AppSorting
import com.merxury.blocker.core.model.preference.AppSorting.NAME
import com.merxury.blocker.core.model.preference.SortingOrder
import com.merxury.blocker.core.model.preference.SortingOrder.ASCENDING
import com.merxury.blocker.feature.sort.viewmodel.AppSortInfoUiState.Loading
import com.merxury.blocker.feature.sort.viewmodel.AppSortInfoUiState.Success
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AppSortViewModel @Inject constructor(
    private val userDataRepository: UserDataRepository,
) : ViewModel() {

    private val _appSortInfoUiState: MutableStateFlow<AppSortInfoUiState> =
        MutableStateFlow(Loading)
    val appSortInfoUiState = _appSortInfoUiState.asStateFlow()

    init {
        loadAppSortInfo()
    }

    private fun loadAppSortInfo() = viewModelScope.launch {
        val userData = userDataRepository.userData.first()
        val sorting = userData.appSorting
        val order = userData.appSortingOrder
        val showRunningAppsOnTop = userData.showRunningAppsOnTop
        _appSortInfoUiState.emit(
            Success(AppSortInfo(sorting, order, showRunningAppsOnTop)),
        )
    }

    fun updateAppSorting(sorting: AppSorting) = viewModelScope.launch {
        userDataRepository.setAppSorting(sorting)
    }

    fun updateAppSortingOrder(order: SortingOrder) = viewModelScope.launch {
        userDataRepository.setAppSortingOrder(order)
    }

    fun updateShowRunningAppsOnTop(shouldShow: Boolean) {
        viewModelScope.launch {
            userDataRepository.setShowRunningAppsOnTop(shouldShow)
        }
    }
}

sealed interface AppSortInfoUiState {
    object Loading : AppSortInfoUiState
    data class Success(val appSortInfo: AppSortInfo) : AppSortInfoUiState
}

data class AppSortInfo(
    val sorting: AppSorting = NAME,
    val order: SortingOrder = ASCENDING,
    val showRunningAppsOnTop: Boolean = false,
)
