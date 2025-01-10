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

package com.merxury.blocker.core.designsystem.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.merxury.blocker.core.designsystem.R
import com.merxury.blocker.core.designsystem.icon.BlockerActionIcon
import com.merxury.blocker.core.designsystem.icon.BlockerIcons
import com.merxury.blocker.core.designsystem.theme.BlockerTheme
import kotlin.math.roundToInt

private val contentPadding = 16.dp
private val appIconSize = 80.dp
private val collapsedTitleSize = 22.sp
private val expandedTitleSize = 28.sp
private val padding = 4.dp
private val minNavWidth = 1.dp

val MinToolbarHeight = 64.dp
val MaxToolbarHeight = 188.dp

@Composable
fun BlockerCollapsingTopAppBar(
    progress: Float,
    title: String,
    subtitle: String,
    summary: String,
    modifier: Modifier = Modifier,
    showBackButton: Boolean = true,
    onNavigationClick: () -> Unit = {},
    actions: (@Composable RowScope.() -> Unit)? = {},
    iconSource: Any? = null,
    onIconClick: () -> Unit = {},
) {
    val titleSize = with(LocalDensity.current) {
        lerp(collapsedTitleSize.toPx(), expandedTitleSize.toPx(), progress).toSp()
    }

    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = modifier,
    ) {
        Box(
            modifier = Modifier
                .statusBarsPadding()
                .padding(horizontal = contentPadding)
                .fillMaxSize(),
        ) {
            CollapsingToolbarLayout(progress = progress) {
                if (showBackButton) {
                    IconButton(
                        onClick = onNavigationClick,
                        modifier = Modifier.then(Modifier.size(24.dp)),
                    ) {
                        Icon(
                            imageVector = BlockerIcons.Back,
                            contentDescription = stringResource(id = R.string.core_designsystem_back),
                            tint = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                } else {
                    Spacer(modifier = Modifier.width(minNavWidth))
                }
                BlockerBodyLargeText(
                    text = title,
                    maxLines = 2,
                    fontSize = titleSize,
                    modifier = Modifier.fillMaxWidth(0.7f),
                )
                Row {
                    if (actions != null) {
                        actions()
                    }
                }
                BlockerBodySmallText(
                    text = subtitle,
                    maxLines = 2,
                    modifier = Modifier
                        .padding(vertical = padding)
                        .fillMaxWidth(0.7f)
                        .graphicsLayer { alpha = ((progress - 0.25f) * 4).coerceIn(0f, 1f) },
                )
                BlockerBodySmallText(
                    text = summary,
                    maxLines = 2,
                    modifier = Modifier
                        .padding(vertical = padding)
                        .fillMaxWidth(0.7f)
                        .graphicsLayer { alpha = ((progress - 0.25f) * 4).coerceIn(0f, 1f) },
                )
                Box(
                    modifier = Modifier.wrapContentSize(),
                    contentAlignment = Alignment.CenterEnd,
                ) {
                    AsyncImage(
                        modifier = Modifier
                            .size(appIconSize)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = ripple(bounded = false),
                                onClick = { onIconClick() },
                            )
                            .graphicsLayer { alpha = ((progress - 0.25f) * 4).coerceIn(0f, 1f) },
                        model = ImageRequest
                            .Builder(LocalContext.current)
                            .data(iconSource)
                            .error(BlockerIcons.Android)
                            .placeholder(BlockerIcons.Android)
                            .build(),
                        contentDescription = null,
                    )
                }
            }
        }
    }
}

