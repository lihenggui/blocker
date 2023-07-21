package com.merxury.blocker.core.ui.dynamictheme

import android.content.Context
import android.graphics.Bitmap
import androidx.collection.LruCache
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
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
import com.merxury.blocker.core.designsystem.theme.BlockerTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.floor

@Composable
fun rememberDominantColorState(
    context: Context = LocalContext.current,
    defaultColor: Color = MaterialTheme.colorScheme.primary,
    defaultOnColor: Color = MaterialTheme.colorScheme.surfaceVariant,
    defaultSurfaceColor: Color = MaterialTheme.colorScheme.surface,
    cacheSize: Int = 12,
    isColorValid: (Color) -> Boolean = { true },
): DominantColorState = remember {
    DominantColorState(
        context,
        defaultColor,
        defaultOnColor,
        defaultSurfaceColor,
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
    content: @Composable () -> Unit,
) {
    val colors = MaterialTheme.colorScheme.copy(
        primary = animateColorAsState(
            dominantColorState.color,
            spring(stiffness = Spring.StiffnessLow),
            label = "primary",
        ).value,
        surfaceVariant = animateColorAsState(
            dominantColorState.onColor,
            spring(stiffness = Spring.StiffnessLow),
            label = "surfaceVariant",
        ).value,
        surface = animateColorAsState(
            dominantColorState.surfaceColor,
            spring(stiffness = Spring.StiffnessLow),
            label = "surface",
        ).value,
    )
    BlockerTheme(setColorScheme = colors, content = content)
}

/**
 * A class which stores and caches the result of any calculated dominant colors
 * from images.
 *
 * @param context Android context
 * @param defaultColor The default color, which will be used if [calculateDominantColor] fails to
 * calculate a dominant color
 * @param defaultOnColor The default foreground 'on color' for [defaultColor].
 * @param cacheSize The size of the [LruCache] used to store recent results. Pass `0` to
 * disable the cache.
 * @param isColorValid A lambda which allows filtering of the calculated image colors.
 */
@Stable
class DominantColorState(
    private val context: Context,
    private val defaultColor: Color,
    private val defaultOnColor: Color,
    private val defaultSurfaceColor: Color,
    cacheSize: Int = 12,
    private val isColorValid: (Color) -> Boolean = { true },
) {
    var color by mutableStateOf(defaultColor)
        private set
    var onColor by mutableStateOf(defaultOnColor)
        private set

    var surfaceColor by mutableStateOf(defaultSurfaceColor)
        private set

    private val cache = when {
        cacheSize > 0 -> LruCache<String, DominantColors>(cacheSize)
        else -> null
    }

    suspend fun updateColorsFromImageBitmap(bitmap: Bitmap) {
        val result = calculateDominantColor(bitmap)
        color = result?.color ?: defaultColor
        onColor = result?.onColor ?: defaultOnColor
        surfaceColor = result?.surfaceColor ?: defaultSurfaceColor
    }

    private suspend fun calculateDominantColor(bitmap: Bitmap): DominantColors? {
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
                DominantColors(
                    color = Color(swatch.rgb),
                    onColor = Color(changeColor(swatch.rgb)),
                    surfaceColor = Color(changeColor(swatch.rgb)),
                )
            }
            // Cache the resulting [DominantColors]
            ?.also { result -> cache?.put(bitmap.toString(), result) }
    }

    /**
     * Reset the color values to [defaultColor].
     */
    fun reset() {
        color = defaultColor
        onColor = defaultColor
        surfaceColor = defaultSurfaceColor
    }
}

@Immutable
private data class DominantColors(val color: Color, val onColor: Color, val surfaceColor: Color)

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

private fun changeColor(rgb: Int): Int {
    var red = rgb shr 16 and 0xFF
    var green = rgb shr 8 and 0xFF
    var blue = rgb and 0xFF
    red = floor(red * (1 - 0.2)).toInt()
    green = floor(green * (1 - 0.2)).toInt()
    blue = floor(blue * (1 - 0.2)).toInt()
    return android.graphics.Color.argb(80, red, green, blue)
}
