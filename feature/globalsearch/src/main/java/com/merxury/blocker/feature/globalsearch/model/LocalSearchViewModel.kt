package com.merxury.blocker.feature.globalsearch.model

import android.content.pm.PackageInfo
import androidx.compose.ui.text.input.TextFieldValue
import androidx.lifecycle.ViewModel
import com.merxury.blocker.core.ui.data.ErrorMessage
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
    private val _localSearchUiState = MutableStateFlow(LocalSearchUiState.Loading)
    val localSearchUiState: StateFlow<LocalSearchUiState> = _localSearchUiState.asStateFlow()

    fun onSearchTextChanged(changedSearchText: TextFieldValue) {
        _searchBoxUiState.update { it.copy(keyword = changedSearchText) }
    }

    fun onClearClick() {
        _searchBoxUiState.update { SearchBoxUiState() }
    }

    fun onNavigationBack() {
        // TODO
    }
}

sealed interface LocalSearchUiState {
    object Loading : LocalSearchUiState
    class LocalSearchResult(
        val filter: List<FilterAppItem>,
        val appCount: Int,
        val componentCount: Int,
        val onlineRuleCount: Int
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
