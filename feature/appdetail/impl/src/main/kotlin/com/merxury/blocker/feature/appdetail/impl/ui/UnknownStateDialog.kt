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

package com.merxury.blocker.feature.appdetail.impl.ui

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import com.merxury.blocker.core.designsystem.component.BlockerTextButton
import com.merxury.blocker.core.model.control.ControlGoal
import com.merxury.blocker.core.ui.control.UnknownCause
import com.merxury.blocker.core.ui.control.explanation
import com.merxury.blocker.feature.appdetail.api.R.string
import com.merxury.blocker.core.ui.R.string as uiString

/**
 * Explains why a component's state cannot be read and what to do about it, and lets the user block
 * or enable it anyway: the layers that can be changed still are, and the outcome says which could
 * not.
 */
@Composable
internal fun UnknownStateDialog(
    cause: UnknownCause,
    onControl: (ControlGoal) -> Unit,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AlertDialog(
        modifier = modifier.testTag("component:unknownDialog"),
        title = { Text(text = stringResource(uiString.core_ui_unknown_state_title)) },
        text = { Text(text = stringResource(cause.explanation())) },
        onDismissRequest = onDismissRequest,
        confirmButton = {
            BlockerTextButton(
                onClick = {
                    onControl(ControlGoal.BLOCK)
                    onDismissRequest()
                },
            ) {
                Text(text = stringResource(string.feature_appdetail_api_block))
            }
        },
        dismissButton = {
            BlockerTextButton(
                onClick = {
                    onControl(ControlGoal.ENABLE)
                    onDismissRequest()
                },
            ) {
                Text(text = stringResource(string.feature_appdetail_api_enable))
            }
        },
    )
}
