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

package com.merxury.blocker.ui.home.advsearch.online

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.merxury.blocker.core.data.respository.generalrule.GeneralRuleRepository
import com.merxury.blocker.core.model.data.GeneralRule
import com.merxury.blocker.core.result.Result
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber

@HiltViewModel
class GeneralRulesViewModel @Inject constructor(
    private val generalRuleRepository: GeneralRuleRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow<GeneralRuleUiState>(GeneralRuleUiState.Loading)
    val uiState = _uiState.asStateFlow()

    init {
        loadRuleFromCache()
        loadRuleFromNetwork()
    }

    private fun loadRuleFromCache() = viewModelScope.launch {
        generalRuleRepository.getCacheGeneralRules()
            .collect { list ->
                if (list.isEmpty()) {
                    Timber.d("No cache data in the db")
                } else {
                    _uiState.emit(GeneralRuleUiState.Success(list))
                }
            }
    }

    private fun loadRuleFromNetwork() = viewModelScope.launch {
        generalRuleRepository.getNetworkGeneralRules()
            .collect { result ->
                when (result) {
                    is Result.Success -> {
                        Timber.d("Load online rules from network successfully.")
                        _uiState.emit(GeneralRuleUiState.Success(result.data))
                        generalRuleRepository.clearCacheData()
                        generalRuleRepository.updateGeneralRules(result.data)
                    }

                    is Result.Error -> {
                        Timber.e("Can't fetch network rules", result.exception)
                        val currentState = _uiState.value
                        if (currentState is GeneralRuleUiState.Loading) {
                            _uiState.emit(GeneralRuleUiState.Error(result.exception?.message))
                        }
                    }

                    is Result.Loading -> {
                        Timber.d("Start to load rules data from the network.")
                    }
                }
            }
    }
}

sealed interface GeneralRuleUiState {
    data class Success(val generalRules: List<GeneralRule>) : GeneralRuleUiState

    data class Error(val message: String?) : GeneralRuleUiState

    object Loading : GeneralRuleUiState
}
