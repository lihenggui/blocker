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
    private val _localSearchUiState = MutableStateFlow(LocalSearchUiState.NoSearch)
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

    fun onNavigationClick() {
        // TODO
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
)

data class SearchTabState(
    val titles: List<Int>,
    val currentIndex: Int,
    val appCount: Int = 0,
    val componentCount: Int = 0,
    val rulesCount: Int = 0,
)
