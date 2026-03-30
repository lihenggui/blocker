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

import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.merxury.blocker.core.data.respository.component.ComponentRepository
import com.merxury.blocker.core.model.data.AdvancedGlobalIfwRuleDraft
import com.merxury.blocker.core.model.data.GlobalIfwRuleEditMode
import com.merxury.blocker.core.model.data.GlobalIfwRuleEditorUiState
import com.merxury.blocker.core.model.data.GlobalIfwRuleScreenState
import com.merxury.blocker.core.model.data.GlobalIfwRuleUiState
import com.merxury.blocker.core.model.data.PackageRuleGroup
import com.merxury.blocker.core.model.data.RuleItemUiState
import com.merxury.blocker.core.model.data.SimpleGlobalIfwRuleDraft
import com.merxury.blocker.core.model.data.SimpleRuleComponentUiState
import com.merxury.blocker.core.model.data.SimpleTargetMode
import com.merxury.blocker.feature.globalifwrule.impl.components.flattenComponentName
import com.merxury.blocker.feature.globalifwrule.impl.components.toComponentType
import com.merxury.blocker.feature.globalifwrule.impl.components.toDetailUiState
import com.merxury.blocker.feature.globalifwrule.impl.components.toIfwRule
import com.merxury.blocker.feature.globalifwrule.impl.components.toIfwRuleOrNull
import com.merxury.blocker.feature.globalifwrule.impl.components.toRuleItemUiState
import com.merxury.core.ifw.IIntentFirewall
import com.merxury.core.ifw.editor.IfwEditorNode
import com.merxury.core.ifw.model.IfwComponentType
import com.merxury.core.ifw.model.IfwRule
import com.merxury.core.ifw.model.IfwRules
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class GlobalIfwRuleViewModel @Inject constructor(
    private val intentFirewall: IIntentFirewall,
    private val componentRepository: ComponentRepository,
    private val packageManager: PackageManager,
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {
    private companion object {
        const val EDITOR_SCREEN_KEY = "editor_screen"
        const val EDITING_PACKAGE_NAME_KEY = "editing_package_name"
        const val EDITING_RULE_INDEX_KEY = "editing_rule_index"
    }

    private val _uiState = MutableStateFlow<GlobalIfwRuleUiState>(GlobalIfwRuleUiState.Loading)
    val uiState: StateFlow<GlobalIfwRuleUiState> = _uiState.asStateFlow()

    private val _editorState = MutableStateFlow(GlobalIfwRuleEditorUiState())
    val editorState: StateFlow<GlobalIfwRuleEditorUiState> = _editorState.asStateFlow()

    private var componentsJob: Job? = null

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            loadAllRules()
        }
    }

    fun startAddingSimpleRule() {
        openSimpleDraft(
            draft = SimpleGlobalIfwRuleDraft(),
            saveEditingIdentity = false,
        )
    }

    fun startAddingAdvancedRule() {
        openAdvancedDraft(
            draft = AdvancedGlobalIfwRuleDraft(),
            saveEditingIdentity = false,
        )
    }

    fun openRule(packageName: String, ruleIndex: Int) {
        val state = _uiState.value as? GlobalIfwRuleUiState.Success ?: return
        val rule = state.groups.findRuleItem(packageName, ruleIndex) ?: return
        when (rule.editMode) {
            GlobalIfwRuleEditMode.SIMPLE -> {
                val draft = rule.simpleDraft ?: return
                openSimpleDraft(draft)
            }

            GlobalIfwRuleEditMode.ADVANCED -> {
                openAdvancedDetail(rule.advancedDraft)
            }
        }
    }

    fun copyAdvancedRule() {
        val detail = editorState.value.detail ?: return
        openAdvancedDraft(
            draft = detail.draft.copy(
                originStoragePackageName = null,
                editingRuleIndex = null,
            ),
            saveEditingIdentity = false,
        )
    }

    fun dismissEditor() {
        clearEditorIdentity()
        componentsJob?.cancel()
        _editorState.value = GlobalIfwRuleEditorUiState()
    }

    fun updateSimplePackageName(packageName: String) {
        updateSimpleDraft(resetComponentState = true) {
            copy(
                selectedPackageName = packageName.trim(),
                targets = emptyList(),
            )
        }
        observeSimpleComponents()
    }

    fun updateSimpleComponentType(componentType: IfwComponentType) {
        updateSimpleDraft(resetComponentState = true) {
            copy(
                componentType = componentType,
                targets = emptyList(),
            )
        }
        observeSimpleComponents()
    }

    fun updateSimpleTargetMode(targetMode: SimpleTargetMode) {
        updateSimpleDraft {
            copy(
                targetMode = targetMode,
                targets = if (targetMode == SimpleTargetMode.SINGLE) targets.take(1) else targets,
            )
        }
    }

    fun updateSimpleBlock(block: Boolean) = updateSimpleDraft { copy(block = block) }

    fun updateSimpleLog(log: Boolean) = updateSimpleDraft { copy(log = log) }

    fun updateSimpleAction(action: String) = updateSimpleDraft { copy(action = action) }

    fun updateSimpleCategory(category: String) = updateSimpleDraft { copy(category = category) }

    fun updateSimpleCallerPackage(callerPackage: String) = updateSimpleDraft { copy(callerPackage = callerPackage) }

    fun updateComponentQuery(query: String) {
        _editorState.value = _editorState.value.copy(componentQuery = query)
    }

    fun selectSingleTarget(flattenedName: String) {
        updateSimpleDraft {
            copy(targets = listOf(flattenedName))
        }
    }

    fun toggleMultiTarget(flattenedName: String) {
        updateSimpleDraft {
            copy(
                targets = if (flattenedName in targets) {
                    targets - flattenedName
                } else {
                    targets + flattenedName
                }.sorted(),
            )
        }
    }

    fun updateAdvancedPackageName(packageName: String) = updateAdvancedDraft {
        copy(storagePackageName = packageName.trim())
    }

    fun updateAdvancedComponentType(componentType: IfwComponentType) = updateAdvancedDraft {
        copy(componentType = componentType)
    }

    fun updateAdvancedBlock(block: Boolean) = updateAdvancedDraft { copy(block = block) }

    fun updateAdvancedLog(log: Boolean) = updateAdvancedDraft { copy(log = log) }

    fun updateAdvancedRootGroup(rootGroup: IfwEditorNode.Group) = updateAdvancedDraft {
        copy(rootGroup = rootGroup)
    }

    fun saveRule() {
        when (val draft = editorState.value.draft) {
            is SimpleGlobalIfwRuleDraft -> viewModelScope.launch {
                saveSimpleRule(draft)
            }
            is AdvancedGlobalIfwRuleDraft -> viewModelScope.launch {
                saveAdvancedRule(draft)
            }
            null -> Unit
        }
    }

    fun deleteRule(packageName: String, ruleIndex: Int) {
        viewModelScope.launch {
            deleteRuleInternal(
                packageName = packageName,
                ruleIndex = ruleIndex,
                dismissAfterDelete = false,
            )
        }
    }

    fun deleteViewedRule() {
        val detail = editorState.value.detail ?: return
        viewModelScope.launch {
            deleteRuleInternal(
                packageName = detail.storagePackageName,
                ruleIndex = detail.ruleIndex,
                dismissAfterDelete = true,
            )
        }
    }

    private fun openSimpleDraft(
        draft: SimpleGlobalIfwRuleDraft,
        saveEditingIdentity: Boolean = true,
    ) {
        persistEditorIdentity(
            screen = GlobalIfwRuleScreenState.SIMPLE_EDIT,
            packageName = if (saveEditingIdentity) draft.originStoragePackageName else null,
            ruleIndex = if (saveEditingIdentity) draft.editingRuleIndex else null,
        )
        _editorState.value = GlobalIfwRuleEditorUiState(
            screen = GlobalIfwRuleScreenState.SIMPLE_EDIT,
            simpleDraft = draft,
            selectedPackageLabel = resolveAppLabel(draft.selectedPackageName),
        )
        observeSimpleComponents()
    }

    private fun openAdvancedDraft(
        draft: AdvancedGlobalIfwRuleDraft,
        saveEditingIdentity: Boolean = true,
    ) {
        persistEditorIdentity(
            screen = GlobalIfwRuleScreenState.ADVANCED_EDIT,
            packageName = if (saveEditingIdentity) draft.originStoragePackageName else null,
            ruleIndex = if (saveEditingIdentity) draft.editingRuleIndex else null,
        )
        componentsJob?.cancel()
        _editorState.value = GlobalIfwRuleEditorUiState(
            screen = GlobalIfwRuleScreenState.ADVANCED_EDIT,
            advancedDraft = draft,
            isDirty = false,
        )
    }

    private fun openAdvancedDetail(draft: AdvancedGlobalIfwRuleDraft) {
        persistEditorIdentity(
            screen = GlobalIfwRuleScreenState.ADVANCED_DETAIL,
            packageName = draft.originStoragePackageName,
            ruleIndex = draft.editingRuleIndex,
        )
        componentsJob?.cancel()
        _editorState.value = GlobalIfwRuleEditorUiState(
            screen = GlobalIfwRuleScreenState.ADVANCED_DETAIL,
            detail = draft.toDetailUiState(),
        )
    }

    private fun updateSimpleDraft(
        resetComponentState: Boolean = false,
        markDirty: Boolean = true,
        transform: SimpleGlobalIfwRuleDraft.() -> SimpleGlobalIfwRuleDraft,
    ) {
        val current = editorState.value
        val draft = current.simpleDraft ?: return
        val updatedDraft = draft.transform()
        _editorState.value = current.copy(
            simpleDraft = updatedDraft,
            isDirty = current.isDirty || markDirty,
            selectedPackageLabel = if (resetComponentState) {
                resolveAppLabel(updatedDraft.selectedPackageName)
            } else {
                current.selectedPackageLabel
            },
            availableComponents = if (resetComponentState) emptyList() else current.availableComponents,
            componentQuery = if (resetComponentState) "" else current.componentQuery,
            isComponentLoading = if (resetComponentState) false else current.isComponentLoading,
            componentLoadError = if (resetComponentState) null else current.componentLoadError,
        )
    }

    private fun updateAdvancedDraft(
        markDirty: Boolean = true,
        transform: AdvancedGlobalIfwRuleDraft.() -> AdvancedGlobalIfwRuleDraft,
    ) {
        val current = editorState.value
        val draft = current.advancedDraft ?: return
        _editorState.value = current.copy(
            advancedDraft = draft.transform(),
            isDirty = current.isDirty || markDirty,
        )
    }

    private fun observeSimpleComponents() {
        componentsJob?.cancel()
        val draft = editorState.value.simpleDraft ?: return
        if (draft.selectedPackageName.isBlank()) {
            _editorState.value = _editorState.value.copy(
                selectedPackageLabel = null,
                availableComponents = emptyList(),
                isComponentLoading = false,
                componentLoadError = null,
            )
            return
        }

        val packageName = draft.selectedPackageName
        val componentType = draft.componentType.toComponentType()
        componentsJob = viewModelScope.launch {
            _editorState.value = _editorState.value.copy(
                selectedPackageLabel = resolveAppLabel(packageName),
                isComponentLoading = true,
                componentLoadError = null,
                availableComponents = emptyList(),
            )
            try {
                componentRepository.updateComponentList(packageName, componentType).first()
            } catch (e: Exception) {
                Timber.w(e, "Failed to refresh components for $packageName")
            }

            try {
                componentRepository.getComponentList(packageName, componentType).collect { components ->
                    val currentDraft = editorState.value.simpleDraft ?: return@collect
                    if (currentDraft.selectedPackageName != packageName || currentDraft.componentType != draft.componentType) {
                        return@collect
                    }

                    val availableComponents = components
                        .sortedBy { component -> component.simpleName.lowercase() }
                        .map { component ->
                            val flattenedName =
                                flattenComponentName(component.packageName, component.name)
                            SimpleRuleComponentUiState(
                                flattenedName = flattenedName,
                                componentName = component.name,
                                simpleName = component.simpleName,
                                exported = component.exported,
                                selected = flattenedName in currentDraft.targets,
                            )
                        }

                    val validTargets = currentDraft.targets.filter { target ->
                        availableComponents.any { component -> component.flattenedName == target }
                    }

                    _editorState.value = _editorState.value.copy(
                        selectedPackageLabel = resolveAppLabel(packageName),
                        availableComponents = availableComponents.map { component ->
                            component.copy(selected = component.flattenedName in validTargets)
                        },
                        isComponentLoading = false,
                        componentLoadError = null,
                    )

                    if (validTargets != currentDraft.targets) {
                        updateSimpleDraft(markDirty = false) { copy(targets = validTargets) }
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to observe components for $packageName")
                _editorState.value = _editorState.value.copy(
                    selectedPackageLabel = resolveAppLabel(packageName),
                    availableComponents = emptyList(),
                    isComponentLoading = false,
                    componentLoadError = e.message ?: "Unknown error",
                )
            }
        }
    }

    private suspend fun saveSimpleRule(draft: SimpleGlobalIfwRuleDraft) {
        if (!draft.canSave) return
        try {
            persistRule(
                originalStoragePackageName = draft.originStoragePackageName,
                editingRuleIndex = draft.editingRuleIndex,
                destinationStoragePackageName = draft.storagePackageName,
                newRule = draft.toIfwRule(),
            )
            dismissEditor()
            loadAllRules()
        } catch (e: Exception) {
            Timber.e(e, "Failed to save simple IFW rule for ${draft.storagePackageName}")
        }
    }

    private suspend fun saveAdvancedRule(draft: AdvancedGlobalIfwRuleDraft) {
        if (!draft.canSave) return
        try {
            val newRule = draft.toIfwRuleOrNull() ?: return
            persistRule(
                originalStoragePackageName = draft.originStoragePackageName,
                editingRuleIndex = draft.editingRuleIndex,
                destinationStoragePackageName = draft.storagePackageName,
                newRule = newRule,
            )
            dismissEditor()
            loadAllRules()
        } catch (e: Exception) {
            Timber.e(e, "Failed to save advanced IFW rule for ${draft.storagePackageName}")
        }
    }

    private suspend fun persistRule(
        originalStoragePackageName: String?,
        editingRuleIndex: Int?,
        destinationStoragePackageName: String,
        newRule: IfwRule,
    ) {
        if (editingRuleIndex == null || originalStoragePackageName == null) {
            appendRule(destinationStoragePackageName, newRule)
            return
        }

        if (originalStoragePackageName == destinationStoragePackageName) {
            replaceRule(destinationStoragePackageName, editingRuleIndex, newRule)
            return
        }

        removeRule(originalStoragePackageName, editingRuleIndex)
        appendRule(destinationStoragePackageName, newRule)
    }

    private suspend fun appendRule(
        packageName: String,
        newRule: IfwRule,
    ) {
        val currentRules = intentFirewall.getRules(packageName)
        intentFirewall.saveRules(
            packageName,
            IfwRules(currentRules.rules + newRule),
        )
    }

    private suspend fun replaceRule(
        packageName: String,
        ruleIndex: Int,
        newRule: IfwRule,
    ) {
        val currentRules = intentFirewall.getRules(packageName)
        val updatedRules = currentRules.rules.toMutableList()
        if (ruleIndex !in updatedRules.indices) {
            appendRule(packageName, newRule)
            return
        }
        updatedRules[ruleIndex] = newRule
        intentFirewall.saveRules(packageName, IfwRules(updatedRules))
    }

    private suspend fun removeRule(
        packageName: String,
        ruleIndex: Int,
    ) {
        val currentRules = intentFirewall.getRules(packageName)
        val updatedRules = currentRules.rules.toMutableList()
        if (ruleIndex !in updatedRules.indices) return
        updatedRules.removeAt(ruleIndex)
        intentFirewall.saveRules(packageName, IfwRules(updatedRules))
    }

    private suspend fun deleteRuleInternal(
        packageName: String,
        ruleIndex: Int,
        dismissAfterDelete: Boolean,
    ) {
        try {
            removeRule(packageName, ruleIndex)
            if (dismissAfterDelete) {
                dismissEditor()
            }
            loadAllRules()
        } catch (e: Exception) {
            Timber.e(e, "Failed to delete IFW rule for $packageName at index $ruleIndex")
        }
    }

    private suspend fun loadAllRules() {
        _uiState.value = GlobalIfwRuleUiState.Loading
        try {
            intentFirewall.resetCache()
            val groups = intentFirewall.getAllRules()
                .mapNotNull { (packageName, ifwRules) ->
                    if (ifwRules.isEmpty()) return@mapNotNull null
                    PackageRuleGroup(
                        packageName = packageName,
                        appLabel = resolveAppLabel(packageName),
                        packageInfo = resolvePackageInfo(packageName),
                        rules = ifwRules.rules.mapIndexed { index, rule ->
                            rule.toRuleItemUiState(index, packageName)
                        },
                    )
                }
                .sortedBy { group -> group.appLabel ?: group.packageName }
            _uiState.value = GlobalIfwRuleUiState.Success(groups)
            restoreEditorState(groups)
        } catch (e: Exception) {
            Timber.e(e, "Failed to load all IFW rules")
            _uiState.value = GlobalIfwRuleUiState.Error(e.message ?: "Unknown error")
        }
    }

    private fun restoreEditorState(groups: List<PackageRuleGroup>) {
        val savedScreen = savedStateHandle.get<String>(EDITOR_SCREEN_KEY)
            ?.let { screenName -> GlobalIfwRuleScreenState.entries.find { it.name == screenName } }
            ?: return

        when (savedScreen) {
            GlobalIfwRuleScreenState.LIST -> {
                _editorState.value = GlobalIfwRuleEditorUiState()
            }

            GlobalIfwRuleScreenState.SIMPLE_EDIT -> {
                val editingPackageName = savedStateHandle.get<String>(EDITING_PACKAGE_NAME_KEY)
                val editingRuleIndex = savedStateHandle.get<Int>(EDITING_RULE_INDEX_KEY)
                if (editingPackageName != null && editingRuleIndex != null) {
                    val rule = groups.findRuleItem(editingPackageName, editingRuleIndex)
                    val draft = rule?.simpleDraft ?: return dismissEditor()
                    openSimpleDraft(draft)
                } else {
                    val draft = editorState.value.simpleDraft ?: return
                    _editorState.value = editorState.value.copy(
                        selectedPackageLabel = resolveAppLabel(draft.selectedPackageName),
                    )
                    observeSimpleComponents()
                }
            }

            GlobalIfwRuleScreenState.ADVANCED_EDIT -> {
                val editingPackageName = savedStateHandle.get<String>(EDITING_PACKAGE_NAME_KEY)
                val editingRuleIndex = savedStateHandle.get<Int>(EDITING_RULE_INDEX_KEY)
                if (editingPackageName != null && editingRuleIndex != null) {
                    val rule = groups.findRuleItem(editingPackageName, editingRuleIndex)
                    val draft = rule?.advancedDraft ?: return dismissEditor()
                    openAdvancedDraft(draft)
                }
            }

            GlobalIfwRuleScreenState.ADVANCED_DETAIL -> {
                val editingPackageName = savedStateHandle.get<String>(EDITING_PACKAGE_NAME_KEY)
                val editingRuleIndex = savedStateHandle.get<Int>(EDITING_RULE_INDEX_KEY)
                if (editingPackageName == null || editingRuleIndex == null) {
                    dismissEditor()
                    return
                }
                val rule = groups.findRuleItem(editingPackageName, editingRuleIndex) ?: return dismissEditor()
                openAdvancedDetail(rule.advancedDraft)
            }
        }
    }

    private fun persistEditorIdentity(
        screen: GlobalIfwRuleScreenState,
        packageName: String?,
        ruleIndex: Int?,
    ) {
        savedStateHandle[EDITOR_SCREEN_KEY] = screen.name
        savedStateHandle[EDITING_PACKAGE_NAME_KEY] = packageName
        savedStateHandle[EDITING_RULE_INDEX_KEY] = ruleIndex
    }

    private fun clearEditorIdentity() {
        savedStateHandle[EDITOR_SCREEN_KEY] = GlobalIfwRuleScreenState.LIST.name
        savedStateHandle[EDITING_PACKAGE_NAME_KEY] = null
        savedStateHandle[EDITING_RULE_INDEX_KEY] = null
    }

    private fun resolveAppLabel(packageName: String): String? = try {
        val appInfo = packageManager.getApplicationInfo(packageName, 0)
        packageManager.getApplicationLabel(appInfo).toString()
    } catch (_: Exception) {
        null
    }

    private fun resolvePackageInfo(packageName: String): PackageInfo? = try {
        packageManager.getPackageInfo(packageName, 0)
    } catch (_: Exception) {
        null
    }

    private fun List<PackageRuleGroup>.findRuleItem(
        packageName: String,
        ruleIndex: Int,
    ): RuleItemUiState? = find { group -> group.packageName == packageName }
        ?.rules
        ?.find { rule -> rule.ruleIndex == ruleIndex }
}
