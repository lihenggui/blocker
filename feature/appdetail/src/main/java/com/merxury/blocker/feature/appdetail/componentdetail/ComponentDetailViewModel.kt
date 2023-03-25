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
import com.merxury.blocker.core.data.respository.component.LocalComponentRepository
import com.merxury.blocker.core.data.respository.componentdetail.ComponentDetailRepository
import com.merxury.blocker.core.decoder.StringDecoder
import com.merxury.blocker.core.dispatchers.BlockerDispatchers.IO
import com.merxury.blocker.core.dispatchers.Dispatcher
import com.merxury.blocker.core.model.data.ComponentDetail
import com.merxury.blocker.core.ui.data.UiMessage
import com.merxury.blocker.feature.appdetail.navigation.ComponentDetailArgs
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ComponentDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    stringDecoder: StringDecoder,
    private val componentRepository: LocalComponentRepository,
    private val componentDetailRepository: ComponentDetailRepository,
    @Dispatcher(IO) private val ioDispatcher: CoroutineDispatcher,
) : ViewModel() {
    private val componentDetailArg = ComponentDetailArgs(savedStateHandle, stringDecoder)

    private val _uiState = MutableStateFlow<ComponentDetailUiState>(ComponentDetailUiState.Loading)
    val uiState = _uiState.asStateFlow()

    init {
        load()
    }
    private fun load() = viewModelScope.launch(ioDispatcher) {
        componentDetailRepository.getComponentDetail(componentDetailArg.name)
            .collect { detail ->
                if (detail != null) {
                    _uiState.value = ComponentDetailUiState.Success(detail)
                }
            }
    }

    fun save(componentDetail: ComponentDetail) = viewModelScope.launch(ioDispatcher) {
        componentDetailRepository.saveComponentDetail(componentDetail, userGenerated = true)
    }
}

sealed class ComponentDetailUiState {
    object Loading : ComponentDetailUiState()
    data class Success(val componentDetail: ComponentDetail) : ComponentDetailUiState()
    data class Error(val message: UiMessage) : ComponentDetailUiState()
}
