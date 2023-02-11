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

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.merxury.blocker.core.data.respository.component.LocalComponentRepository
import com.merxury.blocker.core.data.respository.userdata.UserDataRepository
import com.merxury.blocker.core.dispatchers.BlockerDispatchers.DEFAULT
import com.merxury.blocker.core.dispatchers.BlockerDispatchers.IO
import com.merxury.blocker.core.dispatchers.Dispatcher
import com.merxury.blocker.core.extension.exec
import com.merxury.blocker.core.model.ComponentType
import com.merxury.blocker.core.model.data.ComponentInfo
import com.merxury.blocker.core.model.preference.ComponentSorting
import com.merxury.blocker.core.model.preference.ComponentSorting.NAME_ASCENDING
import com.merxury.blocker.core.model.preference.ComponentSorting.NAME_DESCENDING
import com.merxury.blocker.core.ui.data.ErrorMessage
import com.merxury.blocker.core.ui.data.toErrorMessage
import com.merxury.blocker.feature.appdetail.cmplist.ComponentListUiState.Error
import com.merxury.blocker.feature.appdetail.cmplist.ComponentListUiState.Loading
import com.merxury.blocker.feature.appdetail.cmplist.ComponentListUiState.Success
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

class ComponentListViewModel @AssistedInject constructor(
    private val userDataRepository: UserDataRepository,
    private val componentRepository: LocalComponentRepository,
    @Assisted private val packageName: String,
    @Assisted private val type: ComponentType,
    @Dispatcher(IO) private val ioDispatcher: CoroutineDispatcher,
    @Dispatcher(DEFAULT) private val cpuDispatcher: CoroutineDispatcher,
) : ViewModel() {
    private val _uiState: MutableStateFlow<ComponentListUiState> = MutableStateFlow(Loading)
    val uiState: StateFlow<ComponentListUiState> = _uiState.asStateFlow()
    private val _errorState = MutableStateFlow<ErrorMessage?>(null)
    val errorState = _errorState.asStateFlow()
    private val exceptionHandler = CoroutineExceptionHandler { _, throwable ->
        Timber.e(throwable)
        _errorState.tryEmit(throwable.toErrorMessage())
    }
    private val stateList = mutableStateListOf<ComponentItem>()

    init {
        listenDataChange()
        getComponentList()
    }

    fun dismissAlert() = viewModelScope.launch {
        _errorState.emit(null)
    }

    fun launchActivity(packageName: String, componentName: String) {
        viewModelScope.launch(ioDispatcher + exceptionHandler) {
            "am start -n $packageName/$componentName".exec()
        }
    }

    fun stopService(packageName: String, componentName: String) {
        viewModelScope.launch(ioDispatcher + exceptionHandler) {
            "am stopservice $packageName/$componentName".exec()
        }
    }

    private fun listenDataChange() = viewModelScope.launch {
        componentRepository.data.collect { list ->
            stateList.clear()
            val userData = userDataRepository.userData.first()
            val componentItemList = list.map { it.toComponentInfo() }
            val sortedList = sortList(componentItemList, userData.componentSorting)
            stateList.addAll(sortedList)
            _uiState.emit(Success(stateList))
        }
    }

    private suspend fun sortList(
        list: List<ComponentItem>,
        sort: ComponentSorting,
    ): List<ComponentItem> = withContext(cpuDispatcher) {
        when (sort) {
            NAME_ASCENDING -> list.sortedBy { it.simpleName }
            NAME_DESCENDING -> list.sortedByDescending { it.simpleName }
        }
    }

    private fun getComponentList() = viewModelScope.launch {
        componentRepository.getComponentList(packageName, type)
            .onStart {
                Timber.d("getComponentList $packageName, $type")
                _uiState.emit(Loading)
            }
            .catch { _uiState.emit(Error(it.toErrorMessage())) }
            .collect()
    }

    fun controlComponent(
        packageName: String,
        componentName: String,
        enabled: Boolean,
    ) = viewModelScope.launch {
        componentRepository.controlComponent(packageName, componentName, enabled)
            .catch { exception ->
                _errorState.emit(exception.toErrorMessage())
            }
            .collect()
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

/**
 * Data representation for the component.
 */

data class ComponentItem(
    val name: String,
    val simpleName: String,
    val packageName: String,
    val type: ComponentType,
    val pmBlocked: Boolean,
    val ifwBlocked: Boolean = false,
    val isRunning: Boolean = false,
    val description: String? = null,
) {
    fun enabled() = !(pmBlocked || ifwBlocked)
}

private fun ComponentInfo.toComponentInfo() = ComponentItem(
    name = name,
    simpleName = simpleName,
    packageName = packageName,
    type = type,
    pmBlocked = pmBlocked,
    ifwBlocked = ifwBlocked,
    description = description,
)

sealed interface ComponentListUiState {
    object Loading : ComponentListUiState
    class Error(val error: ErrorMessage) : ComponentListUiState
    data class Success(
        val list: SnapshotStateList<ComponentItem>,
    ) : ComponentListUiState
}
