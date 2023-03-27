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

package com.merxury.blocker.feature.appdetail.componentdetail

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.merxury.blocker.core.designsystem.component.BlockerTextButton
import com.merxury.blocker.core.ui.TrackScreenViewEvent
import com.merxury.blocker.feature.appdetail.R.string

@Composable
fun ComponentDetailDialog(
    name: String,
    detail: UserEditableComponentDetail,
    modifier: Modifier = Modifier,
    onDismiss: () -> Unit = {},
    onSaveDetailClick: (UserEditableComponentDetail) -> Unit = {},
) {
    val configuration = LocalConfiguration.current
    var valueChanged by rememberSaveable { mutableStateOf(false) }
    var editableDetail by rememberSaveable { mutableStateOf(detail) }
    /**
     * usePlatformDefaultWidth = false is use as a temporary fix to allow
     * height recalculation during recomposition. This, however, causes
     * Dialog's to occupy full width in Compact mode. Therefore max width
     * is configured below. This should be removed when there's fix to
     * https://issuetracker.google.com/issues/221643630
     */
    AlertDialog(
        properties = DialogProperties(usePlatformDefaultWidth = false),
        modifier = Modifier.widthIn(max = configuration.screenWidthDp.dp - 80.dp),
        onDismissRequest = { onDismiss() },
        title = {
            Text(
                text = name.split(".").last(),
                style = MaterialTheme.typography.headlineSmall,
            )
        },
        text = {
            Column {
                OutlinedTextField(
                    value = editableDetail.description ?: "",
                    label = {
                        Text(text = stringResource(id = string.description))
                    },
                    onValueChange = { newValue ->
                        editableDetail = editableDetail.copy(description = newValue)
                        valueChanged = true
                    },
                )
                Spacer(modifier = modifier.height(8.dp))
                OutlinedTextField(
                    value = editableDetail.disableEffect ?: "",
                    label = {
                        Text(text = stringResource(id = string.blocking_effect))
                    },
                    onValueChange = {
                        editableDetail = editableDetail.copy(disableEffect = it)
                        valueChanged = true
                    },
                )
                Spacer(modifier = modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = editableDetail.recommendToBlock,
                        onCheckedChange = { checked ->
                            editableDetail = editableDetail.copy(recommendToBlock = checked)
                            valueChanged = true
                        },
                    )
                    Spacer(modifier = modifier.width(8.dp))
                    Text(text = stringResource(id = string.recommended_blocking))
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = editableDetail.belongToSdk,
                        onCheckedChange = { checked ->
                            editableDetail = editableDetail.copy(belongToSdk = checked)
                            valueChanged = true
                        },
                    )
                    Spacer(modifier = modifier.width(8.dp))
                    Text(text = stringResource(id = string.belonging_sdk))
                }
            }
            TrackScreenViewEvent(screenName = "ComponentDetail")
        },
        confirmButton = {
            BlockerTextButton(
                onClick = {
                    if (valueChanged) {
                        onSaveDetailClick(editableDetail)
                    }
                    onDismiss()
                },
            ) {
                Text(text = stringResource(string.save))
            }
        },
        dismissButton = {
            BlockerTextButton(
                onClick = {
                    onDismiss()
                },
            ) {
                Text(text = stringResource(android.R.string.cancel))
            }
        },
    )
}

@Preview
@Composable
fun EditComponentDetailDialogPreview() {
    ComponentDetailDialog(
        name = "com.merxury.blocker.feature.appdetail.componentdetail.EditComponentDetailDialog",
        detail = UserEditableComponentDetail(
            name = "com.merxury.blocker.feature.appdetail.componentdetail.EditComponentDetailDialog",
            description = "This is a test description",
            recommendToBlock = true,
            sdkName = "com.merxury.blocker.feature.appdetail.componentdetail",
        ),
    )
}
