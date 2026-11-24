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

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.merxury.blocker.core.designsystem.component.BlockerBodyMediumText
import com.merxury.blocker.core.designsystem.component.BlockerTextButton
import com.merxury.blocker.core.designsystem.component.PreviewThemes
import com.merxury.blocker.core.designsystem.icon.BlockerIcons
import com.merxury.blocker.core.designsystem.theme.BlockerTheme
import com.merxury.blocker.feature.appdetail.api.R.string
import com.merxury.blocker.feature.appdetail.impl.ModeBanner

/** The limits of the current mode, once above the component lists. */
@Composable
internal fun ModeBanners(
    banners: List<ModeBanner>,
    onGrantAccess: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (banners.isEmpty()) return
    Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh, modifier = modifier.fillMaxWidth()) {
        Column {
            banners.forEach { banner ->
                Banner(banner = banner, onGrantAccess = onGrantAccess)
            }
            HorizontalDivider()
        }
    }
}

@Composable
private fun Banner(banner: ModeBanner, onGrantAccess: () -> Unit) {
    Column(modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp).testTag("appDetail:banner:${banner.name}")) {
        Row(verticalAlignment = Alignment.Top, modifier = Modifier.padding(bottom = if (banner == ModeBanner.NO_ACCESS) 0.dp else 12.dp)) {
            Icon(imageVector = BlockerIcons.Info, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.width(12.dp))
            BlockerBodyMediumText(text = stringResource(banner.text()), color = MaterialTheme.colorScheme.onSurface)
        }
        if (banner == ModeBanner.NO_ACCESS) {
            BlockerTextButton(onClick = onGrantAccess, modifier = Modifier.align(Alignment.End)) {
                Text(text = stringResource(string.feature_appdetail_api_grant_access))
            }
        }
    }
}

private fun ModeBanner.text(): Int = when (this) {
    ModeBanner.NO_ACCESS -> string.feature_appdetail_api_banner_no_access
    ModeBanner.NEEDS_ROOT -> string.feature_appdetail_api_banner_needs_root
    ModeBanner.FIREWALL_UNAVAILABLE -> string.feature_appdetail_api_banner_firewall_unavailable
}

@PreviewThemes
@Composable
private fun ModeBannersPreview() {
    BlockerTheme {
        Surface {
            ModeBanners(banners = ModeBanner.entries, onGrantAccess = {})
        }
    }
}
