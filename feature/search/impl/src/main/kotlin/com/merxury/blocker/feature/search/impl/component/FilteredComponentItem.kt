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

package com.merxury.blocker.feature.search.impl.component

import android.content.pm.PackageInfo
import android.content.res.Configuration
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest.Builder
import com.merxury.blocker.core.designsystem.component.BlockerBodyLargeText
import com.merxury.blocker.core.designsystem.component.BlockerBodyMediumText
import com.merxury.blocker.core.designsystem.icon.BlockerIcons
import com.merxury.blocker.core.designsystem.theme.BlockerTheme
import com.merxury.blocker.core.model.ComponentType.ACTIVITY
import com.merxury.blocker.core.model.data.AppItem
import com.merxury.blocker.core.model.data.ComponentInfo
import com.merxury.blocker.core.model.data.FilteredComponent
import com.merxury.blocker.feature.search.api.R
import com.merxury.blocker.feature.search.api.R.string

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FilteredComponentItem(
    items: FilteredComponent,
    isSelectedMode: Boolean,
    isSelectedInSelectedMode: Boolean,
    modifier: Modifier = Modifier,
    isSelected: Boolean = false,
    switchSelectedMode: (Boolean) -> Unit = {},
    onSelect: (FilteredComponent) -> Unit = {},
    onDeselect: (FilteredComponent) -> Unit = {},
    onComponentClick: (FilteredComponent) -> Unit = {},
) {
    val animatedColor = animateColorAsState(
        targetValue = if (isSelectedInSelectedMode || isSelected) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.background,
        animationSpec = tween(300, 0, LinearEasing),
        label = "color",
    )
    val radius = if (isSelectedInSelectedMode) {
        12.dp
    } else {
        0.dp
    }
    val cornerRadius = animateDpAsState(targetValue = radius, label = "shape")
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .background(
                color = animatedColor.value,
                shape = RoundedCornerShape(cornerRadius.value),
            )
            .combinedClickable(
                onClick = {
                    if (!isSelectedMode) {
                        onComponentClick(items)
                    } else {
                        if (isSelectedInSelectedMode) {
                            onDeselect(items)
                        } else {
                            onSelect(items)
                        }
                    }
                },
                onLongClick = {
                    if (!isSelectedMode) {
                        switchSelectedMode(true)
                        onSelect(items)
                    }
                },
            )
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        SelectableAppIcon(
            info = items.app.packageInfo,
            isSelected = isSelectedInSelectedMode,
        )
        Spacer(modifier = Modifier.width(16.dp))
        AppContent(appItem = items)
    }
}

@Composable
private fun SelectableAppIcon(
    info: PackageInfo?,
    isSelected: Boolean,
    modifier: Modifier = Modifier,
) {
    Crossfade(
        isSelected,
        animationSpec = tween(500),
        label = "icon",
        modifier = modifier,
    ) { targetState ->
        if (targetState) {
            Icon(
                imageVector = BlockerIcons.Check,
                modifier = Modifier.size(48.dp),
                contentDescription = stringResource(id = string.feature_search_api_check_icon),
            )
        } else {
            AsyncImage(
                modifier = Modifier
                    .size(48.dp),
                model = Builder(LocalContext.current)
                    .data(info)
                    .error(BlockerIcons.Android)
                    .placeholder(BlockerIcons.Android)
                    .crossfade(true)
                    .build(),
                contentDescription = null,
            )
        }
    }
}

@Composable
private fun AppContent(
    appItem: FilteredComponent,
    modifier: Modifier = Modifier,
) {
    Column(modifier) {
        BlockerBodyLargeText(
            text = appItem.app.label,
        )
        BlockerBodyMediumText(
            text = getComponentCountDescription(appItem = appItem),
        )
    }
}

@Composable
private fun getComponentCountDescription(appItem: FilteredComponent): String {
    val countDescriptions = mutableListOf<String>()
    if (appItem.receiver.isNotEmpty()) {
        countDescriptions.add(
            pluralStringResource(
                id = R.plurals.feature_search_api_receiver_count,
                count = appItem.receiver.size,
                appItem.receiver.size,
            ),
        )
    }
    if (appItem.service.isNotEmpty()) {
        countDescriptions.add(
            pluralStringResource(
                id = R.plurals.feature_search_api_service_count,
                count = appItem.service.size,
                appItem.service.size,
            ),
        )
    }
    if (appItem.activity.isNotEmpty()) {
        countDescriptions.add(
            pluralStringResource(
                id = R.plurals.feature_search_api_activity_count,
                count = appItem.activity.size,
                appItem.activity.size,
            ),
        )
    }
    if (appItem.provider.isNotEmpty()) {
        countDescriptions.add(
            pluralStringResource(
                id = R.plurals.feature_search_api_content_provider_count,
                count = appItem.provider.size,
                appItem.provider.size,
            ),
        )
    }
    return countDescriptions
        .joinToString(separator = stringResource(id = string.feature_search_api_delimiter))
}

@Composable
@Preview
@Preview(uiMode = Configuration.UI_MODE_NIGHT_YES)
private fun AppListItemInSelectedModePreview() {
    val componentInfo = ComponentInfo(
        name = "component",
        packageName = "blocker",
        type = ACTIVITY,
    )
    val filterAppItem = FilteredComponent(
        app = AppItem(
            packageName = "com.merxury.blocker",
            label = "Blocker",
            isSystem = false,
        ),
        activity = listOf(componentInfo),
        service = listOf(componentInfo),
        receiver = listOf(componentInfo),
        provider = listOf(componentInfo),
    )
    BlockerTheme {
        Surface {
            FilteredComponentItem(
                items = filterAppItem,
                isSelected = true,
                isSelectedMode = true,
                isSelectedInSelectedMode = true,
            )
        }
    }
}

@Composable
@Preview
private fun AppListItemSelectedPreview() {
    val componentInfo = ComponentInfo(
        name = "component",
        packageName = "blocker",
        type = ACTIVITY,
    )
    val filterAppItem = FilteredComponent(
        app = AppItem(
            packageName = "com.merxury.blocker",
            label = "Blocker",
            isSystem = false,
        ),
        activity = listOf(componentInfo),
        service = listOf(componentInfo),
        receiver = listOf(componentInfo),
        provider = listOf(componentInfo),
    )
    BlockerTheme {
        FilteredComponentItem(
            items = filterAppItem,
            isSelectedMode = false,
            isSelected = true,
            isSelectedInSelectedMode = false,
        )
    }
}

@Composable
@Preview
private fun AppListItemWithoutServicePreview() {
    val componentInfo = ComponentInfo(
        name = "component",
        packageName = "blocker",
        type = ACTIVITY,
    )
    val filterAppItem = FilteredComponent(
        app = AppItem(
            packageName = "com.merxury.blocker",
            label = "Blocker",
            isSystem = false,
        ),
        activity = listOf(componentInfo),
        service = listOf(componentInfo),
        receiver = listOf(componentInfo),
        provider = listOf(componentInfo),
    )
    BlockerTheme {
        FilteredComponentItem(
            items = filterAppItem,
            isSelectedMode = false,
            isSelectedInSelectedMode = false,
            isSelected = false,
        )
    }
}
