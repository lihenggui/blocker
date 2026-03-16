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

package com.merxury.blocker.feature.ifwrule.impl.component

import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.merxury.blocker.feature.ifwrule.impl.R
import com.merxury.blocker.feature.ifwrule.impl.model.ConditionUiState
import java.util.UUID

private data class ConditionTypeItem(
    @StringRes val titleRes: Int,
    @StringRes val descriptionRes: Int,
    val factory: () -> ConditionUiState,
)

private val conditionTypes = listOf(
    ConditionTypeItem(
        titleRes = R.string.feature_ifwrule_condition_action,
        descriptionRes = R.string.feature_ifwrule_condition_action_desc,
        factory = { ConditionUiState.ActionFilter(id = UUID.randomUUID().toString()) },
    ),
    ConditionTypeItem(
        titleRes = R.string.feature_ifwrule_condition_source,
        descriptionRes = R.string.feature_ifwrule_condition_source_desc,
        factory = { ConditionUiState.SourceControl(id = UUID.randomUUID().toString()) },
    ),
    ConditionTypeItem(
        titleRes = R.string.feature_ifwrule_condition_caller_app,
        descriptionRes = R.string.feature_ifwrule_condition_caller_app_desc,
        factory = { ConditionUiState.CallerApp(id = UUID.randomUUID().toString()) },
    ),
    ConditionTypeItem(
        titleRes = R.string.feature_ifwrule_condition_caller_permission,
        descriptionRes = R.string.feature_ifwrule_condition_caller_permission_desc,
        factory = { ConditionUiState.CallerPermission(id = UUID.randomUUID().toString()) },
    ),
    ConditionTypeItem(
        titleRes = R.string.feature_ifwrule_condition_category,
        descriptionRes = R.string.feature_ifwrule_condition_category_desc,
        factory = { ConditionUiState.CategoryFilter(id = UUID.randomUUID().toString()) },
    ),
    ConditionTypeItem(
        titleRes = R.string.feature_ifwrule_condition_link,
        descriptionRes = R.string.feature_ifwrule_condition_link_desc,
        factory = { ConditionUiState.LinkFilter(id = UUID.randomUUID().toString()) },
    ),
    ConditionTypeItem(
        titleRes = R.string.feature_ifwrule_condition_data,
        descriptionRes = R.string.feature_ifwrule_condition_data_desc,
        factory = { ConditionUiState.DataFilter(id = UUID.randomUUID().toString()) },
    ),
    ConditionTypeItem(
        titleRes = R.string.feature_ifwrule_condition_mime,
        descriptionRes = R.string.feature_ifwrule_condition_mime_desc,
        factory = { ConditionUiState.MimeTypeFilter(id = UUID.randomUUID().toString()) },
    ),
    ConditionTypeItem(
        titleRes = R.string.feature_ifwrule_condition_port,
        descriptionRes = R.string.feature_ifwrule_condition_port_desc,
        factory = { ConditionUiState.PortFilter(id = UUID.randomUUID().toString()) },
    ),
    ConditionTypeItem(
        titleRes = R.string.feature_ifwrule_condition_component_pattern,
        descriptionRes = R.string.feature_ifwrule_condition_component_pattern_desc,
        factory = { ConditionUiState.ComponentPattern(id = UUID.randomUUID().toString()) },
    ),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConditionTypeBottomSheet(
    onDismiss: () -> Unit,
    onSelect: (ConditionUiState) -> Unit,
    modifier: Modifier = Modifier,
) {
    val sheetState = rememberModalBottomSheetState()
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 16.dp),
        ) {
            Text(
                text = stringResource(R.string.feature_ifwrule_add_condition),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
            )
            conditionTypes.forEach { item ->
                ConditionTypeRow(
                    titleRes = item.titleRes,
                    descriptionRes = item.descriptionRes,
                    onClick = {
                        onSelect(item.factory())
                        onDismiss()
                    },
                )
            }
            Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.safeDrawing))
        }
    }
}

@Composable
private fun ConditionTypeRow(
    @StringRes titleRes: Int,
    @StringRes descriptionRes: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 12.dp),
    ) {
        Text(
            text = stringResource(titleRes),
            style = MaterialTheme.typography.bodyLarge,
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = stringResource(descriptionRes),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
