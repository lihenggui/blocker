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

package com.merxury.blocker.feature.appdetail

import android.content.Context
import androidx.compose.ui.text.input.TextFieldValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.merxury.blocker.core.decoder.StringDecoder
import com.merxury.blocker.core.model.Application
import com.merxury.blocker.core.ui.AppDetailTabs
import com.merxury.blocker.core.ui.AppDetailTabs.Activity
import com.merxury.blocker.core.ui.AppDetailTabs.Info
import com.merxury.blocker.core.ui.AppDetailTabs.Provider
import com.merxury.blocker.core.ui.AppDetailTabs.Receiver
import com.merxury.blocker.core.ui.AppDetailTabs.Service
import com.merxury.blocker.core.ui.data.ErrorMessage
import com.merxury.blocker.core.ui.state.toolbar.AppBarActionState
import com.merxury.blocker.core.utils.ApplicationUtil
import com.merxury.blocker.feature.appdetail.AppInfoUiState.Loading
import com.merxury.blocker.feature.appdetail.navigation.AppDetailArgs
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber

@HiltViewModel
class AppDetailViewModel @Inject constructor(
    app: android.app.Application,
    savedStateHandle: SavedStateHandle,
    stringDecoder: StringDecoder,
) : AndroidViewModel(app) {
    private val appPackageNameArgs: AppDetailArgs = AppDetailArgs(savedStateHandle, stringDecoder)
    private val _uiState: MutableStateFlow<AppInfoUiState> =
        MutableStateFlow(Loading)
    val uiState: StateFlow<AppInfoUiState> = _uiState
    private val _appBarUiState = MutableStateFlow(AppBarUiState())
    val appBarUiState: StateFlow<AppBarUiState> = _appBarUiState.asStateFlow()
    private val _tabState = MutableStateFlow(
        AppDetailTabState(
            items = listOf(
                Info,
                Receiver,
                Service,
                Activity,
                Provider,
            ),
            selectedItem = Info,
        ),
    )
    val tabState: StateFlow<AppDetailTabState> = _tabState.asStateFlow()

    init {
        loadTabInfo()
        load()
    }

    private fun loadTabInfo() {
        val screen = appPackageNameArgs.tabs
        Timber.v("Jump to tab: $screen")
        _tabState.update { it.copy(selectedItem = screen) }
    }

    fun switchTab(newTab: AppDetailTabs) {
        if (newTab != tabState.value.selectedItem) {
            _tabState.update {
                it.copy(selectedItem = newTab)
            }
        }
    }

    fun launchApp(packageName: String) {
        Timber.i("Launch app $packageName")
        val context: Context = getApplication()
        context.packageManager.getLaunchIntentForPackage(packageName)?.let { launchIntent ->
            context.startActivity(launchIntent)
        }
    }

    fun search(changedSearchText: TextFieldValue) {
        _appBarUiState.update { it.copy(keyword = changedSearchText) }
    }

    fun changeSearchMode(isSearchMode: Boolean) {
        _appBarUiState.update {
            it.copy(
                isSearchMode = isSearchMode,
            )
        }
    }

    fun updateAppBarAction(actions: AppBarActionState) {
        _appBarUiState.update {
            it.copy(actions = actions)
        }
    }

    private fun load() = viewModelScope.launch {
        val packageName = appPackageNameArgs.packageName
        val app = ApplicationUtil.getApplicationInfo(getApplication(), packageName)
        if (app == null) {
            val error = ErrorMessage("Can't find $packageName in this device.")
            Timber.e(error.message)
            _uiState.emit(AppInfoUiState.Error(error))
        } else {
            _uiState.emit(AppInfoUiState.Success(app))
        }
    }
}

sealed interface AppInfoUiState {
    object Loading : AppInfoUiState
    class Error(val error: ErrorMessage) : AppInfoUiState
    data class Success(
        val appInfo: Application,
    ) : AppInfoUiState
}

data class AppBarUiState(
    val keyword: TextFieldValue = TextFieldValue(),
    val isSearchMode: Boolean = false,
    val actions: AppBarActionState = AppBarActionState(),
)

data class AppDetailTabState(
    val items: List<AppDetailTabs>,
    val selectedItem: AppDetailTabs,
) {
    fun currentIndex(): Int {
        val currentIndex = items.indexOf(selectedItem)
        if (currentIndex == -1) {
            Timber.w("Can't find index of $selectedItem, returning to default page.")
            return 0
        }
        return currentIndex
    }
}
