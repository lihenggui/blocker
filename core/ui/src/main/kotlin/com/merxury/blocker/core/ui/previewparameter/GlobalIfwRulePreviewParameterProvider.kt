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

package com.merxury.blocker.core.ui.previewparameter

import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import com.merxury.blocker.core.model.data.AdvancedGlobalIfwRuleDraft
import com.merxury.blocker.core.model.data.AdvancedRuleDetailPresentationUiState
import com.merxury.blocker.core.model.data.AdvancedRuleDetailUiState
import com.merxury.blocker.core.model.data.GlobalIfwRuleEditMode
import com.merxury.blocker.core.model.data.GlobalIfwRuleUiState
import com.merxury.blocker.core.model.data.PackageRuleGroup
import com.merxury.blocker.core.model.data.RuleItemPresentationUiState
import com.merxury.blocker.core.model.data.RuleItemUiState
import com.merxury.blocker.core.model.data.SimpleGlobalIfwRuleDraft
import com.merxury.blocker.core.model.data.SimpleRuleComponentUiState
import com.merxury.blocker.core.model.data.SimpleTargetMode
import com.merxury.core.ifw.editor.IfwEditorConditionKind
import com.merxury.core.ifw.editor.IfwEditorGroupMode
import com.merxury.core.ifw.editor.IfwEditorNode
import com.merxury.core.ifw.model.IfwComponentType
import com.merxury.core.ifw.model.IfwIntentFilter

class GlobalIfwRuleUiStatePreviewParameterProvider : PreviewParameterProvider<GlobalIfwRuleUiState> {
    override val values: Sequence<GlobalIfwRuleUiState> = sequenceOf(
        GlobalIfwRuleUiState.Success(groups = GlobalIfwRulePreviewParameterData.packageRuleGroups),
        GlobalIfwRuleUiState.Success(groups = emptyList()),
        GlobalIfwRuleUiState.Loading,
        GlobalIfwRuleUiState.Error(message = "Failed to load IFW rules from local storage."),
    )
}

class AdvancedRuleDetailPreviewParameterProvider : PreviewParameterProvider<AdvancedRuleDetailUiState> {
    override val values: Sequence<AdvancedRuleDetailUiState> = sequenceOf(
        GlobalIfwRulePreviewParameterData.advancedRuleDetail,
    )
}

class SimpleGlobalIfwRuleScreenPreviewParameterProvider : PreviewParameterProvider<SimpleGlobalIfwRuleScreenPreviewState> {
    override val values: Sequence<SimpleGlobalIfwRuleScreenPreviewState> = sequenceOf(
        SimpleGlobalIfwRuleScreenPreviewState(
            draft = SimpleGlobalIfwRuleDraft(),
        ),
        SimpleGlobalIfwRuleScreenPreviewState(
            draft = SimpleGlobalIfwRuleDraft(
                selectedPackageName = GlobalIfwRulePreviewParameterData.PREVIEW_SIMPLE_RULE_PACKAGE_NAME,
                componentType = IfwComponentType.BROADCAST,
                targetMode = SimpleTargetMode.MULTIPLE,
                targets = listOf(
                    GlobalIfwRulePreviewParameterData.PREVIEW_BOOT_RECEIVER_NAME,
                    GlobalIfwRulePreviewParameterData.PREVIEW_ALARM_RECEIVER_NAME,
                ),
                action = "android.intent.action.BOOT_COMPLETED",
                category = "android.intent.category.DEFAULT",
                callerPackage = "android",
            ),
            isDirty = true,
            selectedPackageLabel = "Spotify",
            visibleComponents = GlobalIfwRulePreviewParameterData.simpleRuleComponents(
                selectedTargets = setOf(
                    GlobalIfwRulePreviewParameterData.PREVIEW_BOOT_RECEIVER_NAME,
                    GlobalIfwRulePreviewParameterData.PREVIEW_ALARM_RECEIVER_NAME,
                ),
            ),
        ),
        SimpleGlobalIfwRuleScreenPreviewState(
            draft = SimpleGlobalIfwRuleDraft(
                selectedPackageName = GlobalIfwRulePreviewParameterData.PREVIEW_SIMPLE_RULE_PACKAGE_NAME,
                componentType = IfwComponentType.BROADCAST,
            ),
            selectedPackageLabel = "Spotify",
            isComponentLoading = true,
        ),
        SimpleGlobalIfwRuleScreenPreviewState(
            draft = SimpleGlobalIfwRuleDraft(
                selectedPackageName = GlobalIfwRulePreviewParameterData.PREVIEW_SIMPLE_RULE_PACKAGE_NAME,
                componentType = IfwComponentType.BROADCAST,
            ),
            selectedPackageLabel = "Spotify",
            componentQuery = "widget",
        ),
        SimpleGlobalIfwRuleScreenPreviewState(
            draft = SimpleGlobalIfwRuleDraft(
                selectedPackageName = GlobalIfwRulePreviewParameterData.PREVIEW_SIMPLE_RULE_PACKAGE_NAME,
                componentType = IfwComponentType.BROADCAST,
            ),
            selectedPackageLabel = "Spotify",
            componentLoadError = "Failed to query receivers for this package.",
        ),
    )
}

