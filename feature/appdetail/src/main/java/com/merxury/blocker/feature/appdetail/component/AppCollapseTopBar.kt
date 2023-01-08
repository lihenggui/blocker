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

package com.merxury.blocker.feature.appdetail.component

import android.content.pm.PackageInfo
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest.Builder
import com.merxury.blocker.core.designsystem.theme.BlockerTheme
import com.merxury.blocker.core.model.Application
import kotlinx.datetime.Clock.System

@Composable
fun AppCollapseTopBar(
    app: Application,
    topAppBarTextSize: TextUnit,
    isCollapsed: Boolean,
    modifier: Modifier = Modifier,
    iconModifier: Modifier = Modifier
) {
    val versionName = app.versionName
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(
                text = app.label,
                fontSize = topAppBarTextSize
            )
            if (!isCollapsed) {
                Text(text = app.packageName, style = MaterialTheme.typography.bodyMedium)
                if (versionName != null) {
                    Text(text = versionName, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
        Spacer(modifier = Modifier.weight(1f))
        AppIcon(
            info = app.packageInfo,
            modifier = iconModifier
                .size(80.dp)
                .padding(vertical = 40.dp),
        )
    }
}

@Composable
private fun AppIcon(info: PackageInfo?, modifier: Modifier = Modifier) {
    AsyncImage(
        modifier = modifier,
        model = Builder(LocalContext.current)
            .data(info)
            .crossfade(true)
            .build(),
        contentDescription = null
    )
}

@Composable
@Preview
fun PreviewAppBasicInfoCard() {
    val app = Application(
        label = "Blocker",
        packageName = "com.mercury.blocker",
        versionName = "1.2.69-alpha",
        isEnabled = false,
        firstInstallTime = System.now(),
        lastUpdateTime = System.now(),
        packageInfo = null,
    )
    BlockerTheme {
        Surface {
            AppCollapseTopBar(
                app = app,
                topAppBarTextSize = 28.sp,
                isCollapsed = false
            )
        }
    }
}
