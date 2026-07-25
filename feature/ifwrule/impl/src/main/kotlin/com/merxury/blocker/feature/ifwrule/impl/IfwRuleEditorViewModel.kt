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

package com.merxury.blocker.feature.ifwrule.impl

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.merxury.blocker.core.ui.ifwruleeditor.BlockMode
import com.merxury.blocker.core.ui.ifwruleeditor.RuleEditorUiState
import com.merxury.blocker.feature.ifwrule.impl.model.mergeComponentRule
import com.merxury.blocker.feature.ifwrule.impl.model.toEditorState
import com.merxury.blocker.feature.ifwrule.impl.model.toIfwFilters
import com.merxury.core.ifw.IIntentFirewall
import com.merxury.core.ifw.editor.IfwEditorNode
import com.merxury.core.ifw.model.IfwComponentType
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber

@HiltViewModel(assistedFactory = IfwRuleEditorViewModel.Factory::class)
class IfwRuleEditorViewModel @AssistedInject constructor(
    private val intentFirewall: IIntentFirewall,
    @Assisted("packageName") val packageName: String,
    @Assisted("componentName") val componentName: String,
    @Assisted("componentType") componentTypeTag: String,
) : ViewModel() {

    @AssistedFactory
    interface Factory {
        fun create(
            @Assisted("packageName") packageName: String,
            @Assisted("componentName") componentName: String,
            @Assisted("componentType") componentType: String,
        ): IfwRuleEditorViewModel
    }

    private val componentType: IfwComponentType =
        IfwComponentType.fromXmlTag(componentTypeTag) ?: IfwComponentType.BROADCAST

    private val _uiState = MutableStateFlow<RuleEditorScreenUiState>(RuleEditorScreenUiState.Loading)
    val uiState: StateFlow<RuleEditorScreenUiState> = _uiState.asStateFlow()

    private val _saveComplete = MutableStateFlow(false)
    val saveComplete: StateFlow<Boolean> = _saveComplete.asStateFlow()

    init {
        loadRules()
    }

    private fun loadRules() {
        viewModelScope.launch {
            loadRulesInternal()
        }
    }

    fun updateBlockMode(mode: BlockMode) {
        updateEditor { copy(blockMode = mode) }
    }

    fun updateLog(enabled: Boolean) {
        updateEditor { copy(log = enabled) }
    }

    fun updateBlockEnabled(enabled: Boolean) {
        updateEditor { copy(blockEnabled = enabled) }
    }

    fun updateRootGroup(rootGroup: IfwEditorNode.Group) {
        updateEditor { copy(rootGroup = rootGroup) }
    }

    fun save() {
        viewModelScope.launch {
            saveRuleInternal()
        }
    }

    fun deleteRule() {
        viewModelScope.launch {
            deleteRuleInternal()
        }
    }

    private suspend fun loadRulesInternal() {
        try {
            val rules = intentFirewall.getRules(packageName)
            val editorState = rules.toEditorState(componentType, packageName, componentName)
            _uiState.value = RuleEditorScreenUiState.Success(editor = editorState)
        } catch (e: Exception) {
            Timber.e(e, "Failed to load IFW rules for $packageName")
            _uiState.value = RuleEditorScreenUiState.Error(e.message ?: "Unknown error")
        }
    }

    private suspend fun saveRuleInternal() {
        val state = (_uiState.value as? RuleEditorScreenUiState.Success)?.editor ?: return
        try {
            val newFilters = state.toIfwFilters()
            val currentRules = intentFirewall.getRules(packageName)
            val filterName = "$packageName/$componentName"
            val merged = currentRules.mergeComponentRule(
                componentType = componentType,
                componentName = filterName,
                newFilters = newFilters,
                block = state.blockEnabled,
                log = state.log,
            )
            intentFirewall.saveRules(packageName, merged)
            _saveComplete.value = true
        } catch (e: Exception) {
            Timber.e(e, "Failed to save IFW rules for $packageName")
        }
    }

    private suspend fun deleteRuleInternal() {
        try {
            val currentRules = intentFirewall.getRules(packageName)
            val filterName = "$packageName/$componentName"
            val merged = currentRules.mergeComponentRule(
                componentType = componentType,
                componentName = filterName,
                newFilters = null,
                block = true,
                log = true,
            )
            intentFirewall.saveRules(packageName, merged)
            _saveComplete.value = true
        } catch (e: Exception) {
            Timber.e(e, "Failed to delete IFW rule for $packageName/$componentName")
        }
    }

    private fun updateEditor(transform: RuleEditorUiState.() -> RuleEditorUiState) {
        val current = _uiState.value
        if (current is RuleEditorScreenUiState.Success) {
            _uiState.value = current.copy(
                editor = current.editor.transform(),
                hasUnsavedChanges = true,
            )
        }
    }
}

sealed interface RuleEditorScreenUiState {
    data object Loading : RuleEditorScreenUiState
    data class Success(
        val editor: RuleEditorUiState,
        val hasUnsavedChanges: Boolean = false,
    ) : RuleEditorScreenUiState

    data class Error(val message: String) : RuleEditorScreenUiState
}
