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

package com.merxury.blocker.feature.search.component

import android.content.pm.PackageInfo
import android.content.res.Configuration
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest.Builder
import com.merxury.blocker.core.designsystem.icon.BlockerIcons
import com.merxury.blocker.core.designsystem.theme.BlockerTheme
import com.merxury.blocker.core.model.ComponentType.ACTIVITY
import com.merxury.blocker.core.model.data.ComponentInfo
import com.merxury.blocker.feature.search.R
import com.merxury.blocker.feature.search.model.FilteredComponentItem
import com.merxury.blocker.feature.search.model.InstalledAppItem

@OptIn(ExperimentalFoundationApi::class, ExperimentalComposeUiApi::class)
@Composable
fun AppListItem(
    filterAppItem: FilteredComponentItem,
    modifier: Modifier = Modifier,
    isSelectedMode: Boolean,
    switchSelectedMode: (Boolean) -> Unit,
    onSelect: (Boolean) -> Unit,
    onClick: () -> Unit,
) {
    val keyboardController = LocalSoftwareKeyboardController.current
    val color = if (isSelectedMode) {
        MaterialTheme.colorScheme.tertiaryContainer
    } else {
        MaterialTheme.colorScheme.background
    }
    val shape = if (isSelectedMode) {
        RoundedCornerShape(12.dp)
    } else {
        RoundedCornerShape(0.dp)
    }
    Box(
        modifier = modifier
            .padding(horizontal = 8.dp, vertical = 4.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = modifier
                .fillMaxWidth()
                .combinedClickable(
                    onClick = {
                        if (!isSelectedMode) {
                            onClick()
                        } else {
                            onSelect(!filterAppItem.isSelected)
                        }
                        keyboardController?.hide()
                    },
                    onLongClick = {
                        if (!isSelectedMode) {
                            switchSelectedMode(true)
                        }
                        keyboardController?.hide()
                    },
                )
                .background(
                    color = color,
                    shape = shape,
                )
                .padding(horizontal = 8.dp, vertical = 10.dp),

        ) {
            AppIcon(
                info = filterAppItem.app.packageInfo,
                isSelectedMode = isSelectedMode,
                isSelected = filterAppItem.isSelected,
                onSelect = onSelect,
            )
            Spacer(modifier = Modifier.width(16.dp))
            AppContent(appItem = filterAppItem)
        }
    }
}

@Composable
private fun AppIcon(
    info: PackageInfo?,
    modifier: Modifier = Modifier,
    isSelectedMode: Boolean,
    isSelected: Boolean,
    onSelect: (Boolean) -> Unit,
) {
    if (isSelected) {
        IconButton(onClick = { onSelect(false) }) {
            Icon(
                imageVector = BlockerIcons.Check,
                modifier = modifier.size(40.dp),
                contentDescription = null,
            )
        }
    } else {
        AsyncImage(
            modifier = modifier
                .size(40.dp)
                .clickable {
                    if (isSelectedMode) {
                        onSelect(true)
                    }
                },
            model = Builder(LocalContext.current)
                .data(info)
                .crossfade(true)
                .build(),
            contentDescription = null,
        )
    }
}

@Composable
private fun AppContent(
    appItem: FilteredComponentItem,
    modifier: Modifier = Modifier,
) {
    Column(modifier) {
        Text(
            text = appItem.app.label,
            style = MaterialTheme.typography.bodyLarge,
        )
        Text(
            text = getComponentInfo(appItem = appItem),
            style = MaterialTheme.typography.bodyLarge,
        )
    }
}

@Composable
private fun getComponentInfo(
    appItem: FilteredComponentItem,
): String {
    var componentInfo = ""
    var count = 0
    if (appItem.service.isNotEmpty()) {
        componentInfo = stringResource(
            id = R.string.service_count,
            appItem.service.size,
        )
        count++
    }
    if (appItem.receiver.isNotEmpty()) {
        if (count != 0) {
            componentInfo += stringResource(id = R.string.delimiter)
        }
        componentInfo += stringResource(
            id = R.string.broadcast_count,
            appItem.receiver.size,
        )
        count++
    }
    if (appItem.activity.isNotEmpty()) {
        if (count != 0) {
            componentInfo += stringResource(id = R.string.delimiter)
        }
        componentInfo += stringResource(
            id = R.string.activity_count,
            appItem.activity.size,
        )
    }
    if (appItem.provider.isNotEmpty()) {
        if (count != 0) {
            componentInfo += stringResource(id = R.string.delimiter)
        }
        componentInfo += stringResource(
            id = R.string.content_provider_count,
            appItem.provider.size,
        )
    }
    return componentInfo
}

@Composable
@Preview
@Preview(uiMode = Configuration.UI_MODE_NIGHT_YES)
fun AppListItemPreview() {
    val componentInfo = ComponentInfo(
        name = "component",
        simpleName = "com",
        packageName = "blocker",
        type = ACTIVITY,
        exported = false,
        pmBlocked = false,
    )
    val filterAppItem = FilteredComponentItem(
        app = InstalledAppItem(
            packageName = "com.merxury.blocker",
            label = "Blocker",
            isSystem = false,
        ),
        isSelected = true,
        activity = listOf(componentInfo),
        service = listOf(componentInfo),
        receiver = listOf(componentInfo),
        provider = listOf(componentInfo),
    )
    BlockerTheme {
        Surface {
            AppListItem(
                filterAppItem = filterAppItem,
                isSelectedMode = true,
                switchSelectedMode = {},
                onSelect = {},
                onClick = {},
            )
        }
    }
}

@Composable
@Preview
fun AppListItemWithoutServicePreview() {
    val componentInfo = ComponentInfo(
        name = "component",
        simpleName = "com",
        packageName = "blocker",
        type = ACTIVITY,
        exported = false,
        pmBlocked = false,
    )
    val filterAppItem = FilteredComponentItem(
        app = InstalledAppItem(
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
            AppListItem(
                filterAppItem = filterAppItem,
                isSelectedMode = false,
                switchSelectedMode = {},
                onSelect = {},
                onClick = {},
            )
        }
    }
}
