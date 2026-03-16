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

package com.merxury.blocker.core.ui.ifwruleeditor

import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.merxury.blocker.core.designsystem.icon.BlockerIcons
import com.merxury.blocker.core.ui.R
import com.merxury.core.ifw.editor.IfwEditorConditionKind

private data class ConditionPickerSection(
    @StringRes val titleRes: Int,
    val kinds: List<IfwEditorConditionKind>,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ConditionPickerSheet(
    onDismiss: () -> Unit,
    onSelect: (IfwEditorConditionKind) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState()
    val sections = remember {
        listOf(
            ConditionPickerSection(
                titleRes = R.string.core_ui_ifw_picker_section_intent,
                kinds = listOf(
                    IfwEditorConditionKind.ACTION,
                    IfwEditorConditionKind.CATEGORY,
                ),
            ),
            ConditionPickerSection(
                titleRes = R.string.core_ui_ifw_picker_section_caller,
                kinds = listOf(
                    IfwEditorConditionKind.CALLER_TYPE,
                    IfwEditorConditionKind.CALLER_PACKAGE,
                    IfwEditorConditionKind.CALLER_PERMISSION,
                ),
            ),
            ConditionPickerSection(
                titleRes = R.string.core_ui_ifw_picker_section_link,
                kinds = listOf(
                    IfwEditorConditionKind.SCHEME,
                    IfwEditorConditionKind.HOST,
                    IfwEditorConditionKind.PATH,
                    IfwEditorConditionKind.SCHEME_SPECIFIC_PART,
                    IfwEditorConditionKind.DATA,
                    IfwEditorConditionKind.MIME_TYPE,
                    IfwEditorConditionKind.PORT,
                ),
            ),
            ConditionPickerSection(
                titleRes = R.string.core_ui_ifw_picker_section_component,
                kinds = listOf(
                    IfwEditorConditionKind.COMPONENT_FILTER,
                    IfwEditorConditionKind.COMPONENT,
                    IfwEditorConditionKind.COMPONENT_NAME,
                    IfwEditorConditionKind.COMPONENT_PACKAGE,
                ),
            ),
        )
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 24.dp),
        ) {
            Text(
                text = stringResource(R.string.core_ui_ifw_add_condition),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
            )
            sections.forEach { section ->
                Text(
                    text = stringResource(section.titleRes),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                )
                section.kinds.forEach { kind ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onSelect(kind)
                                onDismiss()
                            }
                            .padding(horizontal = 24.dp, vertical = 12.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = BlockerIcons.Add,
                                contentDescription = null,
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = stringResource(kind.labelRes),
                                    style = MaterialTheme.typography.bodyLarge,
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = stringResource(kind.descriptionRes),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