@Composable
private fun CollapsingToolbarLayout(
    progress: Float,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit = {},
) {
    Layout(
        modifier = modifier,
        content = content,
    ) { measurables, constraints ->
        check(measurables.size == 6)

        val placeables = measurables.map {
            it.measure(constraints)
        }
        layout(
            width = constraints.maxWidth,
            height = constraints.maxHeight,
        ) {
            val expandedHorizontalGuideline = (constraints.maxHeight * 0.4f).roundToInt()
            val collapsedHorizontalGuideline = (constraints.maxHeight * 0.5f).roundToInt()

            val navigationIcon = placeables[0]
            val title = placeables[1]
            val actionsIcon = placeables[2]
            val subtitle = placeables[3]
            val summary = placeables[4]
            val icon = placeables[5]
            navigationIcon.placeRelative(
                x = 0,
                y = MinToolbarHeight.roundToPx() / 2 - navigationIcon.height / 2,
            )
            val navWidth = navigationIcon.width
            val space = if (navWidth == minNavWidth.roundToPx()) {
                0
            } else {
                contentPadding.roundToPx()
            }
            title.placeRelative(
                x = lerp(
                    start = navWidth + space,
                    stop = 0,
                    fraction = progress,
                ),
                y = lerp(
                    start = MinToolbarHeight.roundToPx() / 2 - title.height / 2,
                    stop = expandedHorizontalGuideline + title.height / 4,
                    fraction = progress,
                ),
            )
            actionsIcon.placeRelative(
                x = navigationIcon.width,
                y = MinToolbarHeight.roundToPx() / 2 - actionsIcon.height / 2,
            )
            subtitle.placeRelative(
                x = lerp(
                    start = navigationIcon.width + contentPadding.roundToPx(),
                    stop = 0,
                    fraction = progress,
                ),
                y = lerp(
                    start = collapsedHorizontalGuideline + title.height - subtitle.height / 2,
                    stop = expandedHorizontalGuideline + title.height + subtitle.height / 2,
                    fraction = progress,
                ),
            )
            summary.placeRelative(
                x = lerp(
                    start = navigationIcon.width + contentPadding.roundToPx(),
                    stop = 0,
                    fraction = progress,
                ),
                y = lerp(
                    start = collapsedHorizontalGuideline + title.height + subtitle.height,
                    stop = expandedHorizontalGuideline + title.height + subtitle.height + summary.height / 4,
                    fraction = progress,
                ),
            )
            icon.placeRelative(
                x = lerp(
                    start = constraints.maxWidth - icon.width,
                    stop = constraints.maxWidth - icon.width,
                    fraction = progress,
                ),
                y = lerp(
                    start = collapsedHorizontalGuideline + title.height,
                    stop = expandedHorizontalGuideline,
                    fraction = progress,
                ),
            )
        }
    }
}

@Preview
@Composable
private fun CollapsingToolbarCollapsedPreview() {
    BlockerTheme {
        BlockerCollapsingTopAppBar(
            progress = 0f,
            title = "Title name",
            actions = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(
                        onClick = { },
                    ) {
                        BlockerActionIcon(
                            imageVector = BlockerIcons.Search,
                            contentDescription = stringResource(id = R.string.core_designsystem_search_icon),
                        )
                    }
                    BlockerAppTopBarMenu(
                        menuIcon = BlockerIcons.MoreVert,
                        menuIconDesc = R.string.core_designsystem_more_icon,
                        menuList = listOf(),
                    )
                }
            },
            subtitle = "packageName",
            summary = "versionCode",
            iconSource = R.drawable.core_designsystem_ic_android,
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp),
        )
    }
}

@Preview
@Composable
private fun CollapsingToolbarHalfwayPreview() {
    BlockerTheme {
        BlockerCollapsingTopAppBar(
            progress = 0.5f,
            title = "Title with long name 0123456789",
            showBackButton = false,
            actions = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(
                        onClick = { },
                    ) {
                        BlockerActionIcon(
                            imageVector = BlockerIcons.Search,
                            contentDescription = stringResource(id = R.string.core_designsystem_search_icon),
                        )
                    }
                    BlockerAppTopBarMenu(
                        menuIcon = BlockerIcons.MoreVert,
                        menuIconDesc = R.string.core_designsystem_more_icon,
                        menuList = listOf(),
                    )
                }
            },
            subtitle = "packageName",
            summary = "versionCode",
            iconSource = R.drawable.core_designsystem_ic_android,
            modifier = Modifier
                .fillMaxWidth()
                .height(94.dp),
        )
    }
}

@PreviewThemes
@Composable
private fun CollapsingToolbarExpandedPreview() {
    BlockerTheme {
        BlockerCollapsingTopAppBar(
            progress = 1f,
            title = "Title with long name 0123456789",
            actions = {
                BlockerSearchTextField(
                    searchQuery = "blocker",
                    onSearchQueryChange = {},
                    onSearchTrigger = {},
                    modifier = Modifier.weight(1f),
                )
                BlockerAppTopBarMenu(
                    menuIcon = BlockerIcons.MoreVert,
                    menuIconDesc = R.string.core_designsystem_more_icon,
                    menuList = listOf(),
                )
            },
            subtitle = "packageName with long long long name 0123456789",
            summary = "versionCode with long long long name 0123456789",
            iconSource = R.drawable.core_designsystem_ic_android,
            modifier = Modifier
                .fillMaxWidth()
                .height(188.dp),
        )
    }
}
