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

import android.content.Context
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.toMutableStateList
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.merxury.blocker.core.decoder.StringDecoder
import com.merxury.blocker.core.extension.getSimpleName
import com.merxury.blocker.core.model.data.ComponentInfo
import com.merxury.blocker.core.ui.data.ErrorMessage
import com.merxury.blocker.core.utils.ApplicationUtil
import com.merxury.blocker.feature.appdetail.cmplist.ComponentListUiState.Loading
import com.merxury.blocker.feature.appdetail.cmplist.ComponentListUiState.Success
import com.merxury.blocker.feature.appdetail.navigation.AppDetailArgs
import com.merxury.blocker.feature.appdetail.navigation.Screen
import com.merxury.blocker.feature.appdetail.navigation.Screen.Activity
import com.merxury.blocker.feature.appdetail.navigation.Screen.Receiver
import com.merxury.blocker.feature.appdetail.navigation.Screen.Service
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class ComponentListViewModel @Inject constructor(
    app: android.app.Application,
    savedStateHandle: SavedStateHandle,
    stringDecoder: StringDecoder,
) : AndroidViewModel(app) {
    private val vmArgs = AppDetailArgs(savedStateHandle, stringDecoder)
    private val _uiState: MutableStateFlow<ComponentListUiState> =
        MutableStateFlow(Loading)
    val uiState: StateFlow<ComponentListUiState> = _uiState

    init {
        getComponentList()
    }

    private fun getComponentList() = viewModelScope.launch {
        val context: Context = getApplication()
        val pm = context.packageManager
        val packageName = vmArgs.packageName
        val list = when (Screen.fromName(vmArgs.screenName)) {
            Receiver -> ApplicationUtil.getReceiverList(pm, packageName)
            Service -> ApplicationUtil.getServiceList(pm, packageName)
            Activity -> ApplicationUtil.getActivityList(pm, packageName)
            else -> ApplicationUtil.getProviderList(pm, packageName)
        }
        val convertedList = list.map {
            ComponentInfo(
                simpleName = it.getSimpleName(),
                name = it.name,
                packageName = it.packageName,
                enabled = it.enabled,
            )
        }.toMutableStateList()
        _uiState.emit(Success(convertedList))
    }

    fun controlComponent(packageName: String, componentName: String, enabled: Boolean) {
        Timber.d("Control $packageName/$componentName to state $enabled")
    }
}

sealed interface ComponentListUiState {
    object Loading : ComponentListUiState
    class Error(val error: ErrorMessage) : ComponentListUiState
    data class Success(
        val list: SnapshotStateList<ComponentInfo>,
    ) : ComponentListUiState
}
