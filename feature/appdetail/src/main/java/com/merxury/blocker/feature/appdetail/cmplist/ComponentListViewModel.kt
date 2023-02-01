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

package com.merxury.blocker.feature.appdetail.cmplist

import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.toMutableStateList
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.merxury.blocker.core.data.respository.component.LocalComponentRepository
import com.merxury.blocker.core.model.ComponentType
import com.merxury.blocker.core.model.data.ComponentInfo
import com.merxury.blocker.core.ui.data.ErrorMessage
import com.merxury.blocker.feature.appdetail.cmplist.ComponentListUiState.Loading
import com.merxury.blocker.feature.appdetail.cmplist.ComponentListUiState.Success
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import timber.log.Timber

class ComponentListViewModel @AssistedInject constructor(
    private val repository: LocalComponentRepository,
    @Assisted private val packageName: String,
    @Assisted private val type: ComponentType,
) : ViewModel() {
    private val _uiState: MutableStateFlow<ComponentListUiState> =
        MutableStateFlow(Loading)
    val uiState: StateFlow<ComponentListUiState> = _uiState

    init {
        getComponentList()
    }

    private fun getComponentList() = viewModelScope.launch {
        Timber.d("getComponentList $packageName, $type")
        repository.getComponentList(packageName, type).collect { list ->
            _uiState.emit(Success(list.toMutableStateList()))
        }
    }

    fun controlComponent(packageName: String, componentName: String, enabled: Boolean) {
        Timber.d("Control $packageName/$componentName to state $enabled")
    }

    @AssistedFactory
    interface Factory {
        fun create(packageName: String, type: ComponentType): ComponentListViewModel
    }

    companion object {
        @Suppress("UNCHECKED_CAST")
        fun provideFactory(
            assistedFactory: Factory,
            packageName: String,
            type: ComponentType,
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return assistedFactory.create(packageName, type) as T
            }
        }
    }
}

sealed interface ComponentListUiState {
    object Loading : ComponentListUiState
    class Error(val error: ErrorMessage) : ComponentListUiState
    data class Success(
        val list: SnapshotStateList<ComponentInfo>,
    ) : ComponentListUiState
}
