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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.merxury.blocker.core.designsystem.component.BlockerTextButton
import com.merxury.blocker.core.model.data.ComponentDetail
import com.merxury.blocker.core.ui.TrackScreenViewEvent
import com.merxury.blocker.feature.appdetail.R.string
import com.merxury.blocker.feature.appdetail.componentdetail.ComponentDetailUiState.Error
import com.merxury.blocker.feature.appdetail.componentdetail.ComponentDetailUiState.Loading
import com.merxury.blocker.feature.appdetail.componentdetail.ComponentDetailUiState.Success

@Composable
fun ComponentDetailDialogRoute(
    dismissHandler: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ComponentDetailViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    ComponentDetailDialog(
        modifier = modifier,
        uiState = uiState,
        onDismiss = dismissHandler,
        onInfoChange = viewModel::onInfoChanged,
        onSaveDetailClick = viewModel::save,
    )
}

@Composable
fun ComponentDetailDialog(
    modifier: Modifier = Modifier,
    uiState: ComponentDetailUiState,
    onDismiss: () -> Unit = {},
    onInfoChange: (ComponentDetail) -> Unit = {},
    onSaveDetailClick: (ComponentDetail) -> Unit = {},
) {
    val configuration = LocalConfiguration.current
    AlertDialog(
        properties = DialogProperties(usePlatformDefaultWidth = false),
        modifier = Modifier.widthIn(max = configuration.screenWidthDp.dp - 80.dp),
        onDismissRequest = { onDismiss() },
        title = {
            Text(
                text = if (uiState is Success) {
                    uiState.detail.name.split(".").last()
                } else {
                    stringResource(id = string.unknown)
                },
                style = MaterialTheme.typography.headlineSmall,
            )
        },
        text = {
            when (uiState) {
                Loading -> {
                    Text(
                        text = stringResource(string.loading),
                        modifier = Modifier.padding(vertical = 16.dp),
                    )
                }

                is Success -> {
                    ComponentDetailPanel(
                        modifier = modifier,
                        componentDetailInfo = uiState.detail,
                        onInfoChange = onInfoChange,
                    )
                }

                is Error -> {
                    Text(
                        text = uiState.message.toString(),
                        modifier = Modifier.padding(vertical = 16.dp),
                    )
                }
            }
            TrackScreenViewEvent(screenName = "ComponentDetail")
        },
        confirmButton = {
            BlockerTextButton(
                onClick = {
                    if (uiState is Success) {
                        onSaveDetailClick(uiState.detail)
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

@Composable
fun ComponentDetailPanel(
    modifier: Modifier = Modifier,
    componentDetailInfo: ComponentDetail,
    onInfoChange: (ComponentDetail) -> Unit,
) {
    Column {
        OutlinedTextField(
            value = componentDetailInfo.description ?: "",
            label = {
                Text(text = stringResource(id = string.description))
            },
            onValueChange = {
                onInfoChange.invoke(componentDetailInfo.copy(description = it))
            },
        )
        Spacer(modifier = modifier.height(8.dp))
        OutlinedTextField(
            value = componentDetailInfo.disableEffect ?: "",
            label = {
                Text(text = stringResource(id = string.blocking_effect))
            },
            onValueChange = {
                onInfoChange.invoke(componentDetailInfo.copy(disableEffect = it))
            },
        )
        Spacer(modifier = modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(
                checked = componentDetailInfo.recommendToBlock,
                onCheckedChange = {
                    onInfoChange.invoke(componentDetailInfo.copy(recommendToBlock = it))
                },
            )
            Spacer(modifier = modifier.width(8.dp))
            Text(text = stringResource(id = string.recommended_blocking))
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(
                checked = componentDetailInfo.sdkName.isNullOrEmpty().not(),
                onCheckedChange = {
                    onInfoChange.invoke(
                        componentDetailInfo.copy(
                            sdkName = if (it) {
                                string.unknown.toString()
                            } else {
                                null
                            },
                        ),
                    )
                },
            )
            Spacer(modifier = modifier.width(8.dp))
            Text(text = stringResource(id = string.belonging_sdk))
        }
    }
}

@Preview
@Composable
fun EditComponentDetailDialogPreview() {
    val detail = ComponentDetail(
        name = "com.merxury.blocker.feature.appdetail.componentdetail.EditComponentDetailDialog",
        description = "This is a test description",
        recommendToBlock = true,
        sdkName = "com.merxury.blocker.feature.appdetail.componentdetail",
    )
    val uiState = Success(
        isFetchingData = false,
        detail = detail,
    )
    ComponentDetailDialog(uiState = uiState)
}

@Preview
@Composable
fun LoadingComponentDetailDialogPreview() {
    val uiState = Loading
    ComponentDetailDialog(uiState = uiState)
}
