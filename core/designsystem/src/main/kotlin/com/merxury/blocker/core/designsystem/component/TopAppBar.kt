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

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumTopAppBar
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.merxury.blocker.core.designsystem.icon.BlockerActionIcon
import com.merxury.blocker.core.designsystem.icon.BlockerIcons
import com.merxury.blocker.core.designsystem.theme.BlockerTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BlockerTopAppBar(
    modifier: Modifier = Modifier,
    title: String,
    hasNavigationIcon: Boolean = false,
    onNavigationClick: () -> Unit = {},
    actions: @Composable () -> Unit = {},
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
            if (hasNavigationIcon) {
                IconButton(onClick = onNavigationClick) {
                    Icon(
                        imageVector = BlockerIcons.Back,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        },
        actions = {
            actions()
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BlockerTopAppBar(
    modifier: Modifier = Modifier,
    title: String,
    progress: Int? = null,
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
        actions = {
            if (progress != 100 && progress != null) {
                Row(
                    modifier = Modifier.padding(end = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "$progress%",
                    )
                    BlockerLoadingWheel(
                        modifier = Modifier.size(36.dp),
                    )
                }
            }
        },
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
@Composable
fun BlockerMediumTopAppBar(
    modifier: Modifier = Modifier,
    title: String,
    navigation: @Composable () -> Unit,
    actions: @Composable () -> Unit,
) {
    MediumTopAppBar(
        title = {
            Row(
                modifier = modifier.fillMaxWidth(),
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
        colors = TopAppBarDefaults.mediumTopAppBarColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            scrolledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    )
}

@Preview("Top App Bar with actions and navigation")
@Composable
fun BlockerTopAppBarNaviActionsPreview() {
    BlockerTheme {
        BlockerTopAppBar(
            title = stringResource(id = android.R.string.untitled),
            hasNavigationIcon = true,
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

@Preview("Top App Bar with loading icon, 0%")
@Composable
fun BlockerTopAppBarLoadingStartPreview() {
    BlockerTheme {
        BlockerTopAppBar(
            title = stringResource(id = android.R.string.untitled),
            progress = 0,
        )
    }
}

@Preview("Top App Bar with loading icon, 50%")
@Composable
fun BlockerTopAppBarLoadingProgressPreview() {
    BlockerTheme {
        BlockerTopAppBar(
            title = stringResource(id = android.R.string.untitled),
            progress = 50,
        )
    }
}

@Preview("Top App Bar with loading icon, 100%")
@Composable
fun BlockerTopAppBarLoadingCompeletPreview() {
    BlockerTheme {
        BlockerTopAppBar(
            title = stringResource(id = android.R.string.untitled),
            progress = 100,
        )
    }
}

@Preview("Top App Bar without navigation and actions")
@Composable
fun BlockerHomeTopAppBarPreview() {
    BlockerTheme {
        BlockerTopAppBar(
            title = stringResource(id = android.R.string.untitled),
        )
    }
}

@Preview("Medium Top App Bar")
@Composable
fun BlockerMediumTopAppBarPreview() {
    BlockerTheme {
        BlockerMediumTopAppBar(
            title = stringResource(id = android.R.string.untitled),
            navigation = {
                IconButton(onClick = {}) {
                    BlockerActionIcon(
                        imageVector = BlockerIcons.Close,
                        contentDescription = null,
                    )
                }
            },
            actions = {
                IconButton(onClick = {}) {
                    BlockerActionIcon(
                        imageVector = BlockerIcons.SelectAll,
                        contentDescription = null,
                    )
                }
                IconButton(onClick = {}) {
                    BlockerActionIcon(
                        imageVector = BlockerIcons.Block,
                        contentDescription = null,
                    )
                }
                IconButton(onClick = {}) {
                    BlockerActionIcon(
                        imageVector = BlockerIcons.CheckCircle,
                        contentDescription = null,
                    )
                }
            },
        )
    }
}
