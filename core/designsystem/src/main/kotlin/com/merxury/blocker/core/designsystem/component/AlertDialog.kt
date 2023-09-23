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

package com.merxury.blocker.core.designsystem.component

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.merxury.blocker.core.designsystem.theme.BlockerTheme

@Composable
fun BlockerErrorAlertDialog(
    title: String,
    text: String,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scrollState = rememberScrollState(0)
    AlertDialog(
        modifier = modifier,
        title = {
            Text(text = title)
        },
        text = {
            Text(
                modifier = Modifier.verticalScroll(scrollState),
                text = text,
            )
        },
        onDismissRequest = onDismissRequest,
        confirmButton = {
            BlockerTextButton(
                onClick = onDismissRequest,
            ) {
                Text(
                    text = stringResource(id = android.R.string.ok),
                )
            }
        },
    )
}

@Composable
fun BlockerWarningAlertDialog(
    title: String,
    text: String,
    onDismissRequest: () -> Unit,
    onConfirmRequest: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scrollState = rememberScrollState(0)
    AlertDialog(
        modifier = modifier,
        title = {
            Text(
                modifier = Modifier,
                text = title,
            )
        },
        text = {
            Text(
                modifier = Modifier.verticalScroll(scrollState),
                text = text,
            )
        },
        onDismissRequest = onDismissRequest,
        confirmButton = {
            BlockerTextButton(
                onClick = {
                    onConfirmRequest()
                    onDismissRequest()
                },
            ) {
                Text(
                    text = stringResource(id = android.R.string.ok),
                )
            }
        },
        dismissButton = {
            BlockerTextButton(onClick = onDismissRequest) {
                Text(
                    text = stringResource(id = android.R.string.cancel),
                )
            }
        },
    )
}

@Composable
fun BlockerConfirmAlertDialog(
    text: String,
    onDismissRequest: () -> Unit,
    onConfirmRequest: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AlertDialog(
        modifier = modifier,
        text = {
            Text(text = text)
        },
        onDismissRequest = onDismissRequest,
        confirmButton = {
            BlockerTextButton(
                onClick = {
                    onConfirmRequest()
                    onDismissRequest()
                },
            ) {
                Text(
                    text = stringResource(id = android.R.string.ok),
                )
            }
        },
        dismissButton = {
            BlockerTextButton(onClick = onDismissRequest) {
                Text(
                    text = stringResource(id = android.R.string.cancel),
                )
            }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BlockerButtonAlertDialog(
    title: String,
    buttons: @Composable () -> Unit,
    onDismissRequest: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
    ) {
        Surface(
            shape = MaterialTheme.shapes.large,
            tonalElevation = AlertDialogDefaults.TonalElevation,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.padding(bottom = 16.dp),
                )
                buttons()
            }
        }
    }
}

@Preview
@Composable
fun BlockerButtonAlertDialogPreView() {
    BlockerTheme {
        BlockerButtonAlertDialog(
            onDismissRequest = {},
            title = "title",
            buttons = {
                BlockerTextButton(onClick = {}) {
                    Text(
                        text = "button 1",
                    )
                }
                BlockerTextButton(onClick = {}) {
                    Text(
                        text = "button 2",
                    )
                }
            },
        )
    }
}

@Preview
@Composable
fun BlockerConfirmAlertDialogPreView() {
    BlockerTheme {
        BlockerConfirmAlertDialog(
            text = "This operation will block 4 components, do you want to continue?",
            onDismissRequest = {},
            onConfirmRequest = {},
        )
    }
}
