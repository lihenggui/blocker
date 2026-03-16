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
) : ViewModel() {

    private val _uiState = MutableStateFlow<GlobalIfwRuleUiState>(GlobalIfwRuleUiState.Loading)
    val uiState: StateFlow<GlobalIfwRuleUiState> = _uiState.asStateFlow()

    init {
        loadAllRules()
    }

    fun refresh() {
        loadAllRules()
    }

    fun saveNewRule(data: AddRuleData) {
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
                loadAllRules()
            } catch (e: Exception) {
                Timber.e(e, "Failed to save new rule for ${data.packageName}")
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
                val allRules = intentFirewall.getAllRules()
                val groups = allRules.mapNotNull { (packageName, ifwRules) ->
                    if (ifwRules.isEmpty()) return@mapNotNull null
                    val appLabel = resolveAppLabel(packageName)
                    val ruleItems = ifwRules.rules.mapIndexed { index, rule ->
                        rule.toRuleItemUiState(index)
                    }
                    PackageRuleGroup(
                        packageName = packageName,
                        appLabel = appLabel,
                        rules = ruleItems,
                    )
                }.sortedBy { it.appLabel ?: it.packageName }
                _uiState.value = GlobalIfwRuleUiState.Success(groups)
            } catch (e: Exception) {
                Timber.e(e, "Failed to load all IFW rules")
                _uiState.value = GlobalIfwRuleUiState.Error(e.message ?: "Unknown error")
            }
        }
    }

    private fun resolveAppLabel(packageName: String): String? = try {
        val appInfo = packageManager.getApplicationInfo(packageName, 0)
        packageManager.getApplicationLabel(appInfo).toString()
    } catch (e: PackageManager.NameNotFoundException) {
        null
    }

    private fun IfwRule.toRuleItemUiState(index: Int): RuleItemUiState {
        val filtersSummary = filters.joinToString("; ") { it.toSummary() }
        return RuleItemUiState(
            componentType = componentType,
            block = block,
            log = log,
            filtersSummary = filtersSummary,
            ruleIndex = index,
        )
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
    val ruleIndex: Int,
)

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
