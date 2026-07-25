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

package com.merxury.blocker.feature.ifwrule.impl.previewparameter

import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import com.merxury.blocker.core.ui.ifwruleeditor.BlockMode
import com.merxury.blocker.core.ui.ifwruleeditor.RuleEditorUiState
import com.merxury.blocker.feature.ifwrule.impl.RuleEditorScreenUiState
import com.merxury.core.ifw.editor.IfwEditorConditionKind
import com.merxury.core.ifw.editor.IfwEditorGroupMode
import com.merxury.core.ifw.editor.IfwEditorNode
import com.merxury.core.ifw.editor.IfwEditorStringMatcherMode
import com.merxury.core.ifw.model.IfwComponentType
import com.merxury.core.ifw.model.SenderType

class IfwRuleEditorScreenPreviewParameterProvider : PreviewParameterProvider<RuleEditorScreenUiState> {
    override val values: Sequence<RuleEditorScreenUiState> = sequenceOf(
        RuleEditorScreenUiState.Success(
            editor = IfwRuleEditorScreenPreviewParameterData.blockAllEditor,
        ),
        RuleEditorScreenUiState.Success(
            editor = IfwRuleEditorScreenPreviewParameterData.conditionalEditor,
        ),
        RuleEditorScreenUiState.Success(
            editor = IfwRuleEditorScreenPreviewParameterData.advancedEditor,
        ),
        RuleEditorScreenUiState.Success(
            editor = IfwRuleEditorScreenPreviewParameterData.conditionalEditor,
            hasUnsavedChanges = true,
        ),
        RuleEditorScreenUiState.Loading,
        RuleEditorScreenUiState.Error("Failed to load IFW rules"),
    )
}

private object IfwRuleEditorScreenPreviewParameterData {
    val blockAllEditor = RuleEditorUiState(
        packageName = "com.example.player",
        componentName = "com.example.player.StartupReceiver",
        componentType = IfwComponentType.BROADCAST,
        blockMode = BlockMode.ALL,
        log = true,
        blockEnabled = true,
    )

    val conditionalEditor = RuleEditorUiState(
        packageName = "com.example.social",
        componentName = "com.example.social.ShareReceiver",
        componentType = IfwComponentType.BROADCAST,
        blockMode = BlockMode.CONDITIONAL,
        log = false,
        blockEnabled = false,
        rootGroup = IfwEditorNode.Group(
            mode = IfwEditorGroupMode.ALL,
            children = listOf(
                IfwEditorNode.Condition(
                    kind = IfwEditorConditionKind.ACTION,
                    matcherMode = IfwEditorStringMatcherMode.EXACT,
                    value = "android.intent.action.SEND",
                ),
                IfwEditorNode.Group(
                    mode = IfwEditorGroupMode.ANY,
                    children = listOf(
                        IfwEditorNode.Condition(
                            kind = IfwEditorConditionKind.CALLER_TYPE,
                            senderType = SenderType.SYSTEM,
                            excluded = true,
                        ),
                        IfwEditorNode.Condition(
                            kind = IfwEditorConditionKind.HOST,
                            matcherMode = IfwEditorStringMatcherMode.CONTAINS,
                            value = "example.com",
                        ),
                    ),
                ),
            ),
        ),
    )

    val advancedEditor = RuleEditorUiState(
        packageName = "com.example.music",
        componentName = "com.example.music.DeepLinkActivity",
        componentType = IfwComponentType.ACTIVITY,
        isAdvancedRule = true,
    )
}
