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

package com.merxury.blocker.feature.appdetail.impl.componentdetail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.merxury.blocker.core.data.respository.componentdetail.ComponentDetailRepository
import com.merxury.blocker.core.dispatchers.BlockerDispatchers.IO
import com.merxury.blocker.core.dispatchers.Dispatcher
import com.merxury.blocker.core.model.data.ComponentDetail
import com.merxury.blocker.core.ui.data.UiMessage
import com.merxury.blocker.feature.appdetail.impl.componentdetail.ComponentDetailUiState.Loading
import com.merxury.blocker.feature.appdetail.impl.componentdetail.ComponentDetailUiState.Success
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * The description of one component, as the user wrote it or as it came with the rules, and the
 * user's edits of it until they are saved.
 */
@HiltViewModel(assistedFactory = ComponentDetailViewModel.Factory::class)
class ComponentDetailViewModel @AssistedInject constructor(
    private val componentDetailRepository: ComponentDetailRepository,
    @Dispatcher(IO) private val ioDispatcher: CoroutineDispatcher,
    @Assisted val componentName: String,
) : ViewModel() {
    private val edited = MutableStateFlow<ComponentDetail?>(null)
    val editedDetail: StateFlow<ComponentDetail?> = edited.asStateFlow()

    // What the user saved before wins over the description that came with the rules.
    private val stored = flow {
        val detail = componentDetailRepository.getUserGeneratedDetail(componentName).first()
            ?: componentDetailRepository.getLocalComponentDetail(componentName).first()
            ?: ComponentDetail(name = componentName)
        emit(detail)
    }.flowOn(ioDispatcher)

    val uiState: StateFlow<ComponentDetailUiState> = stored.map(::Success).stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = Loading,
    )

    fun onInfoChanged(change: ComponentDetail.() -> ComponentDetail) {
        val detail = edited.value ?: (uiState.value as? Success)?.detail ?: return
        edited.value = detail.change()
    }

    fun save(detail: ComponentDetail) {
        val current = edited.value ?: detail
        viewModelScope.launch {
            componentDetailRepository.saveComponentDetail(current).collect {}
        }
    }

    @AssistedFactory
    interface Factory {
        fun create(
            @Assisted componentName: String,
        ): ComponentDetailViewModel
    }
}

sealed interface ComponentDetailUiState {
    data object Loading : ComponentDetailUiState
    data class Success(
        val detail: ComponentDetail,
    ) : ComponentDetailUiState

    data class Error(val message: UiMessage) : ComponentDetailUiState
}
