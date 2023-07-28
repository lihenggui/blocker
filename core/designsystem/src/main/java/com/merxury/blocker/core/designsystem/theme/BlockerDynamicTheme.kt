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

package com.merxury.blocker.core.designsystem.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import com.merxury.blocker.core.model.data.ThemingBasedIconState

const val MinContrastOfPrimaryVsSurface = 3f

@Composable
fun BlockerDynamicTheme(
    themingBasedIconState: ThemingBasedIconState,
    darkTheme: Boolean = isSystemInDarkTheme(),
    blockerTheme: Boolean = false,
    disableDynamicTheming: Boolean = true,
    content: @Composable () -> Unit,
) {
    val surfaceColor = MaterialTheme.colorScheme.surface
    val dominantColorState = rememberDominantColorState { color ->
        // We want a color which has sufficient contrast against the surface color
        color.contrastAgainst(surfaceColor) >= MinContrastOfPrimaryVsSurface
    }
    val imageBitmap = themingBasedIconState.icon
    val isDarkTheme = isSystemInDarkTheme()
    DynamicThemePrimaryColorsFromImage(
        dominantColorState = dominantColorState,
        darkTheme = darkTheme,
        blockerTheme = blockerTheme,
        disableDynamicTheming = disableDynamicTheming,
    ) {
        LaunchedEffect(disableDynamicTheming) {
            // Update the dominantColorState with colors coming from the podcast image URL
            if (imageBitmap != null && !disableDynamicTheming) {
                dominantColorState.updateColorsFromImageBitmap(imageBitmap, isDarkTheme)
            } else {
                dominantColorState.reset()
            }
        }
        content()
    }
}
