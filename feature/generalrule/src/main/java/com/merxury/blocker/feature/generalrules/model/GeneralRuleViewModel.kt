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

package com.merxury.blocker.feature.generalrules.model

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.merxury.blocker.core.data.respository.component.ComponentRepository
import com.merxury.blocker.core.data.respository.generalrule.GeneralRuleRepository
import com.merxury.blocker.core.data.respository.userdata.UserDataRepository
import com.merxury.blocker.core.model.data.ComponentInfo
import com.merxury.blocker.core.result.Result
import com.merxury.blocker.core.ui.data.UiMessage
import com.merxury.blocker.core.ui.data.toErrorMessage
import com.merxury.blocker.core.ui.rule.GeneralRuleWithApp
import com.merxury.blocker.core.ui.rule.toGeneralRuleWithApp
import com.merxury.blocker.feature.generalrules.model.GeneralRuleUiState.Error
import com.merxury.blocker.feature.generalrules.model.GeneralRuleUiState.Loading
import com.merxury.blocker.feature.generalrules.model.GeneralRuleUiState.Success
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class GeneralRulesViewModel @Inject constructor(
    private val generalRuleRepository: GeneralRuleRepository,
    private val componentRepository: ComponentRepository,
    private val userDataRepository: UserDataRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow<GeneralRuleUiState>(Loading)
    val uiState: StateFlow<GeneralRuleUiState> = _uiState.asStateFlow()
    private val _errorState = MutableStateFlow<UiMessage?>(null)
    val errorState = _errorState.asStateFlow()

    init {
        loadData()
        updateGeneralRule()
    }

    fun dismissAlert() = viewModelScope.launch {
        _errorState.emit(null)
    }

    private fun loadData() = viewModelScope.launch {
        generalRuleRepository.getGeneralRules()
            .onStart { _uiState.emit(Loading) }
            .catch { _uiState.emit(Error(it.toErrorMessage())) }
            .collect { rules ->
                if (rules.isEmpty()) {
                    return@collect
                }
                val serverUrl = userDataRepository.userData
                    .first()
                    .ruleServerProvider
                    .baseUrl
                val updatedRules = rules.map { rule ->
                    rule.copy(iconUrl = serverUrl + rule.iconUrl)
                }
                    .map { it.toGeneralRuleWithApp() }
                    .toMutableList()
                // Update rules with matched app info
                rules.forEachIndexed { index, rule ->
                    val matchedComponent = mutableListOf<ComponentInfo>()
                    rule.searchKeyword.forEach { keyword ->
                        val matchComponents = componentRepository.searchComponent(keyword).first()
                        matchedComponent.addAll(matchComponents)
                    }
                    val matchedAppCount = matchedComponent.groupBy { it.packageName }
                        .size
                    Timber.v("Matched rule: ${rule.name} count: $matchedAppCount")
                    updatedRules[index] = updatedRules[index]
                        .copy(matchedAppCount = matchedAppCount)
                }
                updatedRules.sortByDescending { it.matchedAppCount }
                _uiState.emit(Success(updatedRules))
            }
    }

    private fun updateGeneralRule() = viewModelScope.launch {
        generalRuleRepository.updateGeneralRule().collect { result ->
            if (result is Result.Error) {
                _errorState.emit(result.exception?.toErrorMessage())
            }
        }
    }
}

sealed interface GeneralRuleUiState {
    object Loading : GeneralRuleUiState
    class Success(
        val rules: List<GeneralRuleWithApp>,
    ) : GeneralRuleUiState

    class Error(val error: UiMessage) : GeneralRuleUiState
}
