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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest.Builder
import com.merxury.blocker.core.model.Application

@Composable
fun CollapseTextSection(
    app: Application
) {
    val versionName = app.versionName
    Text(text = app.packageName, style = MaterialTheme.typography.bodyMedium)
    if (versionName != null) {
        Text(text = versionName, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
fun CollapseImageSection(info: PackageInfo?, modifier: Modifier = Modifier) {
    AsyncImage(
        modifier = modifier
            .size(80.dp)
            .padding(vertical = 40.dp),
        model = Builder(LocalContext.current)
            .data(info)
            .crossfade(true)
            .build(),
        contentDescription = null
    )
}
