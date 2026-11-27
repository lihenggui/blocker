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

package com.merxury.blocker.feature.appdetail.impl.ui

import android.content.res.Configuration
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.merxury.blocker.core.designsystem.component.BlockerButtonAlertDialog
import com.merxury.blocker.core.designsystem.icon.BlockerActionIcon
import com.merxury.blocker.core.designsystem.icon.BlockerIcons
import com.merxury.blocker.core.designsystem.icon.Icon
import com.merxury.blocker.core.designsystem.icon.Icon.DrawableResourceIcon
import com.merxury.blocker.core.designsystem.icon.Icon.ImageVectorIcon
import com.merxury.blocker.core.designsystem.theme.BlockerTheme
import com.merxury.blocker.feature.appdetail.api.R.string

@Composable
fun ShareAction(
    modifier: Modifier = Modifier,
    shareAppRule: () -> Unit = {},
    shareAllRules: () -> Unit = {},
) {
    var isDialogVisible by remember { mutableStateOf(false) }
    IconButton(
        modifier = modifier,
        onClick = { isDialogVisible = true },
    ) {
        BlockerActionIcon(
            imageVector = BlockerIcons.Share,
            contentDescription = stringResource(id = string.feature_appdetail_api_share_your_rules),
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
    modifier: Modifier = Modifier,
    isDialogVisible: Boolean = false,
    shareAppRule: () -> Unit = {},
    shareAllRules: () -> Unit = {},
    onDismissRequest: () -> Unit = {},
) {
    if (isDialogVisible) {
        BlockerButtonAlertDialog(
            modifier = modifier,
            title = stringResource(id = string.feature_appdetail_api_share_your_rules),
            buttons = {
                BlockerItem(
                    icon = ImageVectorIcon(BlockerIcons.CheckSmall),
                    title = stringResource(id = string.feature_appdetail_api_share_rules_of_this_app),
                    onItemClick = {
                        shareAppRule()
                        onDismissRequest()
                    },
                )
                BlockerItem(
                    icon = ImageVectorIcon(BlockerIcons.CheckList),
                    title = stringResource(id = string.feature_appdetail_api_share_all_rules),
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
private fun BlockerItem(
    title: String,
    icon: Icon,
    modifier: Modifier = Modifier,
    onItemClick: () -> Unit = {},
    iconColor: Color = MaterialTheme.colorScheme.primary,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onItemClick() }
            .padding(
                PaddingValues(vertical = 8.dp),
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        when (icon) {
            is ImageVectorIcon -> Icon(
                imageVector = icon.imageVector,
                contentDescription = title,
                tint = iconColor,
            )

            is DrawableResourceIcon -> Icon(
                painter = painterResource(id = icon.id),
                contentDescription = title,
                tint = iconColor,
            )
        }
        Spacer(modifier = Modifier.width(16.dp))
        Column {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
            )
        }
    }
}

@Composable
@Preview
private fun ShareRuleDialogPreview() {
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

@Composable
@Preview
@Preview(uiMode = Configuration.UI_MODE_NIGHT_YES)
private fun BlockerItemPreview() {
    BlockerTheme {
        Surface {
            BlockerItem(
                title = "Item with icon and extra space",
                icon = ImageVectorIcon(BlockerIcons.CheckList),
            )
        }
    }
}