data class SimpleGlobalIfwRuleScreenPreviewState(
    val draft: SimpleGlobalIfwRuleDraft,
    val isDirty: Boolean = false,
    val selectedPackageLabel: String? = null,
    val componentQuery: String = "",
    val visibleComponents: List<SimpleRuleComponentUiState> = emptyList(),
    val isComponentLoading: Boolean = false,
    val componentLoadError: String? = null,
)

object GlobalIfwRulePreviewParameterData {
    const val PREVIEW_SIMPLE_RULE_PACKAGE_NAME = "com.spotify.music"
    const val PREVIEW_BOOT_RECEIVER_NAME = "com.spotify.music/.receiver.BootReceiver"
    const val PREVIEW_ALARM_RECEIVER_NAME = "com.spotify.music/.receiver.AlarmReceiver"

    private val listSimpleDraft = SimpleGlobalIfwRuleDraft(
        selectedPackageName = PREVIEW_SIMPLE_RULE_PACKAGE_NAME,
        componentType = IfwComponentType.BROADCAST,
        targets = listOf(PREVIEW_BOOT_RECEIVER_NAME),
        block = true,
        log = true,
        action = "android.intent.action.BOOT_COMPLETED",
    )

    private val listAdvancedDraft = AdvancedGlobalIfwRuleDraft(
        storagePackageName = PREVIEW_SIMPLE_RULE_PACKAGE_NAME,
        componentType = IfwComponentType.ACTIVITY,
        block = true,
        log = false,
        rootGroup = IfwEditorNode.Group(
            children = listOf(
                IfwEditorNode.Condition(
                    kind = IfwEditorConditionKind.COMPONENT_FILTER,
                    value = "com.spotify.music/.share.ShareActivity",
                ),
                IfwEditorNode.Condition(
                    kind = IfwEditorConditionKind.ACTION,
                    value = "android.intent.action.SEND",
                ),
            ),
        ),
    )

    private val detailDraft = listAdvancedDraft.copy(
        intentFilters = listOf(
            IfwIntentFilter(
                actions = listOf("android.intent.action.SEND"),
                categories = listOf("android.intent.category.DEFAULT"),
            ),
        ),
        editingRuleIndex = 1,
    )

    val advancedRuleDraft = AdvancedGlobalIfwRuleDraft(
        storagePackageName = PREVIEW_SIMPLE_RULE_PACKAGE_NAME,
        componentType = IfwComponentType.BROADCAST,
        block = true,
        log = false,
        rootGroup = IfwEditorNode.Group(
            children = listOf(
                IfwEditorNode.Condition(
                    kind = IfwEditorConditionKind.COMPONENT_FILTER,
                    value = PREVIEW_BOOT_RECEIVER_NAME,
                ),
                IfwEditorNode.Group(
                    mode = IfwEditorGroupMode.ANY,
                    children = listOf(
                        IfwEditorNode.Condition(
                            kind = IfwEditorConditionKind.ACTION,
                            value = "android.intent.action.BOOT_COMPLETED",
                        ),
                        IfwEditorNode.Condition(
                            kind = IfwEditorConditionKind.ACTION,
                            value = "android.intent.action.QUICKBOOT_POWERON",
                        ),
                    ),
                ),
            ),
        ),
    )

    val advancedRuleDetail = AdvancedRuleDetailUiState(
        storagePackageName = PREVIEW_SIMPLE_RULE_PACKAGE_NAME,
        componentType = IfwComponentType.ACTIVITY,
        block = true,
        log = false,
        filtersSummary = "ShareActivity\naction = android.intent.action.SEND",
        presentation = AdvancedRuleDetailPresentationUiState(
            title = "ShareActivity",
            targetPath = "com.spotify.music/.share.ShareActivity",
            conditionLines = listOf(
                "ShareActivity",
                "action = android.intent.action.SEND",
            ),
        ),
        ruleIndex = 1,
        draft = detailDraft,
    )

