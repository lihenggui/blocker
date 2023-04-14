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
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.merxury.blocker.core.designsystem.component.BlockerTextButton
import com.merxury.blocker.core.model.data.ComponentDetail
import com.merxury.blocker.core.ui.TrackScreenViewEvent
import com.merxury.blocker.feature.appdetail.R.string

@Composable
fun ComponentDetailDialog(
    name: String,
    modifier: Modifier = Modifier,
    belongToSdk: Boolean = false,
    sdkName: String? = null,
    description: String? = null,
    disableEffect: String? = null,
    contributor: String? = null,
    addedVersion: String? = null,
    recommendToBlock: Boolean = false,
    onDismiss: () -> Unit = {},
    onSaveDetailClick: (ComponentDetail) -> Unit = {},
) {
    val configuration = LocalConfiguration.current
    var valueChanged by remember { mutableStateOf(false) }
    var _belongToSdk by remember { mutableStateOf(belongToSdk) }
    var _sdkName by remember { mutableStateOf(sdkName ?: "") }
    var _description by remember { mutableStateOf(description ?: "") }
    var _disableEffect by remember { mutableStateOf(disableEffect ?: "") }
    var _contributor by remember { mutableStateOf(contributor ?: "") }
    var _addedVersion by remember { mutableStateOf(addedVersion ?: "") }
    var _recommendToBlock by remember { mutableStateOf(recommendToBlock) }
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
                    value = _description,
                    label = {
                        Text(text = stringResource(id = string.description))
                    },
                    onValueChange = { newValue ->
                        _description = newValue
                        valueChanged = true
                    },
                )
                Spacer(modifier = modifier.height(8.dp))
                OutlinedTextField(
                    value = _disableEffect,
                    label = {
                        Text(text = stringResource(id = string.blocking_effect))
                    },
                    onValueChange = {
                        _disableEffect = it
                        valueChanged = true
                    },
                )
                Spacer(modifier = modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = _recommendToBlock,
                        onCheckedChange = { checked ->
                            _recommendToBlock = checked
                            valueChanged = true
                        },
                    )
                    Spacer(modifier = modifier.width(8.dp))
                    Text(text = stringResource(id = string.recommended_blocking))
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = _belongToSdk,
                        onCheckedChange = { checked ->
                            _belongToSdk = checked
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
                        onSaveDetailClick(
                            ComponentDetail(
                                name = name,
                                sdkName = _sdkName,
                                description = _description,
                                disableEffect = _disableEffect,
                                contributor = _contributor,
                                addedVersion = _addedVersion,
                                recommendToBlock = _recommendToBlock,
                            ),
                        )
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
        description = "This is a test description",
        recommendToBlock = true,
        sdkName = "com.merxury.blocker.feature.appdetail.componentdetail",
    )
}
