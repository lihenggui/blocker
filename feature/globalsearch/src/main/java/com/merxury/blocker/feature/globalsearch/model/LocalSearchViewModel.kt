package com.merxury.blocker.feature.globalsearch.model

import androidx.compose.ui.text.input.TextFieldValue
import androidx.lifecycle.ViewModel
import com.merxury.blocker.core.database.generalrule.GeneralRuleEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

@HiltViewModel
class LocalSearchViewModel @Inject constructor() : ViewModel() {
    private val _searchBoxUiState = MutableStateFlow(SearchBoxUiState())
    val searchBoxUiState: StateFlow<SearchBoxUiState> = _searchBoxUiState.asStateFlow()
}

sealed interface LocalSearchUiState {
    object Loading : LocalSearchUiState
    class LocalSearchResult(val filter: List<GeneralRuleEntity>) : LocalSearchUiState
    class Error(val message: String) : LocalSearchUiState
}

data class SearchBoxUiState(
    val keyword: TextFieldValue = TextFieldValue()
)
