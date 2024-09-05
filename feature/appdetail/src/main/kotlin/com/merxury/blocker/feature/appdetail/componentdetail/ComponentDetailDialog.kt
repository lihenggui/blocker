/*
 * Copyright 2024 Blocker
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
import com.merxury.blocker.core.ui.R.string as UiString

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
    uiState: ComponentDetailUiState,
    modifier: Modifier = Modifier,
    onDismiss: () -> Unit = {},
    onInfoChange: (ComponentDetail) -> Unit = {},
    onSaveDetailClick: (ComponentDetail) -> Unit = {},
) {
    val configuration = LocalConfiguration.current
    AlertDialog(
        properties = DialogProperties(usePlatformDefaultWidth = false),
        modifier = modifier.widthIn(max = configuration.screenWidthDp.dp - 80.dp),
        onDismissRequest = { onDismiss() },
        title = {
            Text(
                text = if (uiState is Success) {
                    uiState.detail.name.split(".").last()
                } else {
                    stringResource(id = string.feature_appdetail_unknown)
                },
                style = MaterialTheme.typography.headlineSmall,
            )
        },
        text = {
            when (uiState) {
                Loading -> {
                    Text(
                        text = stringResource(UiString.core_ui_loading),
                        modifier = Modifier.padding(vertical = 16.dp),
                    )
                }

                is Success -> {
                    ComponentDetailPanel(
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
                Text(text = stringResource(string.feature_appdetail_save))
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
    componentDetailInfo: ComponentDetail,
    onInfoChange: (ComponentDetail) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        OutlinedTextField(
            value = componentDetailInfo.description ?: "",
            label = {
                Text(text = stringResource(id = UiString.core_ui_description))
            },
            onValueChange = {
                onInfoChange.invoke(componentDetailInfo.copy(description = it))
            },
        )
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = componentDetailInfo.disableEffect ?: "",
            label = {
                Text(text = stringResource(id = string.feature_appdetail_blocking_effect))
            },
            onValueChange = {
                onInfoChange.invoke(componentDetailInfo.copy(disableEffect = it))
            },
        )
        Spacer(modifier = Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(
                checked = componentDetailInfo.recommendToBlock,
                onCheckedChange = {
                    onInfoChange.invoke(componentDetailInfo.copy(recommendToBlock = it))
                },
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(text = stringResource(id = string.feature_appdetail_recommended_blocking))
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(
                checked = componentDetailInfo.sdkName.isNullOrEmpty().not(),
                onCheckedChange = {
                    onInfoChange.invoke(
                        componentDetailInfo.copy(
                            sdkName = if (it) {
                                string.feature_appdetail_unknown.toString()
                            } else {
                                null
                            },
                        ),
                    )
                },
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(text = stringResource(id = string.feature_appdetail_belonging_sdk))
        }
    }
}

@Preview
@Composable
private fun EditComponentDetailDialogPreview() {
    val detail = ComponentDetail(
        name = "com.merxury.blocker.feature.appdetail.componentdetail.EditComponentDetailDialog",
        description = "This is a test description",
        recommendToBlock = true,
        sdkName = "com.merxury.blocker.feature.appdetail.componentdetail",
    )
    val uiState = Success(
        detail = detail,
    )
    ComponentDetailDialog(uiState = uiState)
}

@Preview
@Composable
private fun LoadingComponentDetailDialogPreview() {
    val uiState = Loading
    ComponentDetailDialog(uiState = uiState)
}