    val packageRuleGroups = listOf(
        PackageRuleGroup(
            packageName = PREVIEW_SIMPLE_RULE_PACKAGE_NAME,
            appLabel = "Spotify",
            packageInfo = null,
            rules = listOf(
                RuleItemUiState(
                    componentType = IfwComponentType.BROADCAST,
                    block = true,
                    log = true,
                    filtersSummary = "BootReceiver\naction = android.intent.action.BOOT_COMPLETED",
                    presentation = RuleItemPresentationUiState(
                        title = "BootReceiver",
                        targetPath = PREVIEW_BOOT_RECEIVER_NAME,
                        supportingText = "action = android.intent.action.BOOT_COMPLETED",
                    ),
                    editMode = GlobalIfwRuleEditMode.SIMPLE,
                    simpleDraft = listSimpleDraft,
                    advancedDraft = listAdvancedDraft,
                    ruleIndex = 0,
                ),
                RuleItemUiState(
                    componentType = IfwComponentType.ACTIVITY,
                    block = true,
                    log = false,
                    filtersSummary = advancedRuleDetail.filtersSummary,
                    presentation = RuleItemPresentationUiState(
                        title = "ShareActivity",
                        targetPath = "com.spotify.music/.share.ShareActivity",
                        supportingText = "action = android.intent.action.SEND",
                    ),
                    editMode = GlobalIfwRuleEditMode.ADVANCED,
                    simpleDraft = null,
                    advancedDraft = detailDraft,
                    ruleIndex = 1,
                ),
            ),
        ),
        PackageRuleGroup(
            packageName = "org.example.toolbox",
            appLabel = "Toolbox",
            packageInfo = null,
            rules = listOf(
                RuleItemUiState(
                    componentType = IfwComponentType.SERVICE,
                    block = false,
                    log = true,
                    filtersSummary = "UploadService",
                    presentation = RuleItemPresentationUiState(
                        title = "UploadService",
                        targetPath = "org.example.toolbox/.sync.UploadService",
                        supportingText = null,
                    ),
                    editMode = GlobalIfwRuleEditMode.SIMPLE,
                    simpleDraft = SimpleGlobalIfwRuleDraft(
                        selectedPackageName = "org.example.toolbox",
                        componentType = IfwComponentType.SERVICE,
                        targets = listOf("org.example.toolbox/.sync.UploadService"),
                        block = false,
                        log = true,
                    ),
                    advancedDraft = AdvancedGlobalIfwRuleDraft(
                        storagePackageName = "org.example.toolbox",
                        componentType = IfwComponentType.SERVICE,
                        block = false,
                        log = true,
                        rootGroup = IfwEditorNode.Group(
                            children = listOf(
                                IfwEditorNode.Condition(
                                    kind = IfwEditorConditionKind.COMPONENT_FILTER,
                                    value = "org.example.toolbox/.sync.UploadService",
                                ),
                            ),
                        ),
                    ),
                    ruleIndex = 0,
                ),
            ),
        ),
    )

    fun simpleRuleComponents(
        selectedTargets: Set<String> = emptySet(),
    ): List<SimpleRuleComponentUiState> = listOf(
        SimpleRuleComponentUiState(
            flattenedName = PREVIEW_BOOT_RECEIVER_NAME,
            componentName = "com.spotify.music.receiver.BootReceiver",
            simpleName = "BootReceiver",
            exported = false,
            selected = PREVIEW_BOOT_RECEIVER_NAME in selectedTargets,
        ),
        SimpleRuleComponentUiState(
            flattenedName = PREVIEW_ALARM_RECEIVER_NAME,
            componentName = "com.spotify.music.receiver.AlarmReceiver",
            simpleName = "AlarmReceiver",
            exported = false,
            selected = PREVIEW_ALARM_RECEIVER_NAME in selectedTargets,
        ),
        SimpleRuleComponentUiState(
            flattenedName = "com.spotify.music/.receiver.WidgetReceiver",
            componentName = "com.spotify.music.receiver.WidgetReceiver",
            simpleName = "WidgetReceiver",
            exported = true,
            selected = false,
        ),
    )
}
