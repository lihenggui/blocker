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
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import com.merxury.blocker.core.model.data.IconBasedThemingState

const val MIN_CONTRAST_OF_PRIMARY_VS_SURFACE = 3f

@Composable
fun BlockerDynamicTheme(
    iconBasedThemingState: IconBasedThemingState,
    darkTheme: Boolean = isSystemInDarkTheme(),
    defaultTheme: Boolean = false,
    disableDynamicTheming: Boolean = true,
    content: @Composable () -> Unit,
) {
    val defaultColorScheme = when {
        !disableDynamicTheming && supportsDynamicTheming() -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        else -> if (darkTheme) DarkBlockerColorScheme else LightBlockerColorScheme
    }
    val dominantColorState = rememberDominantColorState(
        colorScheme = defaultColorScheme,
    ) { color ->
        // We want a color which has sufficient contrast against the surface color
        color.contrastAgainst(defaultColorScheme.surface) >= MIN_CONTRAST_OF_PRIMARY_VS_SURFACE
    }
    val icon = iconBasedThemingState.icon
    val isDarkTheme = isSystemInDarkTheme()
    DynamicThemePrimaryColorsFromImage(
        defaultColorScheme = defaultColorScheme,
        dominantColorState = dominantColorState,
        useDarkTheme = darkTheme,
        useBlockerTheme = defaultTheme,
        disableDynamicTheming = disableDynamicTheming,
    ) {
        LaunchedEffect(icon) {
            // Update the dominantColorState with colors coming from the podcast image URL
            if (icon != null && !disableDynamicTheming) {
                dominantColorState.updateColorsFromImageBitmap(icon, isDarkTheme)
            } else {
                dominantColorState.reset()
            }
        }
        content()
    }
}
