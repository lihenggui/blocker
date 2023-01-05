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

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import com.merxury.blocker.core.decoder.StringDecoder
import com.merxury.blocker.core.model.Application
import com.merxury.blocker.core.ui.TabState
import com.merxury.blocker.core.ui.data.ErrorMessage
import com.merxury.blocker.feature.appdetail.R.string
import com.merxury.blocker.feature.appdetail.navigation.AppDetailArgs
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

@HiltViewModel
class AppInfoViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    stringDecoder: StringDecoder
) : ViewModel() {
    private val appPackageNameArgs: AppDetailArgs = AppDetailArgs(savedStateHandle, stringDecoder)
    private val _uiState: MutableStateFlow<AppInfoUiState> =
        MutableStateFlow(AppInfoUiState.Loading)
    val uiState: StateFlow<AppInfoUiState> = _uiState

    private val _tabState = MutableStateFlow(
        TabState(
            titles = listOf(
                string.app_info,
                string.service,
                string.service,
                string.activity,
                string.content_provider
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

    fun onRefresh() {
        // TODO
    }

    fun onShare() {
        // TODO
    }

    fun onFindInPage() {
        // TODO
    }

    fun onEnableApp() {
        // TODO
    }

    fun onEnableAll() {
        // TODO
    }

    fun onBlockAll() {
        // TODO
    }
}

sealed interface AppInfoUiState {
    object Loading : AppInfoUiState
    class Error(val error: ErrorMessage) : AppInfoUiState
    data class Success(
        val appInfo: Application
    ) : AppInfoUiState
}
