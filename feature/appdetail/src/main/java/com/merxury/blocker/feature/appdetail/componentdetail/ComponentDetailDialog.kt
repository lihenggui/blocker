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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.merxury.blocker.core.designsystem.component.BlockerAlertDialog
import com.merxury.blocker.core.designsystem.component.BlockerTextButton
import com.merxury.blocker.core.model.data.ComponentDetail
import com.merxury.blocker.feature.appdetail.R

@Composable
fun ComponentDetailDialog(
    name: String,
    modifier: Modifier = Modifier,
    detail: ComponentDetail? = null,
    onSaveDetailClick: (ComponentDetail?) -> Boolean = { false },
    onDismissRequest: () -> Unit = {},
) {
    var valueChanged by rememberSaveable { mutableStateOf(false) }
    var description by rememberSaveable { mutableStateOf(detail?.description) }
    var recommendToBlock by rememberSaveable { mutableStateOf(detail?.recommendToBlock) }
    var belongToSdk by rememberSaveable { mutableStateOf(!detail?.sdkName.isNullOrBlank()) }
    BlockerAlertDialog(
        onDismissRequest = onDismissRequest,
    ) {
        Surface(
            modifier = Modifier
                .wrapContentWidth()
                .wrapContentHeight(),
            shape = MaterialTheme.shapes.large,
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = name,
                    style = MaterialTheme.typography.headlineSmall,
                )
                OutlinedTextField(
                    value = detail?.description ?: "",
                    label = {
                        Text(text = stringResource(id = R.string.description))
                    },
                    onValueChange = { newValue ->
                        description = newValue
                        valueChanged = true
                    },
                )
                Spacer(modifier = modifier.height(8.dp))
                OutlinedTextField(
                    value = detail?.disableEffect ?: "",
                    label = {
                        Text(text = stringResource(id = R.string.blocking_effect))
                    },
                    onValueChange = {},
                )
                Spacer(modifier = modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = recommendToBlock ?: false,
                        onCheckedChange = { checked ->
                            recommendToBlock = checked
                            valueChanged = true
                        },
                    )
                    Spacer(modifier = modifier.width(8.dp))
                    Text(text = stringResource(id = R.string.recommended_blocking))
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = belongToSdk,
                        onCheckedChange = { checked ->
                            belongToSdk = checked
                            valueChanged = true
                        },
                    )
                    Spacer(modifier = modifier.width(8.dp))
                    Text(text = stringResource(id = R.string.belonging_sdk))
                }
                Spacer(modifier = Modifier.height(24.dp))
                Row {
                    Spacer(modifier = Modifier.weight(1f))
                    BlockerTextButton(
                        onClick = {
                            onDismissRequest()
                        },
                    ) {
                        Text(text = stringResource(android.R.string.cancel))
                    }
                    BlockerTextButton(
                        onClick = {
                            val newEntity = detail?.copy(
                                description = description,
                                recommendToBlock = recommendToBlock ?: false,
                                sdkName = if (belongToSdk) detail.sdkName else null,
                            ) ?: if (valueChanged) {
                                ComponentDetail(
                                    name = name,
                                    description = description,
                                    recommendToBlock = recommendToBlock ?: false,
                                    sdkName = if (belongToSdk) detail?.sdkName else null,
                                )
                            } else {
                                null
                            }
                            if (valueChanged) {
                                onSaveDetailClick(newEntity)
                            }
                            onDismissRequest()
                        },
                    ) {
                        Text(text = stringResource(R.string.save))
                    }
                }
            }
        }
    }
}

@Preview
@Composable
fun EditComponentDetailDialogPreview() {
    ComponentDetailDialog(
        name = "com.merxury.blocker.feature.appdetail.componentdetail.EditComponentDetailDialog",
        detail = ComponentDetail(
            name = "com.merxury.blocker.feature.appdetail.componentdetail.EditComponentDetailDialog",
            description = "This is a test description",
            recommendToBlock = true,
            sdkName = "com.merxury.blocker.feature.appdetail.componentdetail",
        ),
    )
}
