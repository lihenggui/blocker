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

package com.merxury.blocker.feature.appdetail

import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import com.merxury.blocker.core.decoder.StringDecoder
import com.merxury.blocker.core.model.Application
import com.merxury.blocker.core.ui.TabState
import com.merxury.blocker.core.ui.data.ErrorMessage
import com.merxury.blocker.feature.appdetail.navigation.AppDetailArgs
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

@HiltViewModel
class AppDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    stringDecoder: StringDecoder
) : ViewModel() {
    private val appPackageNameArgs: AppDetailArgs = AppDetailArgs(savedStateHandle, stringDecoder)
    private val _uiState: MutableStateFlow<AppDetailUiState> =
        MutableStateFlow(AppDetailUiState.Loading)
    val uiState: StateFlow<AppDetailUiState> = _uiState

    private val _tabState = MutableStateFlow(
        TabState(
            titles = listOf(
                R.string.app_info,
                R.string.service,
                R.string.service,
                R.string.activity,
                R.string.content_provider
            ),
            currentIndex = 0
        )
    )
    val tabState: StateFlow<TabState> = _tabState.asStateFlow()

    fun switchTab(newIndex: Int) {
        if (newIndex != tabState.value.currentIndex) {
            _tabState.update {
                it.copy(currentIndex = newIndex)
            }
        }
    }

    fun onSwitch(componentInfo: ComponentInfo) {
        // TODO
    }

    fun onRefresh() {
        // TODO
    }
}

data class ComponentInfo(
    val simpleName: String,
    val name: String,
    val packageName: String,
    val enabled: Boolean
)

sealed interface AppDetailUiState {
    object Loading : AppDetailUiState
    class Error(val error: ErrorMessage) : AppDetailUiState
    data class Success(
        val appInfo: Application,
        val service: SnapshotStateList<ComponentInfo>,
        val receiver: SnapshotStateList<ComponentInfo>,
        val activity: SnapshotStateList<ComponentInfo>,
        val contentProvider: SnapshotStateList<ComponentInfo>
    ) : AppDetailUiState
}
