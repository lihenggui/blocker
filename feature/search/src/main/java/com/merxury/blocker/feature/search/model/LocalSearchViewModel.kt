package com.merxury.blocker.feature.search.model

import androidx.lifecycle.ViewModel
import com.merxury.blocker.core.database.generalrule.GeneralRuleEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class LocalSearchViewModel @Inject constructor() : ViewModel() {
}

sealed interface LocalSearchUiState {
    object Loading : LocalSearchUiState
    class LocalSearchResult(val filter: List<GeneralRuleEntity>) : LocalSearchUiState
    class Error(val message: String) : LocalSearchUiState
}
