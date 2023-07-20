package com.merxury.blocker.core.ui.dynamictheme

import android.graphics.Bitmap
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import com.merxury.blocker.core.designsystem.theme.MinContrastOfPrimaryVsSurface
import com.merxury.blocker.core.designsystem.theme.contrastAgainst

@Composable
fun DetailDynamicTheme(
    imageBitmap: Bitmap?,
    content: @Composable () -> Unit,
) {
    val surfaceColor = MaterialTheme.colorScheme.surface
    val dominantColorState = rememberDominantColorState(
        defaultColor = MaterialTheme.colorScheme.surface,
    ) { color ->
        // We want a color which has sufficient contrast against the surface color
        color.contrastAgainst(surfaceColor) >= MinContrastOfPrimaryVsSurface
    }
    DynamicThemePrimaryColorsFromImage(dominantColorState) {
        // Update the dominantColorState with colors coming from the podcast image URL
        LaunchedEffect(imageBitmap) {
            if (imageBitmap != null) {
                dominantColorState.updateColorsFromImageBitmap(imageBitmap)
            } else {
                dominantColorState.reset()
            }
        }
        content()
    }
}
