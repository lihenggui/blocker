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
import com.merxury.blocker.core.data.device.ComponentStatusRepository
import com.merxury.blocker.core.data.device.SelectedUserRepository
import com.merxury.blocker.core.model.component.AppKey
import com.merxury.blocker.core.model.ifw.IfwFailure
import com.merxury.blocker.core.model.ifw.IfwResult
import com.merxury.blocker.core.model.system.ReadResult
import com.merxury.core.ifw.IfwStore
import com.merxury.core.ifw.editor.IfwEditorNode
import com.merxury.core.ifw.getOrThrow
import com.merxury.core.ifw.model.IfwComponentType
import com.merxury.core.ifw.model.IfwRule
import com.merxury.core.ifw.model.IfwRules
import com.merxury.core.ifw.withIfwEditorAccess
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class GlobalIfwRuleViewModel @Inject constructor(
    private val ifwStore: IfwStore,
    private val componentRepository: ComponentStatusRepository,
    selectedUsers: SelectedUserRepository,
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

    private val ownUser = selectedUsers.ownUser
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
            val app = AppKey(packageName, ownUser)
            if (componentRepository.refresh(app) is ReadResult.Failure) {
                Timber.w("Failed to refresh components for $packageName")
            }

            try {
                componentRepository.components(app).collect { components ->
                    val currentDraft = editorState.value.simpleDraft ?: return@collect
                    if (currentDraft.selectedPackageName != packageName || currentDraft.componentType != draft.componentType) {
                        return@collect
                    }

                    val availableComponents = components.asSequence()
                        .map { it.declaration }
                        .filter { it.type == componentType }
                        .sortedBy { it.id.simpleName.lowercase() }
                        .map { component ->
                            val flattenedName = flattenComponentName(component.id.packageName, component.id.className)
                            SimpleRuleComponentUiState(
                                flattenedName = flattenedName,
                                componentName = component.id.className,
                                simpleName = component.id.simpleName,
                                exported = component.exported,
                                selected = flattenedName in currentDraft.targets,
                            )
                        }.toList()

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
            } catch (e: CancellationException) {
                throw e
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
        } catch (e: CancellationException) {
            throw e
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
        } catch (e: CancellationException) {
            throw e
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

        // Check that the source can be read, then add the rule to the destination before removing
        // it from the source, so an unreadable file leaves the rule where it was instead of losing it.
        withIfwEditorAccess { ifwStore.rules(originalStoragePackageName).getOrThrow() }
        appendRule(destinationStoragePackageName, newRule)
        removeRule(originalStoragePackageName, editingRuleIndex)
    }

    private suspend fun appendRule(
        packageName: String,
        newRule: IfwRule,
    ) {
        withIfwEditorAccess {
            ifwStore.update(packageName) { IfwRules(it.rules + newRule) }.getOrThrow()
        }
    }

    private suspend fun replaceRule(
        packageName: String,
        ruleIndex: Int,
        newRule: IfwRule,
    ) {
        withIfwEditorAccess {
            ifwStore.update(packageName) { rules ->
                val updatedRules = rules.rules.toMutableList()
                if (ruleIndex in updatedRules.indices) updatedRules[ruleIndex] = newRule else updatedRules.add(newRule)
                IfwRules(updatedRules)
            }.getOrThrow()
        }
    }

    private suspend fun removeRule(
        packageName: String,
        ruleIndex: Int,
    ) {
        withIfwEditorAccess {
            ifwStore.update(packageName) { rules ->
                if (ruleIndex !in rules.rules.indices) rules else IfwRules(rules.rules.filterIndexed { index, _ -> index != ruleIndex })
            }.getOrThrow()
        }
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
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.e(e, "Failed to delete IFW rule for $packageName at index $ruleIndex")
        }
    }

    private suspend fun loadAllRules() {
        _uiState.value = GlobalIfwRuleUiState.Loading
        try {
            val result = withIfwEditorAccess { ifwStore.allRules() }
            val files = if (result == IfwResult.Failure(IfwFailure.NoAccess)) emptyMap() else result.getOrThrow()
            val groups = files.mapNotNull { (packageName, read) ->
                val ifwRules = (read as? IfwResult.Success)?.value ?: return@mapNotNull null
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
        } catch (e: CancellationException) {
            throw e
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
