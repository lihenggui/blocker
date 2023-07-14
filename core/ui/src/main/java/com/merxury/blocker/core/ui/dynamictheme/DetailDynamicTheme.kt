package com.merxury.blocker.core.ui.dynamictheme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import com.merxury.blocker.core.designsystem.theme.MinContrastOfPrimaryVsSurface
import com.merxury.blocker.core.designsystem.theme.contrastAgainst

@Composable
fun DetailDynamicTheme(
    podcastImageUrl: String,
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
        LaunchedEffect(podcastImageUrl) {
            if (podcastImageUrl.isNotEmpty()) {
                dominantColorState.updateColorsFromImageUrl(podcastImageUrl)
            } else {
                dominantColorState.reset()
            }
        }
        content()
    }
}