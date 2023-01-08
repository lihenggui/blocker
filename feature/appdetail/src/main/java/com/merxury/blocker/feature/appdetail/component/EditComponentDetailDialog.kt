/*
 * Copyright 2023 Blocker
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

package com.merxury.blocker.feature.appdetail.component

import android.R.string
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.merxury.blocker.core.designsystem.theme.BlockerTheme
import com.merxury.blocker.feature.appdetail.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditComponentDetailDialog(
    modifier: Modifier = Modifier,
    descriptionValue: String?,
    blockEffectValue: String?,
    onDescriptionValueChange: (String) -> Unit,
    onBlockEffectValueChange: (String) -> Unit,
    recommendedBlockingValue: Boolean,
    belongingSdkValue: Boolean,
    onSave: () -> Unit,
    onDismissRequest: () -> Unit
) {
    AlertDialog(onDismissRequest = onDismissRequest, title = {
        Text(
            text = stringResource(id = R.string.analyze_service),
            style = MaterialTheme.typography.headlineSmall
        )
    }, text = {
        Column {
            OutlinedTextField(
                modifier = modifier.height(56.dp),
                value = descriptionValue ?: "",
                label = { R.string.description },
                onValueChange = onDescriptionValueChange
            )
            Spacer(modifier = modifier.height(8.dp))
            OutlinedTextField(
                modifier = modifier.height(56.dp),
                value = blockEffectValue ?: "",
                label = { R.string.blocking_effect },
                onValueChange = onBlockEffectValueChange
            )
            Spacer(modifier = modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = recommendedBlockingValue, onCheckedChange = {
                    // TODO
                })
                Spacer(modifier = modifier.width(8.dp))
                Text(text = stringResource(id = R.string.recommended_blocking))
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = belongingSdkValue, onCheckedChange = {
                    // TODO
                })
                Spacer(modifier = modifier.width(8.dp))
                Text(text = stringResource(id = R.string.belonging_sdk))
            }
        }
    }, confirmButton = {
        Text(
            text = stringResource(R.string.save),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .padding(horizontal = 8.dp)
                .clickable {
                    onSave()
                    onDismissRequest()
                }
        )
    }, dismissButton = {
        Text(
            text = stringResource(string.cancel),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .padding(horizontal = 8.dp)
                .clickable { onDismissRequest() }
        )
    })
}

@Composable
@Preview
fun AnalyzeServiceDialogPreview() {
    BlockerTheme {
        Surface {
            EditComponentDetailDialog(
                descriptionValue = null,
                blockEffectValue = null,
                onDescriptionValueChange = {},
                onBlockEffectValueChange = {},
                recommendedBlockingValue = false,
                belongingSdkValue = true,
                onSave = {},
                onDismissRequest = {},
            )
        }
    }
}
