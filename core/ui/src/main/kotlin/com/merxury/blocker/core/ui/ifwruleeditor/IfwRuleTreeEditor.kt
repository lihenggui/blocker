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

@file:Suppress("ktlint:compose:multiple-emitters-check")

package com.merxury.blocker.core.ui.ifwruleeditor

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.merxury.blocker.core.designsystem.component.PreviewThemes
import com.merxury.blocker.core.designsystem.theme.BlockerTheme
import com.merxury.core.ifw.editor.IfwEditorConditionKind
import com.merxury.core.ifw.editor.IfwEditorGroupMode
import com.merxury.core.ifw.editor.IfwEditorNode
import com.merxury.core.ifw.editor.addNode
import com.merxury.core.ifw.editor.newIfwEditorId
import com.merxury.core.ifw.editor.removeNode
import com.merxury.core.ifw.editor.updateNode

@Composable
fun IfwRuleTreeEditor(
    rootGroup: IfwEditorNode.Group,
    onChange: (IfwEditorNode.Group) -> Unit,
    modifier: Modifier = Modifier,
    rootValidationMessage: String? = null,
) {
    var pickerTargetGroupId by remember { mutableStateOf<String?>(null) }

    Column(modifier = modifier) {
        GroupEditorCard(
            group = rootGroup,
            depth = 0,
            isRoot = true,
            validationMessage = rootValidationMessage,
            onUpdate = { updated -> onChange(rootGroup.updateNode(updated)) },
            onDelete = { nodeId -> onChange(rootGroup.removeNode(nodeId)) },
            onAddGroup = { groupId ->
                onChange(
                    rootGroup.addNode(
                        targetGroupId = groupId,
                        node = IfwEditorNode.Group(id = newIfwEditorId()),
                    ),
                )
            },
            onAddCondition = { groupId -> pickerTargetGroupId = groupId },
        )
    }

    if (pickerTargetGroupId != null) {
        ConditionPickerSheet(
            onDismiss = { pickerTargetGroupId = null },
            onSelect = { kind ->
                val targetId = pickerTargetGroupId ?: return@ConditionPickerSheet
                onChange(
                    rootGroup.addNode(
                        targetGroupId = targetId,
                        node = IfwEditorNode.Condition(
                            id = newIfwEditorId(),
                            kind = kind,
                        ),
                    ),
                )
                pickerTargetGroupId = null
            },
        )
    }
}

@Composable
private fun IfwRuleTreeEditorPreview(
    initialRootGroup: IfwEditorNode.Group,
    validationMessage: String? = null,
) {
    var previewRootGroup by remember { mutableStateOf(initialRootGroup) }

    BlockerTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
            ) {
                IfwRuleTreeEditor(
                    rootGroup = previewRootGroup,
                    onChange = { updated -> previewRootGroup = updated },
                    rootValidationMessage = validationMessage,
                )
            }
        }
    }
}

@Composable
@PreviewThemes
private fun IfwRuleTreeEditorValidPreview() {
    IfwRuleTreeEditorPreview(
        initialRootGroup = IfwEditorNode.Group(
            children = listOf(
                IfwEditorNode.Condition(
                    kind = IfwEditorConditionKind.COMPONENT_FILTER,
                    value = "com.spotify.music/.receiver.BootReceiver",
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
}

@Composable
@PreviewThemes
private fun IfwRuleTreeEditorSelectorRequiredPreview() {
    IfwRuleTreeEditorPreview(
        initialRootGroup = IfwEditorNode.Group(
            children = listOf(
                IfwEditorNode.Condition(
                    kind = IfwEditorConditionKind.ACTION,
                    value = "android.intent.action.VIEW",
                ),
            ),
        ),
        validationMessage = "Add at least one Exact target component condition.",
    )
}
