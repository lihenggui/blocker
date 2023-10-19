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

package com.merxury.blocker.feature.generalrules

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.merxury.blocker.core.data.respository.generalrule.GeneralRuleRepository
import com.merxury.blocker.core.dispatchers.BlockerDispatchers.IO
import com.merxury.blocker.core.dispatchers.Dispatcher
import com.merxury.blocker.core.domain.InitializeRuleStorageUseCase
import com.merxury.blocker.core.domain.SearchGeneralRuleUseCase
import com.merxury.blocker.core.domain.UpdateRuleMatchedAppUseCase
import com.merxury.blocker.core.domain.model.InitializeState
import com.merxury.blocker.core.model.data.GeneralRule
import com.merxury.blocker.core.result.Result
import com.merxury.blocker.core.ui.data.UiMessage
import com.merxury.blocker.core.ui.data.toErrorMessage
import com.merxury.blocker.feature.generalrules.GeneralRuleUiState.Error
import com.merxury.blocker.feature.generalrules.GeneralRuleUiState.Loading
import com.merxury.blocker.feature.generalrules.GeneralRuleUiState.Success
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class GeneralRulesViewModel @Inject constructor(
    private val initGeneralRuleUseCase: InitializeRuleStorageUseCase,
    private val generalRuleRepository: GeneralRuleRepository,
    private val searchRule: SearchGeneralRuleUseCase,
    private val updateRule: UpdateRuleMatchedAppUseCase,
    @Dispatcher(IO) private val ioDispatcher: CoroutineDispatcher,
) : ViewModel() {
    private val _uiState = MutableStateFlow<GeneralRuleUiState>(Loading)
    val uiState: StateFlow<GeneralRuleUiState> = _uiState.asStateFlow()
    private val _errorState = MutableStateFlow<UiMessage?>(null)
    val errorState = _errorState.asStateFlow()
    private var loadRuleJob: Job? = null

    init {
        loadData()
    }

    fun dismissAlert() = viewModelScope.launch {
        _errorState.emit(null)
    }

    private fun loadData() {
        loadRuleJob?.cancel()
        loadRuleJob = viewModelScope.launch {
            _uiState.emit(Loading)
            initGeneralRuleUseCase()
                .catch { _uiState.emit(Error(it.toErrorMessage())) }
                .takeWhile { state -> state != InitializeState.Done }
                .collect { state ->
                    Timber.d("Initialize general rule: $state")
                }
            Timber.v("Get general rules from local storage")
            updateGeneralRule()
            searchRule()
                .catch { _uiState.emit(Error(it.toErrorMessage())) }
                .distinctUntilChanged()
                .collect { rules ->
                    _uiState.update { state ->
                        if (state is Success) {
                            state.copy(rules = rules)
                        } else {
                            Success(rules = rules)
                        }
                    }
                }
        }
    }

    private fun updateGeneralRule() = viewModelScope.launch {
        // Get general from network first
        generalRuleRepository.updateGeneralRule()
            .flowOn(ioDispatcher)
            .collect { result ->
                when (result) {
                    is Result.Success -> updateMatchedAppInfo()
                    is Result.Error -> _errorState.emit(result.exception?.toErrorMessage())
                    else -> {
                        // Do nothing
                    }
                }
            }
    }

    private fun updateMatchedAppInfo() = viewModelScope.launch {
        // Get matched app info from local
        val ruleList = generalRuleRepository.getGeneralRules()
            .first()
        if (ruleList.isEmpty()) {
            return@launch
        }
        var matchedApps = 0F
        ruleList.forEach { rule ->
            // No need to handle result
            updateRule(rule).firstOrNull()
            matchedApps += 1
            _uiState.update {
                if (it is Success) {
                    it.copy(matchProgress = matchedApps / ruleList.size)
                } else {
                    it
                }
            }
        }
    }
}

sealed interface GeneralRuleUiState {
    data object Loading : GeneralRuleUiState
    data class Success(
        val rules: List<GeneralRule>,
        val matchProgress: Float = 0F,
    ) : GeneralRuleUiState

    data class Error(val error: UiMessage) : GeneralRuleUiState
}
