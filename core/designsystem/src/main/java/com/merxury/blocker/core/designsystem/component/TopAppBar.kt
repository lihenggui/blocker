/*
 * Copyright 2023 Blocker
 * Copyright 2022 The Android Open Source Project
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

package com.merxury.blocker.core.designsystem.component

import android.R.string
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarColors
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.merxury.blocker.core.designsystem.icon.BlockerIcons
import com.merxury.blocker.core.designsystem.theme.BlockerTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BlockerTopAppBar(
    title: String,
    navigationIcon: ImageVector,
    navigationIconContentDescription: String?,
    actions: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    colors: TopAppBarColors = TopAppBarDefaults.smallTopAppBarColors(),
    onNavigationClick: () -> Unit = {},
) {
    TopAppBar(
        modifier = modifier,
        title = {
            AutoResizeText(
                text = title,
                FontSizeRange(5.sp, 22.sp),
                maxLines = 2,
            )
        },
        navigationIcon = {
            IconButton(onClick = onNavigationClick) {
                Icon(
                    imageVector = navigationIcon,
                    contentDescription = navigationIconContentDescription,
                    tint = MaterialTheme.colorScheme.onSurface,
                )
            }
        },
        actions = {
            actions()
        },
        colors = colors,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BlockerHomeTopAppBar(
    @StringRes titleRes: Int,
    actions: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    colors: TopAppBarColors = TopAppBarDefaults.smallTopAppBarColors(),
) {
    TopAppBar(
        modifier = modifier,
        title = {
            AutoResizeText(
                text = stringResource(id = titleRes),
                FontSizeRange(5.sp, 22.sp),
                maxLines = 2,
            )
        },
        actions = {
            actions()
        },
        colors = colors,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BlockerTopAppBar(
    @StringRes titleRes: Int,
    onNavigationClick: () -> Unit,
    modifier: Modifier = Modifier,
    colors: TopAppBarColors = TopAppBarDefaults.smallTopAppBarColors(),
) {
    TopAppBar(
        modifier = modifier,
        title = {
            AutoResizeText(
                text = stringResource(id = titleRes),
                FontSizeRange(5.sp, 22.sp),
                maxLines = 2,
            )
        },
        navigationIcon = {
            IconButton(onClick = onNavigationClick) {
                Icon(
                    imageVector = BlockerIcons.Back,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurface,
                )
            }
        },
        colors = colors,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BlockerCollapsingTopAppBar(
    modifier: Modifier = Modifier,
    title: String,
    content: @Composable () -> Unit = {},
    isCollapsed: Boolean,
    scrollBehavior: TopAppBarScrollBehavior,
    actions: @Composable () -> Unit,
    onNavigationClick: () -> Unit = {},
) {
    LargeTopAppBar(
        title = {
            Row(
                modifier = modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text(
                        text = title,
                        maxLines = 2,
                    )
                    if (!isCollapsed) {
                        content()
                    }
                }
            }
        },
        navigationIcon = {
            IconButton(onClick = onNavigationClick) {
                Icon(
                    imageVector = BlockerIcons.Back,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurface,
                )
            }
        },
        actions = {
            actions()
        },
        scrollBehavior = scrollBehavior,
        colors = TopAppBarDefaults.largeTopAppBarColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            scrolledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BlockerLargeTopAppBar(
    modifier: Modifier = Modifier,
    title: String,
    navigation: @Composable () -> Unit,
    actions: @Composable () -> Unit,
) {
    LargeTopAppBar(
        title = {
            Row(
                modifier = modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text(text = title)
                }
            }
        },
        navigationIcon = {
            navigation()
        },
        actions = {
            actions()
        },
        colors = TopAppBarDefaults.largeTopAppBarColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            scrolledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Preview("Top App Bar")
@Composable
fun BlockerTopAppBarPreview() {
    BlockerTheme {
        BlockerTopAppBar(
            title = stringResource(id = string.untitled),
            navigationIcon = BlockerIcons.Back,
            navigationIconContentDescription = "Navigation icon",
            actions = {
                IconButton(onClick = {}) {
                    Icon(
                        imageVector = BlockerIcons.Search,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurface,
                    )
                }
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Preview("Home Top App Bar")
@Composable
fun BlockerHomeTopAppBarPreview() {
    BlockerTheme {
        BlockerHomeTopAppBar(
            titleRes = string.untitled,
            actions = {},
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Preview("Top App Bar only with navigation icon")
@Composable
fun BlockerTopAppBarWithNavPreview() {
    BlockerTheme {
        BlockerTopAppBar(
            titleRes = string.untitled,
            onNavigationClick = {},
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Preview("Collapsing Top App Bar ")
@Composable
fun BlockerCollapsingTopAppBarPreview() {
    val scrollBehavior =
        TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
    BlockerTheme {
        BlockerCollapsingTopAppBar(
            title = "Blocker",
            content = {
                Text(text = "app.packageName", style = MaterialTheme.typography.bodyMedium)
                Text(text = "1.0.0", style = MaterialTheme.typography.bodyMedium)
            },
            isCollapsed = false,
            scrollBehavior = scrollBehavior,
            actions = {
                IconButton(onClick = {}) {
                    Icon(
                        imageVector = BlockerIcons.Share,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurface,
                    )
                }
                IconButton(onClick = {}) {
                    Icon(
                        imageVector = BlockerIcons.Find,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurface,
                    )
                }
            },
        )
    }
}
