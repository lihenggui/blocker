/*
 * Copyright 2026 Blocker
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

package com.merxury.blocker.feature.globalifwrule.impl.model

import android.content.pm.PackageInfo
import com.merxury.core.ifw.editor.IfwEditorNode
import com.merxury.core.ifw.editor.hasTopLevelComponentFilter
import com.merxury.core.ifw.model.IfwComponentType
import com.merxury.core.ifw.model.IfwIntentFilter

enum class SimpleTargetMode {
    SINGLE,
    MULTIPLE,
}

sealed interface GlobalIfwRuleDraft {
    val editingRuleIndex: Int?
    val canSave: Boolean
}

data class SimpleGlobalIfwRuleDraft(
    val originStoragePackageName: String? = null,
    val selectedPackageName: String = "",
    val componentType: IfwComponentType = IfwComponentType.BROADCAST,
    val targetMode: SimpleTargetMode = SimpleTargetMode.SINGLE,
    val targets: List<String> = emptyList(),
    val block: Boolean = true,
    val log: Boolean = true,
    val action: String = "",
    val category: String = "",
    val callerPackage: String = "",
    override val editingRuleIndex: Int? = null,
) : GlobalIfwRuleDraft {
    val storagePackageName: String
        get() = selectedPackageName.trim()

    override val canSave: Boolean
        get() = storagePackageName.isNotBlank() && targets.isNotEmpty()
}

data class AdvancedGlobalIfwRuleDraft(
    val originStoragePackageName: String? = null,
    val storagePackageName: String = "",
    val componentType: IfwComponentType = IfwComponentType.BROADCAST,
    val block: Boolean = true,
    val log: Boolean = true,
    val intentFilters: List<IfwIntentFilter> = emptyList(),
    val rootGroup: IfwEditorNode.Group = IfwEditorNode.Group(),
    override val editingRuleIndex: Int? = null,
) : GlobalIfwRuleDraft {
    val hasReadOnlyIntentFilters: Boolean
        get() = intentFilters.isNotEmpty()

    override val canSave: Boolean
        get() = storagePackageName.isNotBlank() &&
            (hasReadOnlyIntentFilters || rootGroup.hasTopLevelComponentFilter())
}

sealed interface GlobalIfwRuleUiState {
    data object Loading : GlobalIfwRuleUiState
    data class Success(val groups: List<PackageRuleGroup>) : GlobalIfwRuleUiState
    data class Error(val message: String) : GlobalIfwRuleUiState
}

data class PackageRuleGroup(
    val packageName: String,
    val appLabel: String?,
    val packageInfo: PackageInfo?,
    val rules: List<RuleItemUiState>,
)

data class RuleItemUiState(
    val componentType: IfwComponentType,
    val block: Boolean,
    val log: Boolean,
    val filtersSummary: String,
    val presentation: RuleItemPresentationUiState,
    val editMode: GlobalIfwRuleEditMode,
    val simpleDraft: SimpleGlobalIfwRuleDraft?,
    val advancedDraft: AdvancedGlobalIfwRuleDraft,
    val ruleIndex: Int,
)

data class RuleItemPresentationUiState(
    val title: String?,
    val targetPath: String?,
    val supportingText: String?,
)

data class GlobalIfwRuleEditorUiState(
    val screen: GlobalIfwRuleScreenState = GlobalIfwRuleScreenState.LIST,
    val simpleDraft: SimpleGlobalIfwRuleDraft? = null,
    val advancedDraft: AdvancedGlobalIfwRuleDraft? = null,
    val detail: AdvancedRuleDetailUiState? = null,
    val isDirty: Boolean = false,
    val selectedPackageLabel: String? = null,
    val availableComponents: List<SimpleRuleComponentUiState> = emptyList(),
    val componentQuery: String = "",
    val isComponentLoading: Boolean = false,
    val componentLoadError: String? = null,
) {
    val draft: GlobalIfwRuleDraft?
        get() = simpleDraft ?: advancedDraft

    val visibleComponents: List<SimpleRuleComponentUiState>
        get() {
            if (componentQuery.isBlank()) return availableComponents
            return availableComponents.filter { component ->
                component.componentName.contains(componentQuery, ignoreCase = true) ||
                    component.simpleName.contains(componentQuery, ignoreCase = true)
            }
        }
}

enum class GlobalIfwRuleScreenState {
    LIST,
    SIMPLE_EDIT,
    ADVANCED_EDIT,
    ADVANCED_DETAIL,
}

enum class GlobalIfwRuleEditMode {
    SIMPLE,
    ADVANCED,
}

data class AdvancedRuleDetailUiState(
    val storagePackageName: String,
    val componentType: IfwComponentType,
    val block: Boolean,
    val log: Boolean,
    val filtersSummary: String,
    val presentation: AdvancedRuleDetailPresentationUiState,
    val ruleIndex: Int,
    val draft: AdvancedGlobalIfwRuleDraft,
)

data class AdvancedRuleDetailPresentationUiState(
    val title: String?,
    val targetPath: String?,
    val conditionLines: List<String>,
)

data class SimpleRuleComponentUiState(
    val flattenedName: String,
    val componentName: String,
    val simpleName: String,
    val exported: Boolean,
    val selected: Boolean,
)
