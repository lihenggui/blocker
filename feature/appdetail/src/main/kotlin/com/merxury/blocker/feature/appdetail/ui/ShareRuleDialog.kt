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

package com.merxury.blocker.feature.appdetail.ui

import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.merxury.blocker.core.designsystem.component.BlockerButtonAlertDialog
import com.merxury.blocker.core.designsystem.icon.BlockerActionIcon
import com.merxury.blocker.core.designsystem.icon.BlockerIcons
import com.merxury.blocker.core.designsystem.icon.Icon.ImageVectorIcon
import com.merxury.blocker.core.designsystem.theme.BlockerTheme
import com.merxury.blocker.core.ui.BlockerItem
import com.merxury.blocker.feature.appdetail.R.string

@Composable
fun ShareAction(
    shareAppRule: () -> Unit = {},
    shareAllRules: () -> Unit = {},
) {
    var isDialogVisible by remember { mutableStateOf(false) }
    IconButton(
        onClick = { isDialogVisible = true },
    ) {
        BlockerActionIcon(
            imageVector = BlockerIcons.Share,
            contentDescription = stringResource(id = string.feature_appdetail_share_your_rules),
        )
    }
    ShareRuleDialog(
        isDialogVisible = isDialogVisible,
        shareAppRule = shareAppRule,
        shareAllRules = shareAllRules,
        onDismissRequest = { isDialogVisible = false },
    )
}

@Composable
fun ShareRuleDialog(
    isDialogVisible: Boolean = false,
    shareAppRule: () -> Unit = {},
    shareAllRules: () -> Unit = {},
    onDismissRequest: () -> Unit = {},
) {
    if (isDialogVisible) {
        BlockerButtonAlertDialog(
            title = stringResource(id = string.feature_appdetail_share_your_rules),
            buttons = {
                BlockerItem(
                    icon = ImageVectorIcon(BlockerIcons.CheckSmall),
                    title = stringResource(id = string.feature_appdetail_share_rules_of_this_app),
                    onItemClick = {
                        shareAppRule()
                        onDismissRequest()
                    },
                )
                BlockerItem(
                    icon = ImageVectorIcon(BlockerIcons.CheckList),
                    title = stringResource(id = string.feature_appdetail_share_all_rules),
                    onItemClick = {
                        shareAllRules()
                        onDismissRequest()
                    },
                )
            },
            onDismissRequest = onDismissRequest,
        )
    }
}

@Composable
@Preview
fun ShareRuleDialogPreview() {
    BlockerTheme {
        Surface {
            ShareRuleDialog(
                isDialogVisible = true,
                shareAppRule = {},
                shareAllRules = {},
                onDismissRequest = {},
            )
        }
    }
}
