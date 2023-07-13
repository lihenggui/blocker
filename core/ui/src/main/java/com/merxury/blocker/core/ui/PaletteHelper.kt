package com.merxury.blocker.core.ui

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.palette.graphics.Palette

fun getGradientBackgroundColor(palette: Palette?): Brush {
    val startColor = palette?.dominantSwatch?.rgbAsColor() ?: Color.White
    val endColor = palette?.darkMutedSwatch?.rgbAsColor() ?: Color.White

    return Brush.linearGradient(colors = listOf(startColor, endColor))
}

fun Palette.Swatch.rgbAsColor(): Color = Color(rgb)
fun Palette.Swatch.titleRGBAsColor(): Color = Color(titleTextColor)
fun Palette.Swatch.bodyRGBAsColor(): Color = Color(bodyTextColor)