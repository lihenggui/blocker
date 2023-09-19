/*
 * Copyright 2023 Blocker
 * Copyright 2022 The Android Open Source Project
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

package com.merxury.blocker.core.designsystem

import android.os.Build
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import com.merxury.blocker.core.designsystem.theme.BackgroundTheme
import com.merxury.blocker.core.designsystem.theme.BlockerTheme
import com.merxury.blocker.core.designsystem.theme.DarkBlockerBackgroundTheme
import com.merxury.blocker.core.designsystem.theme.DarkBlockerColorScheme
import com.merxury.blocker.core.designsystem.theme.DarkBlockerGradientColors
import com.merxury.blocker.core.designsystem.theme.GradientColors
import com.merxury.blocker.core.designsystem.theme.LightBlockerBackgroundTheme
import com.merxury.blocker.core.designsystem.theme.LightBlockerColorScheme
import com.merxury.blocker.core.designsystem.theme.LightBlockerGradientColors
import com.merxury.blocker.core.designsystem.theme.LocalBackgroundTheme
import com.merxury.blocker.core.designsystem.theme.LocalGradientColors
import com.merxury.blocker.core.designsystem.theme.LocalTintTheme
import com.merxury.blocker.core.designsystem.theme.TintTheme
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals

/**
 * Tests [BlockerTheme] using different combinations of the theme mode parameters:
 * darkTheme, disableDynamicTheming, and blockerTheme.
 *
 * It verifies that the various composition locals — [MaterialTheme], [LocalGradientColors] and
 * [LocalBackgroundTheme] — have the expected values for a given theme mode, as specified by the
 * design system.
 */
class ThemeTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun darkThemeFalse_dynamicColorTrue_blockerThemeFalse() {
        composeTestRule.setContent {
            BlockerTheme(
                darkTheme = false,
                disableDynamicTheming = false,
                blockerTheme = false,
            ) {
                val colorScheme = dynamicLightColorSchemeWithFallback()
                assertColorSchemesEqual(colorScheme, MaterialTheme.colorScheme)
                val gradientColors = dynamicGradientColorsWithFallback(colorScheme)
                assertEquals(gradientColors, LocalGradientColors.current)
                val backgroundTheme = defaultBackgroundTheme(colorScheme)
                assertEquals(backgroundTheme, LocalBackgroundTheme.current)
                val tintTheme = dynamicTintThemeWithFallback(colorScheme)
                assertEquals(tintTheme, LocalTintTheme.current)
            }
        }
    }

    @Test
    fun darkThemeTrue_dynamicColorTrue_blockerThemeFalse() {
        composeTestRule.setContent {
            BlockerTheme(
                darkTheme = true,
                disableDynamicTheming = false,
                blockerTheme = false,
            ) {
                val colorScheme = dynamicDarkColorSchemeWithFallback()
                assertColorSchemesEqual(colorScheme, MaterialTheme.colorScheme)
                val gradientColors = dynamicGradientColorsWithFallback(colorScheme)
                assertEquals(gradientColors, LocalGradientColors.current)
                val backgroundTheme = defaultBackgroundTheme(colorScheme)
                assertEquals(backgroundTheme, LocalBackgroundTheme.current)
                val tintTheme = dynamicTintThemeWithFallback(colorScheme)
                assertEquals(tintTheme, LocalTintTheme.current)
            }
        }
    }

    @Test
    fun darkThemeFalse_dynamicColorFalse_blockerThemeTrue() {
        composeTestRule.setContent {
            BlockerTheme(
                darkTheme = false,
                disableDynamicTheming = true,
                blockerTheme = true,
            ) {
                val colorScheme = LightBlockerColorScheme
                assertColorSchemesEqual(colorScheme, MaterialTheme.colorScheme)
                val gradientColors = LightBlockerGradientColors
                assertEquals(gradientColors, LocalGradientColors.current)
                val backgroundTheme = LightBlockerBackgroundTheme
                assertEquals(backgroundTheme, LocalBackgroundTheme.current)
                val tintTheme = defaultTintTheme()
                assertEquals(tintTheme, LocalTintTheme.current)
            }
        }
    }

    @Test
    fun darkThemeTrue_dynamicColorFalse_blockerThemeTrue() {
        composeTestRule.setContent {
            BlockerTheme(
                darkTheme = true,
                disableDynamicTheming = true,
                blockerTheme = true,
            ) {
                val colorScheme = DarkBlockerColorScheme
                assertColorSchemesEqual(colorScheme, MaterialTheme.colorScheme)
                val gradientColors = DarkBlockerGradientColors
                assertEquals(gradientColors, LocalGradientColors.current)
                val backgroundTheme = DarkBlockerBackgroundTheme
                assertEquals(backgroundTheme, LocalBackgroundTheme.current)
                val tintTheme = defaultTintTheme()
                assertEquals(tintTheme, LocalTintTheme.current)
            }
        }
    }

    @Composable
    private fun dynamicLightColorSchemeWithFallback(): ColorScheme {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            dynamicLightColorScheme(LocalContext.current)
        } else {
            LightBlockerColorScheme
        }
    }

    @Composable
    private fun dynamicDarkColorSchemeWithFallback(): ColorScheme {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            dynamicDarkColorScheme(LocalContext.current)
        } else {
            DarkBlockerColorScheme
        }
    }

    private fun emptyGradientColors(colorScheme: ColorScheme): GradientColors {
        return GradientColors(container = colorScheme.surfaceColorAtElevation(2.dp))
    }

    private fun defaultGradientColors(colorScheme: ColorScheme): GradientColors {
        return GradientColors(
            top = colorScheme.inverseOnSurface,
            bottom = colorScheme.primaryContainer,
            container = colorScheme.surface,
        )
    }

    private fun dynamicGradientColorsWithFallback(colorScheme: ColorScheme): GradientColors {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            emptyGradientColors(colorScheme)
        } else {
            defaultGradientColors(colorScheme)
        }
    }

    private fun defaultBackgroundTheme(colorScheme: ColorScheme): BackgroundTheme {
        return BackgroundTheme(
            color = colorScheme.surface,
            tonalElevation = 2.dp,
        )
    }

    private fun defaultTintTheme(): TintTheme {
        return TintTheme()
    }

    private fun dynamicTintThemeWithFallback(colorScheme: ColorScheme): TintTheme {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            TintTheme(colorScheme.primary)
        } else {
            TintTheme()
        }
    }

    /**
     * Workaround for the fact that the Blocker design system specify all color scheme values.
     */
    private fun assertColorSchemesEqual(
        expectedColorScheme: ColorScheme,
        actualColorScheme: ColorScheme,
    ) {
        assertEquals(expectedColorScheme.primary, actualColorScheme.primary)
        assertEquals(expectedColorScheme.onPrimary, actualColorScheme.onPrimary)
        assertEquals(expectedColorScheme.primaryContainer, actualColorScheme.primaryContainer)
        assertEquals(expectedColorScheme.onPrimaryContainer, actualColorScheme.onPrimaryContainer)
        assertEquals(expectedColorScheme.secondary, actualColorScheme.secondary)
        assertEquals(expectedColorScheme.onSecondary, actualColorScheme.onSecondary)
        assertEquals(expectedColorScheme.secondaryContainer, actualColorScheme.secondaryContainer)
        assertEquals(
            expectedColorScheme.onSecondaryContainer,
            actualColorScheme.onSecondaryContainer,
        )
        assertEquals(expectedColorScheme.tertiary, actualColorScheme.tertiary)
        assertEquals(expectedColorScheme.onTertiary, actualColorScheme.onTertiary)
        assertEquals(expectedColorScheme.tertiaryContainer, actualColorScheme.tertiaryContainer)
        assertEquals(expectedColorScheme.onTertiaryContainer, actualColorScheme.onTertiaryContainer)
        assertEquals(expectedColorScheme.error, actualColorScheme.error)
        assertEquals(expectedColorScheme.onError, actualColorScheme.onError)
        assertEquals(expectedColorScheme.errorContainer, actualColorScheme.errorContainer)
        assertEquals(expectedColorScheme.onErrorContainer, actualColorScheme.onErrorContainer)
        assertEquals(expectedColorScheme.background, actualColorScheme.background)
        assertEquals(expectedColorScheme.onBackground, actualColorScheme.onBackground)
        assertEquals(expectedColorScheme.surface, actualColorScheme.surface)
        assertEquals(expectedColorScheme.onSurface, actualColorScheme.onSurface)
        assertEquals(expectedColorScheme.surfaceVariant, actualColorScheme.surfaceVariant)
        assertEquals(expectedColorScheme.onSurfaceVariant, actualColorScheme.onSurfaceVariant)
        assertEquals(expectedColorScheme.inverseSurface, actualColorScheme.inverseSurface)
        assertEquals(expectedColorScheme.inverseOnSurface, actualColorScheme.inverseOnSurface)
        assertEquals(expectedColorScheme.outline, actualColorScheme.outline)
    }
}
