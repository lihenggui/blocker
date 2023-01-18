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

package com.merxury.blocker.feature.appdetail.model

import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import com.merxury.blocker.core.decoder.StringDecoder
import com.merxury.blocker.core.model.data.ComponentInfo
import com.merxury.blocker.core.ui.data.ErrorMessage
import com.merxury.blocker.feature.appdetail.model.ComponentListUiState.Loading
import com.merxury.blocker.feature.appdetail.navigation.AppDetailArgs
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

@HiltViewModel
class ComponentListViewModel @Inject constructor(
    app: android.app.Application,
    savedStateHandle: SavedStateHandle,
    stringDecoder: StringDecoder
) : AndroidViewModel(app) {
    private val appPackageNameArgs: AppDetailArgs = AppDetailArgs(savedStateHandle, stringDecoder)
    private val _uiState: MutableStateFlow<ComponentListUiState> =
        MutableStateFlow(Loading)
    val uiState: StateFlow<ComponentListUiState> = _uiState

    init {
//        when (eComponentType) {
//            ACTIVITY -> getActivityList()
//            RECEIVER -> getReceiverList()
//            PROVIDER -> getProviderList()
//            SERVICE -> getServiceList()
//        }
    }

    private fun getActivityList() {
        // TODO
    }

    private fun getReceiverList() {
        // TODO
    }

    private fun getProviderList() {
        // TODO
    }

    private fun getServiceList() {
        // TODO
    }

    fun controlComponent(packageName: String, componentName: String, enabled: Boolean) {
        // TODO
    }

    fun onRefresh() {
        // TODO
    }
}

sealed interface ComponentListUiState {
    object Loading : ComponentListUiState
    class Error(val error: ErrorMessage) : ComponentListUiState
    data class Success(
        val list: SnapshotStateList<ComponentInfo>
    ) : ComponentListUiState
}
