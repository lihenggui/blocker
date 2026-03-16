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

package com.merxury.blocker.feature.globalifwrule.impl

import android.content.pm.PackageManager
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.merxury.core.ifw.IIntentFirewall
import com.merxury.core.ifw.model.IfwComponentType
import com.merxury.core.ifw.model.IfwFilter
import com.merxury.core.ifw.model.IfwRule
import com.merxury.core.ifw.model.IfwRules
import com.merxury.core.ifw.model.StringMatcher
import com.merxury.core.ifw.model.toSummary
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class GlobalIfwRuleViewModel @Inject constructor(
    private val intentFirewall: IIntentFirewall,
    private val packageManager: PackageManager,
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {
    private companion object {
        const val EDITOR_VISIBLE_KEY = "editor_visible"
        const val EDITING_PACKAGE_NAME_KEY = "editing_package_name"
        const val EDITING_RULE_INDEX_KEY = "editing_rule_index"
    }

    private val _uiState = MutableStateFlow<GlobalIfwRuleUiState>(GlobalIfwRuleUiState.Loading)
    val uiState: StateFlow<GlobalIfwRuleUiState> = _uiState.asStateFlow()

    private val _editorState = MutableStateFlow(
        GlobalIfwRuleEditorUiState(
            screen = if (savedStateHandle.get<Boolean>(EDITOR_VISIBLE_KEY) == true) {
                GlobalIfwRuleScreenState.EDIT
            } else {
                GlobalIfwRuleScreenState.LIST
            },
        ),
    )
    val editorState: StateFlow<GlobalIfwRuleEditorUiState> = _editorState.asStateFlow()

    init {
        loadAllRules()
    }

    fun refresh() {
        loadAllRules()
    }

    fun startAddingRule() {
        savedStateHandle[EDITOR_VISIBLE_KEY] = true
        savedStateHandle[EDITING_PACKAGE_NAME_KEY] = null
        savedStateHandle[EDITING_RULE_INDEX_KEY] = null
        _editorState.value = GlobalIfwRuleEditorUiState(
            screen = GlobalIfwRuleScreenState.EDIT,
            editingData = null,
        )
    }

    fun startEditingRule(packageName: String, ruleIndex: Int) {
        val editingData = getRuleForEdit(packageName, ruleIndex) ?: return
        savedStateHandle[EDITOR_VISIBLE_KEY] = true
        savedStateHandle[EDITING_PACKAGE_NAME_KEY] = packageName
        savedStateHandle[EDITING_RULE_INDEX_KEY] = ruleIndex
        _editorState.value = GlobalIfwRuleEditorUiState(
            screen = GlobalIfwRuleScreenState.EDIT,
            editingData = editingData,
        )
    }

    fun dismissEditor() {
        savedStateHandle[EDITOR_VISIBLE_KEY] = false
        savedStateHandle[EDITING_PACKAGE_NAME_KEY] = null
        savedStateHandle[EDITING_RULE_INDEX_KEY] = null
        _editorState.value = GlobalIfwRuleEditorUiState()
    }

    fun saveRule(data: AddRuleData) {
        if (data.editingRuleIndex != null) {
            updateRule(data)
        } else {
            saveNewRule(data)
        }
    }

    private fun saveNewRule(data: AddRuleData) {
        viewModelScope.launch {
            try {
                val filters = data.conditions.mapNotNull { it.toIfwFilter() }
                val newRule = IfwRule(
                    componentType = data.componentType,
                    block = data.block,
                    log = data.log,
                    filters = filters,
                )
                val currentRules = intentFirewall.getRules(data.packageName)
                val updatedRules = IfwRules(currentRules.rules + newRule)
                intentFirewall.saveRules(data.packageName, updatedRules)
                dismissEditor()
                loadAllRules()
            } catch (e: Exception) {
                Timber.e(e, "Failed to save new rule for ${data.packageName}")
            }
        }
    }

    fun getRuleForEdit(packageName: String, ruleIndex: Int): AddRuleData? {
        val state = _uiState.value as? GlobalIfwRuleUiState.Success ?: return null
        val group = state.groups.find { it.packageName == packageName } ?: return null
        val ruleItem = group.rules.find { it.ruleIndex == ruleIndex } ?: return null
        return AddRuleData(
            packageName = packageName,
            componentType = ruleItem.componentType,
            block = ruleItem.block,
            log = ruleItem.log,
            conditions = ruleItem.filters.map { filter ->
                filter.toSimpleCondition()
            },
            editingRuleIndex = ruleIndex,
        )
    }

    private fun updateRule(data: AddRuleData) {
        viewModelScope.launch {
            try {
                val ruleIndex = data.editingRuleIndex ?: return@launch
                val filters = data.conditions.mapNotNull { it.toIfwFilter() }
                val newRule = IfwRule(
                    componentType = data.componentType,
                    block = data.block,
                    log = data.log,
                    filters = filters,
                )
                val currentRules = intentFirewall.getRules(data.packageName)
                val updatedRules = currentRules.rules.toMutableList()
                if (ruleIndex in updatedRules.indices) {
                    updatedRules[ruleIndex] = newRule
                }
                intentFirewall.saveRules(data.packageName, IfwRules(updatedRules))
                dismissEditor()
                loadAllRules()
            } catch (e: Exception) {
                Timber.e(e, "Failed to update rule for ${data.packageName}")
            }
        }
    }

    fun deleteRule(packageName: String, ruleIndex: Int) {
        viewModelScope.launch {
            try {
                val currentRules = intentFirewall.getRules(packageName)
                val updatedRules = currentRules.rules.toMutableList()
                if (ruleIndex in updatedRules.indices) {
                    updatedRules.removeAt(ruleIndex)
                    intentFirewall.saveRules(packageName, IfwRules(updatedRules))
                    loadAllRules()
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to delete rule for $packageName at index $ruleIndex")
            }
        }
    }

    private fun loadAllRules() {
        viewModelScope.launch {
            _uiState.value = GlobalIfwRuleUiState.Loading
            try {
                intentFirewall.resetCache()
                val allRules = intentFirewall.getAllRules()
                val groups = allRules.mapNotNull { (packageName, ifwRules) ->
                    if (ifwRules.isEmpty()) return@mapNotNull null
                    val appLabel = resolveAppLabel(packageName)
                    val ruleItems = ifwRules.rules.mapIndexed { index, rule ->
                        rule.toRuleItemUiState(index, packageName)
                    }
                    PackageRuleGroup(
                        packageName = packageName,
                        appLabel = appLabel,
                        rules = ruleItems,
                    )
                }.sortedBy { it.appLabel ?: it.packageName }
                _uiState.value = GlobalIfwRuleUiState.Success(groups)
                restoreEditorState(groups)
            } catch (e: Exception) {
                Timber.e(e, "Failed to load all IFW rules")
                _uiState.value = GlobalIfwRuleUiState.Error(e.message ?: "Unknown error")
            }
        }
    }

    private fun restoreEditorState(groups: List<PackageRuleGroup>) {
        if (savedStateHandle.get<Boolean>(EDITOR_VISIBLE_KEY) != true) return

        val editingPackageName = savedStateHandle.get<String>(EDITING_PACKAGE_NAME_KEY)
        val editingRuleIndex = savedStateHandle.get<Int>(EDITING_RULE_INDEX_KEY)

        if (editingPackageName == null || editingRuleIndex == null) {
            _editorState.value = GlobalIfwRuleEditorUiState(
                screen = GlobalIfwRuleScreenState.EDIT,
                editingData = null,
            )
            return
        }

        val group = groups.find { it.packageName == editingPackageName }
        val ruleItem = group?.rules?.find { it.ruleIndex == editingRuleIndex }

        if (ruleItem == null) {
            dismissEditor()
            return
        }

        _editorState.value = GlobalIfwRuleEditorUiState(
            screen = GlobalIfwRuleScreenState.EDIT,
            editingData = AddRuleData(
                packageName = editingPackageName,
                componentType = ruleItem.componentType,
                block = ruleItem.block,
                log = ruleItem.log,
                conditions = ruleItem.filters.map { filter -> filter.toSimpleCondition() },
                editingRuleIndex = editingRuleIndex,
            ),
        )
    }

    private fun resolveAppLabel(packageName: String): String? = try {
        val appInfo = packageManager.getApplicationInfo(packageName, 0)
        packageManager.getApplicationLabel(appInfo).toString()
    } catch (e: PackageManager.NameNotFoundException) {
        null
    }

    private fun IfwRule.toRuleItemUiState(index: Int, packageName: String): RuleItemUiState {
        val filtersSummary = filters.joinToString("\n") { it.toDisplaySummary(packageName) }
        return RuleItemUiState(
            componentType = componentType,
            block = block,
            log = log,
            filtersSummary = filtersSummary,
            filters = filters,
            ruleIndex = index,
        )
    }

    private fun IfwFilter.toDisplaySummary(packageName: String): String = when (this) {
        is IfwFilter.ComponentFilter -> {
            val compName = name.substringAfter("/")
            if (compName.startsWith(packageName)) {
                compName.removePrefix(packageName)
            } else {
                compName
            }
        }
        is IfwFilter.And -> filters.joinToString(" AND ") { it.toDisplaySummary(packageName) }
        is IfwFilter.Or -> filters.joinToString(" OR ") { it.toDisplaySummary(packageName) }
        is IfwFilter.Not -> "NOT (${filter.toDisplaySummary(packageName)})"
        else -> toSummary()
    }
}

sealed interface GlobalIfwRuleUiState {
    data object Loading : GlobalIfwRuleUiState
    data class Success(val groups: List<PackageRuleGroup>) : GlobalIfwRuleUiState
    data class Error(val message: String) : GlobalIfwRuleUiState
}

data class PackageRuleGroup(
    val packageName: String,
    val appLabel: String?,
    val rules: List<RuleItemUiState>,
)

data class RuleItemUiState(
    val componentType: IfwComponentType,
    val block: Boolean,
    val log: Boolean,
    val filtersSummary: String,
    val filters: List<IfwFilter>,
    val ruleIndex: Int,
)

data class GlobalIfwRuleEditorUiState(
    val screen: GlobalIfwRuleScreenState = GlobalIfwRuleScreenState.LIST,
    val editingData: AddRuleData? = null,
)

enum class GlobalIfwRuleScreenState {
    LIST,
    EDIT,
}

private fun IfwFilter.toSimpleCondition(): SimpleCondition = when (this) {
    is IfwFilter.ComponentFilter -> SimpleCondition(SimpleFilterType.COMPONENT_FILTER, name)
    is IfwFilter.Action -> SimpleCondition(SimpleFilterType.ACTION, matcher.valueOrEmpty())
    is IfwFilter.Category -> SimpleCondition(SimpleFilterType.CATEGORY, name)
    is IfwFilter.SenderPackage -> SimpleCondition(SimpleFilterType.SENDER_PACKAGE, name)
    is IfwFilter.Component -> SimpleCondition(SimpleFilterType.COMPONENT, matcher.valueOrEmpty())
    is IfwFilter.ComponentName -> SimpleCondition(SimpleFilterType.COMPONENT_NAME, matcher.valueOrEmpty())
    is IfwFilter.ComponentPackage -> SimpleCondition(SimpleFilterType.COMPONENT_PACKAGE, matcher.valueOrEmpty())
    is IfwFilter.Host -> SimpleCondition(SimpleFilterType.HOST, matcher.valueOrEmpty())
    is IfwFilter.Scheme -> SimpleCondition(SimpleFilterType.SCHEME, matcher.valueOrEmpty())
    is IfwFilter.Path -> SimpleCondition(SimpleFilterType.PATH, matcher.valueOrEmpty())
    is IfwFilter.Data -> SimpleCondition(SimpleFilterType.DATA, matcher.valueOrEmpty())
    is IfwFilter.MimeType -> SimpleCondition(SimpleFilterType.MIME_TYPE, matcher.valueOrEmpty())
    else -> SimpleCondition(SimpleFilterType.COMPONENT_FILTER, this.toSummary())
}

private fun StringMatcher.valueOrEmpty(): String = when (this) {
    is StringMatcher.Equals -> value
    is StringMatcher.StartsWith -> value
    is StringMatcher.Contains -> value
    is StringMatcher.Pattern -> value
    is StringMatcher.Regex -> value
    is StringMatcher.IsNull -> ""
}

private fun SimpleCondition.toIfwFilter(): IfwFilter? {
    if (value.isBlank()) return null
    return when (filterType) {
        SimpleFilterType.COMPONENT_FILTER -> IfwFilter.ComponentFilter(value)
        SimpleFilterType.ACTION -> IfwFilter.Action(StringMatcher.Equals(value))
        SimpleFilterType.CATEGORY -> IfwFilter.Category(value)
        SimpleFilterType.SENDER_PACKAGE -> IfwFilter.SenderPackage(value)
        SimpleFilterType.COMPONENT -> IfwFilter.Component(StringMatcher.Equals(value))
        SimpleFilterType.COMPONENT_NAME -> IfwFilter.ComponentName(StringMatcher.Equals(value))
        SimpleFilterType.COMPONENT_PACKAGE -> IfwFilter.ComponentPackage(StringMatcher.Equals(value))
        SimpleFilterType.HOST -> IfwFilter.Host(StringMatcher.Equals(value))
        SimpleFilterType.SCHEME -> IfwFilter.Scheme(StringMatcher.Equals(value))
        SimpleFilterType.PATH -> IfwFilter.Path(StringMatcher.Equals(value))
        SimpleFilterType.DATA -> IfwFilter.Data(StringMatcher.Equals(value))
        SimpleFilterType.MIME_TYPE -> IfwFilter.MimeType(StringMatcher.Equals(value))
    }
}
