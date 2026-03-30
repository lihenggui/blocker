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

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.merxury.blocker.core.designsystem.component.BlockerSwitch
import com.merxury.blocker.core.model.data.SimpleRuleComponentUiState
import com.merxury.blocker.core.model.data.SimpleTargetMode

@Composable
internal fun SectionTitle(
    title: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        modifier = modifier.padding(bottom = 8.dp),
    )
}

@Composable
internal fun TargetModeRow(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .selectable(
                selected = selected,
                onClick = onClick,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(
            selected = selected,
            onClick = onClick,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
internal fun ComponentSelectionContent(
    components: List<SimpleRuleComponentUiState>,
    targetMode: SimpleTargetMode,
    isLoading: Boolean,
    error: String?,
    onSelectSingleTarget: (String) -> Unit,
    onToggleMultiTarget: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        when {
            isLoading -> {
                Text(
                    text = stringResource(R.string.feature_globalifwrule_impl_loading_components),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            error != null -> {
                Text(
                    text = error,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            components.isEmpty() -> {
                Text(
                    text = stringResource(R.string.feature_globalifwrule_impl_no_components),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            else -> {
                components.forEach { component ->
                    ComponentSelectionRow(
                        component = component,
                        targetMode = targetMode,
                        onSelectSingleTarget = onSelectSingleTarget,
                        onToggleMultiTarget = onToggleMultiTarget,
                    )
                }
            }
        }
    }
}

@Composable
internal fun ComponentSelectionRow(
    component: SimpleRuleComponentUiState,
    targetMode: SimpleTargetMode,
    onSelectSingleTarget: (String) -> Unit,
    onToggleMultiTarget: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        tonalElevation = if (component.selected) 2.dp else 0.dp,
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable {
                    if (targetMode == SimpleTargetMode.SINGLE) {
                        onSelectSingleTarget(component.flattenedName)
                    } else {
                        onToggleMultiTarget(component.flattenedName)
                    }
                }
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            BlockerSwitch(
                checked = component.selected,
                onCheckedChange = {
                    if (targetMode == SimpleTargetMode.SINGLE) {
                        onSelectSingleTarget(component.flattenedName)
                    } else {
                        onToggleMultiTarget(component.flattenedName)
                    }
                },
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = component.simpleName,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (component.simpleName != component.componentName) {
                    Text(
                        text = component.componentName,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}
