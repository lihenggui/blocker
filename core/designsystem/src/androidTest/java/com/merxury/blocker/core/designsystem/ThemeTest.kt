/*
 * Copyright 2022 Blocker
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

import android.os.Build.VERSION
import android.os.Build.VERSION_CODES
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import com.merxury.blocker.core.designsystem.theme.BackgroundTheme
import com.merxury.blocker.core.designsystem.theme.BlockerTheme
import com.merxury.blocker.core.designsystem.theme.DarkAndroidBackgroundTheme
import com.merxury.blocker.core.designsystem.theme.DarkAndroidColorScheme
import com.merxury.blocker.core.designsystem.theme.DarkDefaultColorScheme
import com.merxury.blocker.core.designsystem.theme.GradientColors
import com.merxury.blocker.core.designsystem.theme.LightAndroidBackgroundTheme
import com.merxury.blocker.core.designsystem.theme.LightAndroidColorScheme
import com.merxury.blocker.core.designsystem.theme.LightDefaultColorScheme
import com.merxury.blocker.core.designsystem.theme.LightDefaultGradientColors
import com.merxury.blocker.core.designsystem.theme.LocalBackgroundTheme
import com.merxury.blocker.core.designsystem.theme.LocalGradientColors
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals

/**
 * Tests [BlockerTheme] using different combinations of the theme mode parameters:
 * darkTheme, dynamicColor, and androidTheme.
 *
 * It verifies that the various composition locals — [MaterialTheme], [LocalGradientColors] and
 * [LocalBackgroundTheme] — have the expected values for a given theme mode, as specified by the
 * design system.
 */
class ThemeTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun darkThemeFalse_dynamicColorFalse_androidThemeFalse() {
        composeTestRule.setContent {
            BlockerTheme(
                darkTheme = false,
                disableDynamicTheming = true,
                androidTheme = false
            ) {
                val colorScheme = LightDefaultColorScheme
                assertColorSchemesEqual(colorScheme, MaterialTheme.colorScheme)
                val gradientColors = LightDefaultGradientColors
                assertEquals(gradientColors, LocalGradientColors.current)
                val backgroundTheme = BackgroundTheme(
                    color = colorScheme.surface,
                    tonalElevation = 2.dp
                )
                assertEquals(backgroundTheme, LocalBackgroundTheme.current)
            }
        }
    }

    @Test
    fun darkThemeTrue_dynamicColorFalse_androidThemeFalse() {
        composeTestRule.setContent {
            BlockerTheme(
                darkTheme = true,
                disableDynamicTheming = true,
                androidTheme = false
            ) {
                val colorScheme = DarkDefaultColorScheme
                assertColorSchemesEqual(colorScheme, MaterialTheme.colorScheme)
                val gradientColors = GradientColors()
                assertEquals(gradientColors, LocalGradientColors.current)
                val backgroundTheme = BackgroundTheme(
                    color = colorScheme.surface,
                    tonalElevation = 2.dp
                )
                assertEquals(backgroundTheme, LocalBackgroundTheme.current)
            }
        }
    }

    @Test
    fun darkThemeFalse_dynamicColorTrue_androidThemeFalse() {
        composeTestRule.setContent {
            BlockerTheme(
                darkTheme = false,
                androidTheme = false
            ) {
                val colorScheme = if (VERSION.SDK_INT >= VERSION_CODES.S) {
                    dynamicLightColorScheme(LocalContext.current)
                } else {
                    LightDefaultColorScheme
                }
                assertColorSchemesEqual(colorScheme, MaterialTheme.colorScheme)
                val gradientColors = if (VERSION.SDK_INT >= VERSION_CODES.S) {
                    GradientColors()
                } else {
                    LightDefaultGradientColors
                }
                assertEquals(gradientColors, LocalGradientColors.current)
                val backgroundTheme = BackgroundTheme(
                    color = colorScheme.surface,
                    tonalElevation = 2.dp
                )
                assertEquals(backgroundTheme, LocalBackgroundTheme.current)
            }
        }
    }

    @Test
    fun darkThemeTrue_dynamicColorTrue_androidThemeFalse() {
        composeTestRule.setContent {
            BlockerTheme(
                darkTheme = true,
                androidTheme = false
            ) {
                val colorScheme = if (VERSION.SDK_INT >= VERSION_CODES.S) {
                    dynamicDarkColorScheme(LocalContext.current)
                } else {
                    DarkDefaultColorScheme
                }
                assertColorSchemesEqual(colorScheme, MaterialTheme.colorScheme)
                val gradientColors = GradientColors()
                assertEquals(gradientColors, LocalGradientColors.current)
                val backgroundTheme = BackgroundTheme(
                    color = colorScheme.surface,
                    tonalElevation = 2.dp
                )
                assertEquals(backgroundTheme, LocalBackgroundTheme.current)
            }
        }
    }

    @Test
    fun darkThemeFalse_dynamicColorFalse_androidThemeTrue() {
        composeTestRule.setContent {
            BlockerTheme(
                darkTheme = false,
                disableDynamicTheming = true,
                androidTheme = true
            ) {
                val colorScheme = LightAndroidColorScheme
                assertColorSchemesEqual(colorScheme, MaterialTheme.colorScheme)
                val gradientColors = GradientColors()
                assertEquals(gradientColors, LocalGradientColors.current)
                val backgroundTheme = LightAndroidBackgroundTheme
                assertEquals(backgroundTheme, LocalBackgroundTheme.current)
            }
        }
    }

    @Test
    fun darkThemeTrue_dynamicColorFalse_androidThemeTrue() {
        composeTestRule.setContent {
            BlockerTheme(
                darkTheme = true,
                disableDynamicTheming = true,
                androidTheme = true
            ) {
                val colorScheme = DarkAndroidColorScheme
                assertColorSchemesEqual(colorScheme, MaterialTheme.colorScheme)
                val gradientColors = GradientColors()
                assertEquals(gradientColors, LocalGradientColors.current)
                val backgroundTheme = DarkAndroidBackgroundTheme
                assertEquals(backgroundTheme, LocalBackgroundTheme.current)
            }
        }
    }

    @Test
    fun darkThemeFalse_dynamicColorTrue_androidThemeTrue() {
        composeTestRule.setContent {
            BlockerTheme(
                darkTheme = false,
                androidTheme = true
            ) {
                val colorScheme = LightAndroidColorScheme
                assertColorSchemesEqual(colorScheme, MaterialTheme.colorScheme)
                val gradientColors = GradientColors()
                assertEquals(gradientColors, LocalGradientColors.current)
                val backgroundTheme = LightAndroidBackgroundTheme
                assertEquals(backgroundTheme, LocalBackgroundTheme.current)
            }
        }
    }

    @Test
    fun darkThemeTrue_dynamicColorTrue_androidThemeTrue() {
        composeTestRule.setContent {
            BlockerTheme(
                darkTheme = true,
                androidTheme = true
            ) {
                val colorScheme = DarkAndroidColorScheme
                assertColorSchemesEqual(colorScheme, MaterialTheme.colorScheme)
                val gradientColors = GradientColors()
                assertEquals(gradientColors, LocalGradientColors.current)
                val backgroundTheme = DarkAndroidBackgroundTheme
                assertEquals(backgroundTheme, LocalBackgroundTheme.current)
            }
        }
    }

    /**
     * Workaround for the fact that the Blocker design system specify all color scheme values.
     */
    private fun assertColorSchemesEqual(
        expectedColorScheme: ColorScheme,
        actualColorScheme: ColorScheme
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
            actualColorScheme.onSecondaryContainer
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
        assertEquals(expectedColorScheme.outline, actualColorScheme.outline)
    }
}
