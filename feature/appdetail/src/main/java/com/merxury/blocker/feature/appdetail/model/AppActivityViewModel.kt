/*
 * Copyright 2022 Blocker
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
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import com.merxury.blocker.core.decoder.StringDecoder
import com.merxury.blocker.core.model.data.ComponentInfo
import com.merxury.blocker.core.ui.data.ErrorMessage
import com.merxury.blocker.feature.appdetail.navigation.AppDetailArgs
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

@HiltViewModel
class AppActivityViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    stringDecoder: StringDecoder
) : ViewModel() {
    private val appPackageNameArgs: AppDetailArgs = AppDetailArgs(savedStateHandle, stringDecoder)
    private val _uiState: MutableStateFlow<AppActivityUiState> =
        MutableStateFlow(AppActivityUiState.Loading)
    val uiState: StateFlow<AppActivityUiState> = _uiState

    fun onSwitch(simpleName: String, name: String, enabled: Boolean) {
        // TODO
    }

    fun onRefresh() {
        // TODO
    }
}

sealed interface AppActivityUiState {
    object Loading : AppActivityUiState
    class Error(val error: ErrorMessage) : AppActivityUiState
    data class Success(
        val activity: SnapshotStateList<ComponentInfo>
    ) : AppActivityUiState
}
