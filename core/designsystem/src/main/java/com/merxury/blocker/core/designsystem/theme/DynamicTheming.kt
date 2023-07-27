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

import android.content.Context
import android.graphics.Bitmap
import androidx.collection.LruCache
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.palette.graphics.Palette
import com.google.android.material.color.MaterialColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun rememberDominantColorState(
    context: Context = LocalContext.current,
    defaultPrimary: Color = MaterialTheme.colorScheme.primary,
    defaultSurfaceColor: Color = MaterialTheme.colorScheme.surface,
    defaultSurfaceVariantColor: Color = MaterialTheme.colorScheme.surfaceVariant,
    defaultOnPrimaryContainerColor: Color = MaterialTheme.colorScheme.onPrimaryContainer,
    defaultPrimaryContainerColor: Color = MaterialTheme.colorScheme.primaryContainer,
    cacheSize: Int = 12,
    isColorValid: (Color) -> Boolean = { true },
): DominantColorState = remember {
    DominantColorState(
        context,
        defaultPrimary,
        defaultSurfaceColor,
        defaultSurfaceVariantColor,
        defaultPrimaryContainerColor,
        defaultOnPrimaryContainerColor,
        cacheSize,
        isColorValid,
    )
}

/**
 * A composable which allows dynamic theming of the [androidx.compose.material3.MaterialTheme.colorScheme]
 * color from an image.
 */
@Composable
fun DynamicThemePrimaryColorsFromImage(
    dominantColorState: DominantColorState = rememberDominantColorState(),
    darkTheme: Boolean = isSystemInDarkTheme(),
    blockerTheme: Boolean = false,
    disableDynamicTheming: Boolean = true,
    content: @Composable () -> Unit,
) {
    val colors = MaterialTheme.colorScheme.copy(
        primary = animateColorAsState(
            dominantColorState.primaryColor,
            spring(stiffness = Spring.StiffnessLow),
            label = "primary",
        ).value,
        surface = animateColorAsState(
            dominantColorState.surfaceColor,
            spring(stiffness = Spring.StiffnessLow),
            label = "surface",
        ).value,
        surfaceVariant = animateColorAsState(
            dominantColorState.surfaceVariantColor,
            spring(stiffness = Spring.StiffnessLow),
            label = "onSurfaceVariant",
        ).value,
        onPrimaryContainer = animateColorAsState(
            dominantColorState.onPrimaryContainerColor,
            spring(stiffness = Spring.StiffnessLow),
            label = "onPrimaryContainerColor",
        ).value,
        primaryContainer = animateColorAsState(
            dominantColorState.primaryContainerColor,
            spring(stiffness = Spring.StiffnessLow),
            label = "primaryContainerColor",
        ).value,
    )
    BlockerTheme(
        setColorScheme = colors,
        darkTheme = darkTheme,
        blockerTheme = blockerTheme,
        disableDynamicTheming = disableDynamicTheming,
        content = content,
    )
}

/**
 * A class which stores and caches the result of any calculated dominant colors
 * from images.
 *
 * @param context Android context
 * @param defaultPrimaryColor The default color, which will be used if [calculateDominantColor] fails to
 * calculate a dominant color
 * @param cacheSize The size of the [LruCache] used to store recent results. Pass `0` to
 * disable the cache.
 * @param isColorValid A lambda which allows filtering of the calculated image colors.
 */
@Stable
class DominantColorState(
    private val context: Context,
    private val defaultPrimaryColor: Color,
    private val defaultSurfaceColor: Color,
    private val defaultSurfaceVariantColor: Color,
    private val defaultPrimaryContainerColor: Color,
    private val defaultOnPrimaryContainerColor: Color,
    cacheSize: Int = 12,
    private val isColorValid: (Color) -> Boolean = { true },
) {
    var primaryColor by mutableStateOf(defaultPrimaryColor)
        private set

    var surfaceColor by mutableStateOf(defaultSurfaceColor)
        private set

    var surfaceVariantColor by mutableStateOf(defaultSurfaceVariantColor)
        private set

    var primaryContainerColor by mutableStateOf(defaultPrimaryContainerColor)
        private set

    var onPrimaryContainerColor by mutableStateOf(defaultOnPrimaryContainerColor)
        private set

    private val cache = when {
        cacheSize > 0 -> LruCache<String, DominantColors>(cacheSize)
        else -> null
    }

    suspend fun updateColorsFromImageBitmap(bitmap: Bitmap, isSystemInDarkTheme: Boolean) {
        val result = calculateDominantColor(bitmap, isSystemInDarkTheme)
        primaryColor = result?.primary ?: defaultPrimaryColor
        surfaceColor = result?.surface ?: defaultSurfaceColor
        surfaceVariantColor = result?.surfaceVariant ?: defaultSurfaceVariantColor
        primaryContainerColor = result?.primaryContainer ?: defaultPrimaryContainerColor
        onPrimaryContainerColor = result?.onPrimaryContainer ?: defaultOnPrimaryContainerColor
    }

    private suspend fun calculateDominantColor(
        bitmap: Bitmap,
        isSystemInDarkTheme: Boolean,
    ): DominantColors? {
        val cached = cache?.get(bitmap.toString())
        if (cached != null) {
            // If we already have the result cached, return early now...
            return cached
        }

        // Otherwise we calculate the swatches in the image, and return the first valid color
        return calculateSwatchesInImage(bitmap)
            // First we want to sort the list by the color's population
            .sortedByDescending { swatch -> swatch.rgb }
            // Then we want to find the first valid color
            .firstOrNull { swatch -> isColorValid(Color(swatch.rgb)) }
            // If we found a valid swatch, wrap it in a [DominantColors]
            ?.let { swatch ->
                val colorRoles = MaterialColors.getColorRoles(swatch.rgb, !isSystemInDarkTheme)
                DominantColors(
                    primary = Color(colorRoles.accent),
                    surface = Color(colorRoles.accentContainer),
                    surfaceVariant = Color(colorRoles.accentContainer),
                    primaryContainer = Color(colorRoles.accentContainer),
                    onPrimaryContainer = Color(colorRoles.accent),
                )
            }
            // Cache the resulting [DominantColors]
            ?.also { result -> cache?.put(bitmap.toString(), result) }
    }

    /**
     * Reset the color values to [defaultPrimaryColor].
     */
    fun reset() {
        primaryColor = defaultPrimaryColor
        surfaceColor = defaultSurfaceColor
        surfaceVariantColor = defaultSurfaceVariantColor
        primaryContainerColor = defaultPrimaryContainerColor
        onPrimaryContainerColor = defaultOnPrimaryContainerColor
    }
}

@Immutable
private data class DominantColors(
    val primary: Color,
    val surface: Color,
    val surfaceVariant: Color,
    val primaryContainer: Color,
    val onPrimaryContainer: Color,
)

/**
 * Fetches the given [bitmap] with Coil, then uses [Palette] to calculate the dominant color.
 */
private suspend fun calculateSwatchesInImage(
    bitmap: Bitmap?,
): List<Palette.Swatch> {
    return bitmap?.let {
        withContext(Dispatchers.Default) {
            val palette = Palette.Builder(bitmap)
                // Disable any bitmap resizing in Palette. We've already loaded an appropriately
                // sized bitmap through Coil
                .resizeBitmapArea(0)
                // Clear any built-in filters. We want the unfiltered dominant color
                .clearFilters()
                // We reduce the maximum color count down to 8
                .maximumColorCount(8)
                .generate()

            palette.swatches
        }
    } ?: emptyList()
}
