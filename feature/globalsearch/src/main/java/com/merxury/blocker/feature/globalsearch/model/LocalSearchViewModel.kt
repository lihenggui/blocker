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

package com.merxury.blocker.feature.globalsearch.model

import android.content.pm.PackageInfo
import androidx.compose.ui.text.input.TextFieldValue
import androidx.lifecycle.ViewModel
import com.merxury.blocker.core.ui.data.ErrorMessage
import com.merxury.blocker.feature.globalsearch.R
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

@HiltViewModel
class LocalSearchViewModel @Inject constructor() : ViewModel() {
    private val _searchBoxUiState = MutableStateFlow(SearchBoxUiState())
    val searchBoxUiState: StateFlow<SearchBoxUiState> = _searchBoxUiState.asStateFlow()
    private val _localSearchUiState =
        MutableStateFlow<LocalSearchUiState>(LocalSearchUiState.NoSearch)
    val localSearchUiState: StateFlow<LocalSearchUiState> = _localSearchUiState.asStateFlow()

    private val _tabState = MutableStateFlow(
        SearchTabState(
            titles = listOf(
                R.string.application,
                R.string.component,
                R.string.online_rule
            ),
            currentIndex = 0
        )
    )
    val tabState: StateFlow<SearchTabState> = _tabState.asStateFlow()

    fun switchTab(newIndex: Int) {
        if (newIndex != tabState.value.currentIndex) {
            _tabState.update {
                it.copy(currentIndex = newIndex)
            }
        }
    }

    fun onSearchTextChanged(changedSearchText: TextFieldValue) {
        _searchBoxUiState.update { it.copy(keyword = changedSearchText) }
    }

    fun onClearClick() {
        _searchBoxUiState.update { SearchBoxUiState() }
    }

    fun onSelectAll() {
        // TODO
    }

    fun onBlockAll() {
        // TODO
    }

    fun onCheckAll() {
        // TODO
    }

    fun switchSelectedMode(value: Boolean) {
        // TODO, isSelectedMode = true
    }

    fun onSelectItem(select: Boolean) {
        // TODO
    }
}

sealed interface LocalSearchUiState {
    object NoSearch : LocalSearchUiState
    object Loading : LocalSearchUiState
    class LocalSearchResult(
        val filter: List<FilterAppItem>,
        val isSelectedMode: Boolean,
        val selectedAppCount: Int
    ) : LocalSearchUiState

    class Error(val message: ErrorMessage) : LocalSearchUiState
}

data class SearchBoxUiState(
    val keyword: TextFieldValue = TextFieldValue()
)

data class FilterAppItem(
    val label: String = "",
    val packageInfo: PackageInfo?,
    val activityCount: Int = 0,
    val broadcastCount: Int = 0,
    val serviceCount: Int = 0,
    val contentProviderCount: Int = 0,
    val isSelected: Boolean = false
)

data class SearchTabState(
    val titles: List<Int>,
    val currentIndex: Int,
    val appCount: Int = 0,
    val componentCount: Int = 0,
    val rulesCount: Int = 0,
)
