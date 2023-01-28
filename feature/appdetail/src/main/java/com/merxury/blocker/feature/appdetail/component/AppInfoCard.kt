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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize.Max
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest.Builder
import com.merxury.blocker.core.designsystem.theme.BlockerTheme
import com.merxury.blocker.feature.appdetail.R.string

@Composable
fun AppInfoCard(
    modifier: Modifier = Modifier,
    label: String,
    packageName: String,
    versionCode: Long,
    versionName: String?,
    packageInfo: PackageInfo?,
    iconModifier: Modifier = Modifier,
    onAppIconClick: (String) -> Unit,
) {
    Row(
        modifier = modifier
            .width(Max)
            .padding(top = 72.dp, bottom = 16.dp, start = 16.dp, end = 16.dp),
    ) {
        Column {
            Text(
                text = label,
                style = MaterialTheme.typography.headlineMedium,
            )
            Text(
                text = packageName,
                style = MaterialTheme.typography.bodySmall,
            )
            Text(
                text = stringResource(
                    id = string.data_with_explanation,
                    versionCode,
                    versionName.orEmpty(),
                ),
                style = MaterialTheme.typography.bodySmall,
            )
        }
        Spacer(modifier.weight(1F))
        AsyncImage(
            modifier = iconModifier
                .size(80.dp)
                .clickable { onAppIconClick(packageName) }
                .padding(vertical = 40.dp),
            model = Builder(LocalContext.current)
                .data(packageInfo)
                .crossfade(true)
                .build(),
            contentDescription = null,
        )
    }
}

@Preview
@Composable
fun AppInfoCardPreview() {
    BlockerTheme {
        Surface {
            AppInfoCard(
                label = "Blocker",
                packageName = "com.merxury.blocker",
                versionCode = 1178L,
                versionName = "1.1.78",
                packageInfo = null,
                onAppIconClick = { },
            )
        }
    }
}
