/*
 * Copyright 2022 Blocker
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *       https://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 */

package com.merxury.blocker.ui.home.advsearch.online

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.elvishew.xlog.XLog
import com.merxury.blocker.core.data.respository.GeneralRuleRepository
import com.merxury.blocker.core.model.data.GeneralRule
import com.merxury.blocker.core.result.Result
import com.merxury.blocker.core.result.asResult
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

@HiltViewModel
class GeneralRulesViewModel @Inject constructor(
    private val generalRuleRepository: GeneralRuleRepository
) :
    ViewModel() {
    private val logger = XLog.tag("GeneralRulesViewModel")
    private val reloadTrigger = MutableLiveData<Boolean>()
    val generalRuleUiState = generalRulesUiState(generalRuleRepository)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = GeneralRuleUiState.Loading
        )

    init {
        reloadTrigger.value = true
        generalRuleRepository.getGeneralRules()
    }

    fun refresh() {
        logger.i("Refresh data")
        reloadTrigger.value = true
    }
}

private fun generalRulesUiState(
    generalRuleRepository: GeneralRuleRepository
): Flow<GeneralRuleUiState> {
    return generalRuleRepository.getGeneralRules()
        .asResult()
        .map { result ->
            when (result) {
                is Result.Success -> {
                    GeneralRuleUiState.Success(result.data)
                }

                is Result.Loading -> {
                    GeneralRuleUiState.Loading
                }

                is Result.Error -> {
                    GeneralRuleUiState.Error(result.exception?.message)
                }
            }
        }
}

sealed interface GeneralRuleUiState {
    data class Success(val generalRules: List<GeneralRule>) : GeneralRuleUiState

    data class Error(val message: String?) : GeneralRuleUiState

    object Loading : GeneralRuleUiState
}
