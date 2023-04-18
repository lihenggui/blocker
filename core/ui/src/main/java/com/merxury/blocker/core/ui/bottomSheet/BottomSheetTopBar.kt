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

package com.merxury.blocker.core.ui.bottomSheet

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.ripple.rememberRipple
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.merxury.blocker.core.designsystem.component.BlockerBodyLargeText
import com.merxury.blocker.core.designsystem.component.BlockerBodySmallText
import com.merxury.blocker.core.designsystem.theme.BlockerTheme
import com.merxury.blocker.core.ui.applist.model.AppItem

@Composable
fun BottomSheetTopBar(
    modifier: Modifier = Modifier,
    title: String,
    subtitle: String,
    summary: String,
    iconSource: Any? = null,
    onIconClick: () -> Unit = {},
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(color = MaterialTheme.colorScheme.surfaceVariant)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = modifier
                .fillMaxWidth(0.7f),
        ) {
            BlockerBodyLargeText(
                text = title,
                maxLines = 1,
                fontSize = 28.sp,
            )
            BlockerBodySmallText(
                text = subtitle,
                maxLines = 2,
                modifier = Modifier
                    .padding(vertical = 4.dp),
            )
            BlockerBodySmallText(
                text = summary,
                maxLines = 2,
                modifier = Modifier
                    .padding(vertical = 4.dp),
            )
        }
        Spacer(modifier = Modifier.weight(1f))
        AsyncImage(
            modifier = Modifier
                .size(80.dp)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = rememberRipple(bounded = false),
                    onClick = { onIconClick() },
                ),
            model = ImageRequest.Builder(LocalContext.current)
                .data(iconSource)
                .crossfade(true)
                .build(),
            contentDescription = null,
        )
    }
}

@Composable
@Preview
fun BottomSheetAppPreview() {
    val app = AppItem(
        packageName = "com.merxury.blocker",
        label = "Blocker test long name",
        versionName = "23.12.20",
        isSystem = false,
    )
    BlockerTheme {
        Surface {
            BottomSheetTopBar(
                title = app.label,
                subtitle = app.packageName,
                summary = app.versionName,
            )
        }
    }
}
