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

package com.merxury.blocker.feature.globalifwrule.impl.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.merxury.blocker.core.model.data.SimpleRuleComponentUiState
import com.merxury.blocker.core.model.data.SimpleTargetMode
import com.merxury.blocker.feature.globalifwrule.api.R

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
                role = Role.RadioButton,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(
            selected = selected,
            onClick = null,
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier
                .defaultMinSize(minHeight = 48.dp)
                .padding(vertical = 12.dp),
        )
    }
}

@Composable
private fun ComponentSelectionToggle(
    targetMode: SimpleTargetMode,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    when (targetMode) {
        SimpleTargetMode.SINGLE -> {
            RadioButton(
                selected = selected,
                onClick = onClick,
                modifier = modifier,
            )
        }

        SimpleTargetMode.MULTIPLE -> {
            Checkbox(
                checked = selected,
                onCheckedChange = { onClick() },
                modifier = modifier,
            )
        }
    }
}

private fun Modifier.componentSelectionModifier(
    targetMode: SimpleTargetMode,
    selected: Boolean,
    onClick: () -> Unit,
): Modifier = when (targetMode) {
    SimpleTargetMode.SINGLE -> selectable(
        selected = selected,
        onClick = onClick,
        role = Role.RadioButton,
    )

    SimpleTargetMode.MULTIPLE -> toggleable(
        value = selected,
        onValueChange = { onClick() },
        role = Role.Checkbox,
    )
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
                    text = stringResource(R.string.feature_globalifwrule_api_loading_components),
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
                    text = stringResource(R.string.feature_globalifwrule_api_no_components),
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
    val onSelectionChange = {
        if (targetMode == SimpleTargetMode.SINGLE) {
            onSelectSingleTarget(component.flattenedName)
        } else {
            onToggleMultiTarget(component.flattenedName)
        }
    }

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
                .componentSelectionModifier(
                    targetMode = targetMode,
                    selected = component.selected,
                    onClick = onSelectionChange,
                )
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ComponentSelectionToggle(
                targetMode = targetMode,
                selected = component.selected,
                onClick = onSelectionChange,
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
