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

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import com.merxury.blocker.core.designsystem.icon.BlockerIcons
import com.merxury.blocker.core.designsystem.theme.BlockerTheme
import kotlin.math.roundToInt

private val ContentPadding = 16.dp
private val Elevation = 4.dp
private val IconSize = 80.dp
private val CollapsedTitleSize = 22.dp
private val ExpandedTitleSize = 28.dp

@Composable
fun BlockerCollapsingTopAppBar(
    progress: Float,
    actions: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    val titleSize = with(LocalDensity.current) {
        lerp(CollapsedTitleSize.toPx(), ExpandedTitleSize.toPx(), progress).toDp()
    }

    Surface(
        color = MaterialTheme.colorScheme.background,
        modifier = modifier,
    ) {
        Box(
            modifier = Modifier
                .statusBarsPadding()
                .padding(horizontal = ContentPadding)
                .fillMaxSize(),
        ) {
            CollapsingToolbarLayout(progress = progress) {
                IconButton(onClick = {}) {
                    Icon(
                        imageVector = BlockerIcons.Back,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurface,
                    )
                }
                Row {
                    Column {
                        Text(
                            text = "Title",
                            modifier = Modifier
                                .size(titleSize),
                        )
                        Text(text = "Package name")
                        Text(text = "version code")
                    }
                    Image(
                        imageVector = BlockerIcons.Find,
                        contentDescription = null,
                        modifier = Modifier.size(IconSize),
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
    content: @Composable () -> Unit,
) {
    Layout(
        modifier = modifier,
        content = content,
    ) { measurables, constraints ->
        check(measurables.size == 4)

        val placeables = measurables.map {
            it.measure(constraints)
        }
        layout(
            width = constraints.maxWidth,
            height = constraints.maxHeight,
        ) {

            val expandedHorizontalGuideline = (constraints.maxHeight * 0.4f).roundToInt()
            val collapsedHorizontalGuideline = (constraints.maxHeight * 0.5f).roundToInt()

            val backIcon = placeables[0]
            val title = placeables[1]
//            val moreIcon = placeables[2]
            val packageName = placeables[2]
            val versionCode = placeables[3]
            backIcon.placeRelative(
                x = 0,
                y = 0,
            )
            title.placeRelative(
                x = lerp(
                    start = 0,
                    stop = backIcon.width + ContentPadding.roundToPx(),
                    fraction = progress,
                ),
                y = lerp(
                    start = collapsedHorizontalGuideline,
                    stop = 0,
                    fraction = progress,
                ),
            )
//            moreIcon.placeRelative(
//                x= constraints.maxWidth - moreIcon.width,
//                y = 0
//            )
            packageName.placeRelative(
                x = lerp(
                    start = 0,
                    stop = 0,
                    fraction = progress,
                ),
                y = lerp(
                    start = collapsedHorizontalGuideline + title.height,
                    stop = 0,
                    fraction = progress,
                ),
            )
            versionCode.placeRelative(
                x = lerp(
                    start = 0,
                    stop = 0,
                    fraction = progress,
                ),
                y = lerp(
                    start = collapsedHorizontalGuideline + title.height + packageName.height,
                    stop = 0,
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
            actions = {},
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
            actions = {},
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
            actions = {},
            modifier = Modifier
                .fillMaxWidth()
                .height(188.dp),
        )
    }
}