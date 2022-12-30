/*
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarColors
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.sp
import com.merxury.blocker.core.designsystem.icon.BlockerIcons

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BlockerTopAppBar(
    title: String,
    navigationIcon: ImageVector,
    navigationIconContentDescription: String?,
    actionIconFirst: ImageVector,
    actionIconContentDescriptionFirst: String?,
    actionIconSecond: ImageVector,
    actionIconContentDescriptionSecond: String?,
    modifier: Modifier = Modifier,
    colors: TopAppBarColors = TopAppBarDefaults.smallTopAppBarColors(),
    onNavigationClick: () -> Unit = {},
    onFirstActionClick: () -> Unit = {},
    onSecondActionClick: () -> Unit = {}
) {
    TopAppBar(
        modifier = modifier,
        title = {
            AutoResizeText(
                text = title,
                FontSizeRange(5.sp, 22.sp),
                maxLines = 2
            )
        },
        navigationIcon = {
            IconButton(onClick = onNavigationClick) {
                Icon(
                    imageVector = navigationIcon,
                    contentDescription = navigationIconContentDescription,
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }
        },
        actions = {
            IconButton(onClick = onFirstActionClick) {
                Icon(
                    imageVector = actionIconFirst,
                    contentDescription = actionIconContentDescriptionFirst,
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }
            IconButton(onClick = onSecondActionClick) {
                Icon(
                    imageVector = actionIconSecond,
                    contentDescription = actionIconContentDescriptionSecond,
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }
        },
        colors = colors
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BlockerTopAppBar(
    @StringRes titleRes: Int,
    actionIconFirst: ImageVector,
    actionIconContentDescriptionFirst: String?,
    actionIconSecond: ImageVector,
    actionIconContentDescriptionSecond: String?,
    modifier: Modifier = Modifier,
    colors: TopAppBarColors = TopAppBarDefaults.smallTopAppBarColors(),
    onFirstActionClick: () -> Unit = {},
    onSecondActionClick: () -> Unit = {}
) {
    TopAppBar(
        modifier = modifier,
        title = {
            AutoResizeText(
                text = stringResource(id = titleRes),
                FontSizeRange(5.sp, 22.sp),
                maxLines = 2
            )
        },
        actions = {
            IconButton(onClick = onFirstActionClick) {
                Icon(
                    imageVector = actionIconFirst,
                    contentDescription = actionIconContentDescriptionFirst,
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }
            IconButton(onClick = onSecondActionClick) {
                Icon(
                    imageVector = actionIconSecond,
                    contentDescription = actionIconContentDescriptionSecond,
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }
        },
        colors = colors
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
                maxLines = 2
            )
        },
        navigationIcon = {
            IconButton(onClick = onNavigationClick) {
                Icon(
                    imageVector = BlockerIcons.Back,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }
        },
        colors = colors
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Preview("Top App Bar with navigation icon")
@Composable
fun BlockerTopAppBarPreview() {
    BlockerTopAppBar(
        title = stringResource(id = string.untitled),
        navigationIcon = BlockerIcons.Back,
        navigationIconContentDescription = "Navigation icon",
        actionIconFirst = BlockerIcons.Search,
        actionIconContentDescriptionFirst = "First action icon",
        actionIconSecond = BlockerIcons.MoreVert,
        actionIconContentDescriptionSecond = "Second action icon"
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Preview("Top App Bar without navigation icon")
@Composable
fun BlockerTopAppBarWithoutNavPreview() {
    BlockerTopAppBar(
        titleRes = string.untitled,
        actionIconFirst = BlockerIcons.Search,
        actionIconContentDescriptionFirst = "First action icon",
        actionIconSecond = BlockerIcons.MoreVert,
        actionIconContentDescriptionSecond = "Second action icon"
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Preview("Top App Bar only with navigation icon")
@Composable
fun BlockerTopAppBarWithNavPreview() {
    BlockerTopAppBar(
        titleRes = string.untitled,
        onNavigationClick = {}
    )
}