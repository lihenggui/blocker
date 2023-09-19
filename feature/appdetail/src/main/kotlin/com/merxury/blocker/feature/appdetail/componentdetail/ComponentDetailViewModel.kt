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

package com.merxury.blocker.feature.appdetail.componentdetail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.merxury.blocker.core.data.respository.componentdetail.IComponentDetailRepository
import com.merxury.blocker.core.dispatchers.BlockerDispatchers.IO
import com.merxury.blocker.core.dispatchers.Dispatcher
import com.merxury.blocker.core.model.data.ComponentDetail
import com.merxury.blocker.core.ui.data.UiMessage
import com.merxury.blocker.feature.appdetail.componentdetail.ComponentDetailUiState.Loading
import com.merxury.blocker.feature.appdetail.componentdetail.ComponentDetailUiState.Success
import com.merxury.blocker.feature.appdetail.navigation.ComponentDetailArgs
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ComponentDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val componentDetailRepository: IComponentDetailRepository,
    @Dispatcher(IO) private val ioDispatcher: CoroutineDispatcher,
) : ViewModel() {
    private val componentDetailArg = ComponentDetailArgs(savedStateHandle)

    private val _uiState = MutableStateFlow<ComponentDetailUiState>(Loading)
    val uiState = _uiState.asStateFlow()

    init {
        load()
    }

    private fun load() = viewModelScope.launch(ioDispatcher) {
        val userGeneratedDetail = componentDetailRepository
            .getUserGeneratedDetail(componentDetailArg.name)
            .first()
        if (userGeneratedDetail != null) {
            _uiState.value = Success(
                detail = userGeneratedDetail,
            )
            // Do NOT update the dialog if user saved the data previously
            return@launch
        }
        val localDetail = componentDetailRepository
            .getLocalComponentDetail(componentDetailArg.name)
            .first()
        if (localDetail != null) {
            _uiState.value = Success(
                detail = localDetail,
            )
            return@launch
        }
        // No matching found in the network, emit the default value
        // Dismiss the loading progress bar
        _uiState.value = Success(
            detail = ComponentDetail(name = componentDetailArg.name),
        )
    }

    fun onInfoChanged(detail: ComponentDetail) {
        _uiState.update {
            when (it) {
                is Loading -> Success(detail = detail)
                is Success -> it.copy(detail = detail)
                else -> Success(detail = detail)
            }
        }
    }

    fun save(detail: ComponentDetail) = viewModelScope.launch {
        componentDetailRepository.saveComponentDetail(detail)
            .collect {}
    }
}

sealed interface ComponentDetailUiState {
    data object Loading : ComponentDetailUiState
    data class Success(
        val detail: ComponentDetail,
    ) : ComponentDetailUiState

    data class Error(val message: UiMessage) : ComponentDetailUiState
}
