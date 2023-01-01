/*
 * Copyright 2022 Blocker
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

package com.merxury.blocker.feature.helpandfeedback.item

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.merxury.blocker.core.designsystem.icon.BlockerIcons
import com.merxury.blocker.core.designsystem.theme.BlockerTheme
import com.merxury.blocker.feature.supportandfeedback.R.string

@Composable
fun Item(
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    itemRes: Int,
    onClick: () -> Unit,
) {
    var padding = 0.dp
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Icon(imageVector = icon, contentDescription = stringResource(id = itemRes))
            Spacer(modifier = Modifier.width(16.dp))
        } else {
            padding = 40.dp
        }
        Text(
            modifier = modifier.padding(start = padding),
            text = stringResource(id = itemRes),
            style = MaterialTheme.typography.bodyLarge
        )
    }
}

@Composable
@Preview
fun ItemWithIconPreview() {
    BlockerTheme {
        Surface {
            Item(
                icon = BlockerIcons.BugReport,
                itemRes = string.export_error_log,
                onClick = {}
            )
        }
    }
}

@Composable
@Preview
fun ItemWithoutIconPreview() {
    BlockerTheme {
        Surface {
            Item(
                itemRes = string.github,
                onClick = {}
            )
        }
    }
}
