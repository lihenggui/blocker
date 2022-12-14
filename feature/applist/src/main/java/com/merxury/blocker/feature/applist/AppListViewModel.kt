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

package com.merxury.blocker.feature.applist

import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.graphics.ImageBitmap
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

@HiltViewModel
class AppListViewModel @Inject constructor() : ViewModel() {
    private val _uiState: MutableStateFlow<AppListUiState> =
        MutableStateFlow(AppListUiState.Loading)
    val uiState: StateFlow<AppListUiState> = _uiState

    fun onRefresh() {
        // TODO
    }
}

data class AppServiceStatus(
    var running: Int = 0,
    var blocked: Int = 0,
    var total: Int = 0,
    var packageName: String = ""
)

data class AppInfo(
    var packageName: String = "",
    var versionName: String? = "",
    var appIcon: ImageBitmap,
    var appServiceStatus: AppServiceStatus
)

sealed interface AppListUiState {
    object Loading : AppListUiState
    class Error(val errorMessage: String) : AppListUiState
    data class Success(
        val appList: SnapshotStateList<AppInfo>
    ) : AppListUiState
}
