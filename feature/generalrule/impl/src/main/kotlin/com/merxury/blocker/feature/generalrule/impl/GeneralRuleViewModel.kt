/*
 * Copyright 2025 Blocker
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

package com.merxury.blocker.feature.generalrule.impl

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.merxury.blocker.core.analytics.AnalyticsHelper
import com.merxury.blocker.core.data.respository.app.AppRepository
import com.merxury.blocker.core.data.respository.component.ComponentRepository
import com.merxury.blocker.core.data.respository.generalrule.GeneralRuleRepository
import com.merxury.blocker.core.data.respository.userdata.AppPropertiesRepository
import com.merxury.blocker.core.dispatchers.BlockerDispatchers.IO
import com.merxury.blocker.core.dispatchers.Dispatcher
import com.merxury.blocker.core.domain.GatherAllMatchedComponentsUseCase
import com.merxury.blocker.core.domain.InitializeRuleStorageUseCase
import com.merxury.blocker.core.domain.SearchGeneralRuleUseCase
import com.merxury.blocker.core.domain.UpdateRuleMatchedAppsUseCase
import com.merxury.blocker.core.domain.model.InitializeState
import com.merxury.blocker.core.model.data.GeneralRule
import com.merxury.blocker.core.result.Result
import com.merxury.blocker.core.ui.ProcessingProgress
import com.merxury.blocker.core.ui.data.UiMessage
import com.merxury.blocker.core.ui.data.toErrorMessage
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber

@HiltViewModel(assistedFactory = GeneralRulesViewModel.Factory::class)
class GeneralRulesViewModel @AssistedInject constructor(
    private val appRepository: AppRepository,
    private val appPropertiesRepository: AppPropertiesRepository,
    private val generalRuleRepository: GeneralRuleRepository,
    private val componentRepository: ComponentRepository,
    private val initGeneralRuleUseCase: InitializeRuleStorageUseCase,
    private val searchRule: SearchGeneralRuleUseCase,
    private val updateRules: UpdateRuleMatchedAppsUseCase,
    private val gatherAllMatchedComponents: GatherAllMatchedComponentsUseCase,
    private val analyticsHelper: AnalyticsHelper,
    private val savedStateHandle: SavedStateHandle,
    @Dispatcher(IO) private val ioDispatcher: CoroutineDispatcher,
    @Assisted val initialRuleId: String,
) : ViewModel() {
    private val _uiState = MutableStateFlow<GeneralRuleUiState>(GeneralRuleUiState.Loading)
    val uiState: StateFlow<GeneralRuleUiState> = _uiState.asStateFlow()
    private val _errorState = MutableStateFlow<UiMessage?>(null)
    val errorState = _errorState.asStateFlow()
    private val loadExceptionHandler = CoroutineExceptionHandler { _, throwable ->
        Timber.e(throwable, "Error occurred while loading general rules")
        _errorState.value = throwable.toErrorMessage()
        _uiState.value = (GeneralRuleUiState.Error(throwable.toErrorMessage()))
    }
    private var loadRuleJob: Job? = null
    private var matchingComplete = false
    private val _processingProgress = MutableStateFlow<ProcessingProgress?>(null)
    val processingProgress = _processingProgress.asStateFlow()
    private val _isProcessing = MutableStateFlow(false)
    val isProcessing: StateFlow<Boolean> = _isProcessing.asStateFlow()

    // Key used to save and retrieve the currently selected topic id from saved state.
    private val selectedRuleIdKey = "selectedRuleIdKey"
    private val selectedRuleId: StateFlow<String?> = savedStateHandle.getStateFlow(
        key = selectedRuleIdKey,
        initialValue = initialRuleId,
    )

    init {
        loadData()
    }

    fun dismissAlert() = viewModelScope.launch {
        _errorState.emit(null)
    }

    fun onRuleClick(ruleId: String?) {
        savedStateHandle[selectedRuleIdKey] = ruleId
        loadSelectedRule()
    }

    fun controlAllComponents(enable: Boolean) {
        if (_isProcessing.value) return
        _isProcessing.value = true
        _processingProgress.value = ProcessingProgress(0, 0, enable, isComplete = false)
        viewModelScope.launch {
            try {
                analyticsHelper.logControlAllSdksClicked(newState = enable)
                val components = gatherAllMatchedComponents().first()
                if (components.isEmpty()) {
                    _processingProgress.value = null
                    return@launch
                }
                var processed = 0
                _processingProgress.value = ProcessingProgress(0, components.size, enable, isComplete = false)
                componentRepository.batchControlComponent(components, enable).collect {
                    processed++
                    _processingProgress.value = ProcessingProgress(processed, components.size, enable, isComplete = false)
                }
                // Collection ends only after controller state reconciliation and database writes.
                _processingProgress.value = ProcessingProgress(components.size, components.size, enable, isComplete = true)
            } catch (exception: CancellationException) {
                _processingProgress.value = null
                throw exception
            } catch (exception: Exception) {
                _processingProgress.value = null
                _errorState.value = exception.toErrorMessage()
            } finally {
                _isProcessing.value = false
            }
        }
    }

    fun dismissProcessingProgress() {
        _processingProgress.value = null
    }

    private fun loadSelectedRule() {
        _uiState.update {
            if (it is GeneralRuleUiState.Success) {
                it.copy(selectedRuleId = selectedRuleId.value)
            } else {
                it
            }
        }
    }

    private fun loadData() {
        loadRuleJob?.cancel()
        loadRuleJob = viewModelScope.launch(loadExceptionHandler) {
            if (!shouldRefreshList()) {
                Timber.d("No need to refresh the list")
                updateMatchedAppInfo()
                showGeneralRuleList(skipLoading = true)
                return@launch
            }
            _uiState.emit(GeneralRuleUiState.Loading)
            initGeneralRuleUseCase()
                .catch { _uiState.emit(GeneralRuleUiState.Error(it.toErrorMessage())) }
                .takeWhile { state -> state != InitializeState.Done }
                .collect { state ->
                    Timber.d("Initialize general rule: $state")
                }
            Timber.v("Get general rules from local storage")
            launch { updateGeneralRule() }
            showGeneralRuleList()
        }
    }

    private suspend fun showGeneralRuleList(skipLoading: Boolean = false) {
        searchRule()
            .catch { _uiState.emit(GeneralRuleUiState.Error(it.toErrorMessage())) }
            .distinctUntilChanged()
            .collect { rules ->
                val matchedRules = rules.filter { it.matchedAppCount > 0 }
                val unmatchedRules = rules.filter { it.matchedAppCount == 0 }
                _uiState.update { state ->
                    val newState = if (state is GeneralRuleUiState.Success) {
                        state.copy(matchedRules = matchedRules, unmatchedRules = unmatchedRules)
                    } else {
                        GeneralRuleUiState.Success(
                            matchedRules = matchedRules,
                            unmatchedRules = unmatchedRules,
                            matchProgress = if (matchingComplete) 1F else 0F,
                        )
                    }
                    if (!skipLoading) {
                        newState
                    } else {
                        newState.copy(matchProgress = 1F)
                    }
                }
            }
    }

    private suspend fun updateGeneralRule() {
        // Get general from network first
        generalRuleRepository.updateGeneralRule()
            .flowOn(ioDispatcher)
            .collect { result ->
                when (result) {
                    is Result.Success -> updateMatchedAppInfo()

                    is Result.Error -> _errorState.emit(result.exception.toErrorMessage())

                    else -> {
                        // Do nothing
                    }
                }
            }
    }

    private suspend fun updateMatchedAppInfo() {
        updateRules().first()
        matchingComplete = true
        _uiState.update {
            if (it is GeneralRuleUiState.Success) it.copy(matchProgress = 1F) else it
        }
        saveHash()
    }

    private suspend fun shouldRefreshList(): Boolean {
        val appProperties = appPropertiesRepository.appProperties.first()
        val lastOpenedAppListHash = appProperties.lastOpenedAppListHash
        val lastOpenedRuleHash = appProperties.lastOpenedRuleHash
        if (lastOpenedAppListHash.isEmpty() || lastOpenedRuleHash.isEmpty()) {
            Timber.d("User opened this screen for the first time, should refresh the list")
            return true
        }
        val currentAppListHash = getCurrentAppListHash()
        val currentRuleHash = getCurrentRuleHash()
        val appListChanged = currentAppListHash != lastOpenedAppListHash
        val ruleChanged = currentRuleHash != lastOpenedRuleHash
        val shouldReloadList = appListChanged || ruleChanged
        Timber.d(
            "App list changed: $appListChanged, rule changed: $ruleChanged, " +
                "should reload the list: $shouldReloadList",
        )
        return shouldReloadList
    }

    private suspend fun getCurrentAppListHash(): String = appRepository.getApplicationList()
        .first()
        .hashCode()
        .toString()

    private suspend fun getCurrentRuleHash(): String = generalRuleRepository.getRuleHash()
        .first()

    private suspend fun saveHash() {
        val appListHash = getCurrentAppListHash()
        val ruleHash = getCurrentRuleHash()
        Timber.d("Save app list hash: $appListHash, rule hash: $ruleHash")
        appPropertiesRepository.updateLastOpenedAppListHash(appListHash)
        appPropertiesRepository.updateLastOpenedRuleHash(ruleHash)
    }

    @AssistedFactory
    interface Factory {
        fun create(
            @Assisted initialRuleId: String,
        ): GeneralRulesViewModel
    }
}

sealed interface GeneralRuleUiState {
    data object Loading : GeneralRuleUiState
    data class Success(
        val matchedRules: List<GeneralRule>,
        val unmatchedRules: List<GeneralRule>,
        val matchProgress: Float = 0F,
        val selectedRuleId: String? = null,
    ) : GeneralRuleUiState

    data class Error(val error: UiMessage) : GeneralRuleUiState
}
