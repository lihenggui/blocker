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

package com.merxury.blocker.feature.globalsearch.component

import android.content.pm.PackageInfo
import android.content.res.Configuration
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest.Builder
import com.merxury.blocker.core.designsystem.icon.BlockerIcons
import com.merxury.blocker.core.designsystem.theme.BlockerTheme
import com.merxury.blocker.core.model.Application
import com.merxury.blocker.feature.globalsearch.R
import com.merxury.blocker.feature.globalsearch.model.FilterAppItem

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AppListItem(
    filterAppItem: FilterAppItem,
    modifier: Modifier = Modifier,
    isSelectedMode: Boolean,
    switchSelectedMode: (Boolean) -> Unit,
    onSelect: (Boolean) -> Unit,
    onClick: () -> Unit
) {
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
            .padding(horizontal = 8.dp, vertical = 4.dp)
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
                    },
                    onLongClick = {
                        if (!isSelectedMode) {
                            switchSelectedMode(true)
                        }
                    },
                )
                .background(
                    color = color,
                    shape = shape
                )
                .padding(horizontal = 8.dp, vertical = 10.dp)

        ) {
            AppIcon(
                info = filterAppItem.app.packageInfo,
                isSelectedMode = isSelectedMode,
                isSelected = filterAppItem.isSelected,
                onSelect = onSelect
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
    onSelect: (Boolean) -> Unit
) {
    if (isSelected) {
        IconButton(onClick = { onSelect(false) }) {
            Icon(
                imageVector = BlockerIcons.Check,
                modifier = modifier.size(40.dp),
                contentDescription = null
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
    appItem: FilterAppItem,
    modifier: Modifier = Modifier
) {
    var count = 0
    Column(modifier) {
        Text(
            text = appItem.app.label,
            style = MaterialTheme.typography.bodyLarge
        )
        Row(
            modifier = modifier,
            horizontalArrangement = Arrangement.Start
        ) {
            if (appItem.serviceCount != 0) {
                Text(
                    text = stringResource(
                        id = R.string.service_count,
                        appItem.serviceCount
                    ),
                    style = MaterialTheme.typography.bodyMedium
                )
                count++
            }
            if (appItem.broadcastCount != 0) {
                if (count != 0) {
                    Text(
                        text = stringResource(id = R.string.delimiter),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                Text(
                    text = stringResource(
                        id = R.string.broadcast_count,
                        appItem.broadcastCount
                    ),
                    style = MaterialTheme.typography.bodyMedium
                )

                count++
            }
            if (appItem.activityCount != 0) {
                if (count != 0) {
                    Text(
                        text = stringResource(id = R.string.delimiter),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                Text(
                    text = stringResource(
                        id = R.string.activity_count,
                        appItem.activityCount
                    ),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            if (appItem.contentProviderCount != 0) {
                if (count != 0) {
                    Text(
                        text = stringResource(id = R.string.delimiter),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                Text(
                    text = stringResource(
                        id = R.string.content_provider_count,
                        appItem.contentProviderCount
                    ),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}

@Composable
@Preview
@Preview(uiMode = Configuration.UI_MODE_NIGHT_YES)
fun AppListItemPreview() {
    val filterAppItem = FilterAppItem(
        app = Application(label = "Blocker"),
        activityCount = 20,
        broadcastCount = 2,
        serviceCount = 6,
        contentProviderCount = 12,
        isSelected = true
    )
    BlockerTheme {
        Surface {
            AppListItem(
                filterAppItem = filterAppItem,
                isSelectedMode = true,
                switchSelectedMode = {},
                onSelect = {},
                onClick = {}
            )
        }
    }
}

@Composable
@Preview
fun AppListItemWithoutServicePreview() {
    val filterAppItem = FilterAppItem(
        app = Application(label = "Blocker"),
        activityCount = 0,
        broadcastCount = 0,
        serviceCount = 0,
        contentProviderCount = 9
    )
    BlockerTheme {
        Surface {
            AppListItem(
                filterAppItem = filterAppItem,
                isSelectedMode = false,
                switchSelectedMode = {},
                onSelect = {},
                onClick = {}
            )
        }
    }
}
