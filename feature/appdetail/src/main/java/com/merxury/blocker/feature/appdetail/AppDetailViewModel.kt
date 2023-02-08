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
import com.merxury.blocker.core.ui.TabState
import com.merxury.blocker.core.ui.data.ErrorMessage
import com.merxury.blocker.core.utils.ApplicationUtil
import com.merxury.blocker.feature.appdetail.AppInfoUiState.Loading
import com.merxury.blocker.feature.appdetail.R.string
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

    private val _tabState = MutableStateFlow(
        TabState(
            titles = listOf(
                string.app_info,
                string.receiver,
                string.service,
                string.activity,
                string.content_provider,
            ),
            currentIndex = 0,
        ),
    )
    val tabState: StateFlow<TabState> = _tabState.asStateFlow()

    private val _searchBoxUiState = MutableStateFlow(SearchBoxUiState())
    val searchBoxUiState: StateFlow<SearchBoxUiState> = _searchBoxUiState.asStateFlow()

    init {
        load()
    }

    fun switchTab(newIndex: Int) {
        if (newIndex != tabState.value.currentIndex) {
            _tabState.update {
                it.copy(currentIndex = newIndex)
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

    fun onSearchTextChanged(changedSearchText: TextFieldValue) {
        _searchBoxUiState.update { it.copy(keyword = changedSearchText) }
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

data class SearchBoxUiState(
    val keyword: TextFieldValue = TextFieldValue(),
)