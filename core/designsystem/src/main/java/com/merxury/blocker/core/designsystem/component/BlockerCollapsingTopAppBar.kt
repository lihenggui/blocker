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

package com.merxury.blocker.core.designsystem.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.merxury.blocker.core.designsystem.R
import com.merxury.blocker.core.designsystem.icon.BlockerIcons
import com.merxury.blocker.core.designsystem.theme.BlockerTheme
import kotlin.math.roundToInt

private val contentPadding = 16.dp
private val appIconSize = 80.dp
private val collapsedTitleSize = 22.sp
private val expandedTitleSize = 28.sp
private val padding = 4.dp

val MinToolbarHeight = 64.dp
val MaxToolbarHeight = 188.dp

@Composable
fun BlockerCollapsingTopAppBar(
    modifier: Modifier = Modifier,
    progress: Float,
    onNavigationClick: () -> Unit = {},
    title: String,
    actions: @Composable RowScope.() -> Unit = {},
    subtitle: String,
    summary: String,
    iconSource: Any? = null,
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
                IconButton(
                    onClick = onNavigationClick,
                    modifier = Modifier.then(Modifier.size(24.dp)),
                ) {
                    Icon(
                        imageVector = BlockerIcons.Back,
                        contentDescription = stringResource(id = R.string.back),
                        tint = MaterialTheme.colorScheme.onSurface,
                    )
                }
                Text(
                    text = title,
                    fontSize = titleSize,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    modifier = Modifier
                        .fillMaxWidth(0.8f),
                    overflow = TextOverflow.Ellipsis,
                )
                Row(
                    modifier = Modifier.wrapContentSize(),
                    horizontalArrangement = Arrangement.spacedBy(contentPadding),
                ) {
                    actions()
                }
                Text(
                    text = subtitle,
                    maxLines = 1,
                    style = MaterialTheme.typography.bodySmall,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .padding(vertical = padding)
                        .fillMaxWidth(0.8f)
                        .graphicsLayer { alpha = ((progress - 0.25f) * 4).coerceIn(0f, 1f) },
                )
                Text(
                    text = summary,
                    maxLines = 1,
                    style = MaterialTheme.typography.bodySmall,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .padding(vertical = padding)
                        .fillMaxWidth(0.8f)
                        .graphicsLayer { alpha = ((progress - 0.25f) * 4).coerceIn(0f, 1f) },
                )
                AsyncImage(
                    modifier = Modifier
                        .size(appIconSize)
                        .graphicsLayer { alpha = ((progress - 0.25f) * 4).coerceIn(0f, 1f) },
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(iconSource)
                        .crossfade(true)
                        .build(),
                    contentDescription = null,
                )
            }
        }
    }
}

@Composable
private fun CollapsingToolbarLayout(
    progress: Float,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
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
            title.placeRelative(
                x = lerp(
                    start = navigationIcon.width + contentPadding.roundToPx(),
                    stop = 0,
                    fraction = progress,
                ),
                y = lerp(
                    start = MinToolbarHeight.roundToPx() / 2 - title.height / 2,
                    stop = expandedHorizontalGuideline + title.height / 2,
                    fraction = progress,
                ),
            )
            actionsIcon.placeRelative(
                x = constraints.maxWidth - actionsIcon.width,
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
fun CollapsingToolbarCollapsedPreview() {
    BlockerTheme {
        BlockerCollapsingTopAppBar(
            progress = 0f,
            title = "Title",
            actions = {
                IconButton(
                    onClick = {},
                    modifier = Modifier.then(Modifier.size(24.dp)),
                ) {
                    Icon(
                        imageVector = BlockerIcons.Search,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurface,
                    )
                }
                IconButton(
                    onClick = {},
                    modifier = Modifier.then(Modifier.size(24.dp)),
                ) {
                    Icon(
                        imageVector = BlockerIcons.More,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurface,
                    )
                }
            },
            subtitle = "packageName",
            summary = "versionCode",
            iconSource = R.drawable.ic_android,
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp),
        )
    }
}

@Preview
@Composable
fun CollapsingToolbarHalfwayPreview() {
    BlockerTheme {
        BlockerCollapsingTopAppBar(
            progress = 0.5f,
            title = "Title",
            actions = {
                IconButton(
                    onClick = {},
                    modifier = Modifier.then(Modifier.size(24.dp)),
                ) {
                    Icon(
                        imageVector = BlockerIcons.Search,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurface,
                    )
                }
                IconButton(
                    onClick = {},
                    modifier = Modifier.then(Modifier.size(24.dp)),
                ) {
                    Icon(
                        imageVector = BlockerIcons.More,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurface,
                    )
                }
            },
            subtitle = "packageName",
            summary = "versionCode",
            iconSource = R.drawable.ic_android,
            modifier = Modifier
                .fillMaxWidth()
                .height(94.dp),
        )
    }
}

@Preview
@Composable
fun CollapsingToolbarExpandedPreview() {
    BlockerTheme {
        BlockerCollapsingTopAppBar(
            progress = 1f,
            title = "Title with long name 0123456789",
            actions = {
                IconButton(
                    onClick = {},
                    modifier = Modifier.then(Modifier.size(24.dp)),
                ) {
                    Icon(
                        imageVector = BlockerIcons.Search,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurface,
                    )
                }
                IconButton(
                    onClick = {},
                    modifier = Modifier.then(Modifier.size(24.dp)),
                ) {
                    Icon(
                        imageVector = BlockerIcons.More,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurface,
                    )
                }
            },
            subtitle = "packageName with long long long name 0123456789",
            summary = "versionCode with long long long name 0123456789",
            iconSource = R.drawable.ic_android,
            modifier = Modifier
                .fillMaxWidth()
                .height(188.dp),
        )
    }
}
