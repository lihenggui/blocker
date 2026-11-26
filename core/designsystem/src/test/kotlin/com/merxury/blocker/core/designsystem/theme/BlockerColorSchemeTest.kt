/*
 * Copyright 2026 Blocker
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

import androidx.compose.ui.graphics.Color
import org.junit.Test
import kotlin.test.assertEquals

class BlockerColorSchemeTest {
    @Test
    fun givenLightScheme_whenReadingNewSurfaceRoles_thenTheyUseBlockerColors() {
        val scheme = LightBlockerColorScheme

        assertEquals(md_theme_light_surfaceDim, scheme.surfaceDim, "surfaceDim")
        assertEquals(md_theme_light_surfaceBright, scheme.surfaceBright, "surfaceBright")
        assertEquals(md_theme_light_surfaceContainerLowest, scheme.surfaceContainerLowest, "surfaceContainerLowest")
        assertEquals(md_theme_light_surfaceContainerLow, scheme.surfaceContainerLow, "surfaceContainerLow")
        assertEquals(md_theme_light_surfaceContainer, scheme.surfaceContainer, "surfaceContainer")
        assertEquals(md_theme_light_surfaceContainerHigh, scheme.surfaceContainerHigh, "surfaceContainerHigh")
        assertEquals(md_theme_light_surfaceContainerHighest, scheme.surfaceContainerHighest, "surfaceContainerHighest")
    }

    @Test
    fun givenLightScheme_whenReadingExistingRoles_thenTheirColorsAreUnchanged() {
        val scheme = LightBlockerColorScheme

        assertEquals(Color(0xFF006D3B), scheme.primary, "primary")
        assertEquals(Color(0xFFFFFFFF), scheme.onPrimary, "onPrimary")
        assertEquals(Color(0xFF66FEA2), scheme.primaryContainer, "primaryContainer")
        assertEquals(Color(0xFF00210E), scheme.onPrimaryContainer, "onPrimaryContainer")
        assertEquals(Color(0xFF4F6353), scheme.secondary, "secondary")
        assertEquals(Color(0xFFFFFFFF), scheme.onSecondary, "onSecondary")
        assertEquals(Color(0xFFD2E8D4), scheme.secondaryContainer, "secondaryContainer")
        assertEquals(Color(0xFF0D1F13), scheme.onSecondaryContainer, "onSecondaryContainer")
        assertEquals(Color(0xFF3A646F), scheme.tertiary, "tertiary")
        assertEquals(Color(0xFFFFFFFF), scheme.onTertiary, "onTertiary")
        assertEquals(Color(0xFFBEEAF6), scheme.tertiaryContainer, "tertiaryContainer")
        assertEquals(Color(0xFF001F26), scheme.onTertiaryContainer, "onTertiaryContainer")
        assertEquals(Color(0xFFBA1A1A), scheme.error, "error")
        assertEquals(Color(0xFFFFDAD6), scheme.errorContainer, "errorContainer")
        assertEquals(Color(0xFFFFFFFF), scheme.onError, "onError")
        assertEquals(Color(0xFF410002), scheme.onErrorContainer, "onErrorContainer")
        assertEquals(Color(0xFFFBFDF8), scheme.background, "background")
        assertEquals(Color(0xFF191C19), scheme.onBackground, "onBackground")
        assertEquals(Color(0xFFFBFDF8), scheme.surface, "surface")
        assertEquals(Color(0xFF191C19), scheme.onSurface, "onSurface")
        assertEquals(Color(0xFFDDE5DB), scheme.surfaceVariant, "surfaceVariant")
        assertEquals(Color(0xFF414942), scheme.onSurfaceVariant, "onSurfaceVariant")
        assertEquals(Color(0xFF717971), scheme.outline, "outline")
        assertEquals(Color(0xFFF0F1EC), scheme.inverseOnSurface, "inverseOnSurface")
        assertEquals(Color(0xFF2E312E), scheme.inverseSurface, "inverseSurface")
        assertEquals(Color(0xFF43E188), scheme.inversePrimary, "inversePrimary")
        assertEquals(Color(0xFF006D3B), scheme.surfaceTint, "surfaceTint")
        assertEquals(Color(0xFFC1C9BF), scheme.outlineVariant, "outlineVariant")
        assertEquals(Color(0xFF000000), scheme.scrim, "scrim")
    }

    @Test
    fun givenDarkScheme_whenReadingNewSurfaceRoles_thenTheyUseBlockerColors() {
        val scheme = DarkBlockerColorScheme

        assertEquals(md_theme_dark_surfaceDim, scheme.surfaceDim, "surfaceDim")
        assertEquals(md_theme_dark_surfaceBright, scheme.surfaceBright, "surfaceBright")
        assertEquals(md_theme_dark_surfaceContainerLowest, scheme.surfaceContainerLowest, "surfaceContainerLowest")
        assertEquals(md_theme_dark_surfaceContainerLow, scheme.surfaceContainerLow, "surfaceContainerLow")
        assertEquals(md_theme_dark_surfaceContainer, scheme.surfaceContainer, "surfaceContainer")
        assertEquals(md_theme_dark_surfaceContainerHigh, scheme.surfaceContainerHigh, "surfaceContainerHigh")
        assertEquals(md_theme_dark_surfaceContainerHighest, scheme.surfaceContainerHighest, "surfaceContainerHighest")
    }

    @Test
    fun givenDarkScheme_whenReadingExistingRoles_thenTheirColorsAreUnchanged() {
        val scheme = DarkBlockerColorScheme

        assertEquals(Color(0xFF43E188), scheme.primary, "primary")
        assertEquals(Color(0xFF00391C), scheme.onPrimary, "onPrimary")
        assertEquals(Color(0xFF00522B), scheme.primaryContainer, "primaryContainer")
        assertEquals(Color(0xFF66FEA2), scheme.onPrimaryContainer, "onPrimaryContainer")
        assertEquals(Color(0xFFB6CCB8), scheme.secondary, "secondary")
        assertEquals(Color(0xFF223527), scheme.onSecondary, "onSecondary")
        assertEquals(Color(0xFF384B3C), scheme.secondaryContainer, "secondaryContainer")
        assertEquals(Color(0xFFD2E8D4), scheme.onSecondaryContainer, "onSecondaryContainer")
        assertEquals(Color(0xFFA2CEDA), scheme.tertiary, "tertiary")
        assertEquals(Color(0xFF023640), scheme.onTertiary, "onTertiary")
        assertEquals(Color(0xFF214C57), scheme.tertiaryContainer, "tertiaryContainer")
        assertEquals(Color(0xFFBEEAF6), scheme.onTertiaryContainer, "onTertiaryContainer")
        assertEquals(Color(0xFFFFB4AB), scheme.error, "error")
        assertEquals(Color(0xFF93000A), scheme.errorContainer, "errorContainer")
        assertEquals(Color(0xFF690005), scheme.onError, "onError")
        assertEquals(Color(0xFFFFDAD6), scheme.onErrorContainer, "onErrorContainer")
        assertEquals(Color(0xFF191C19), scheme.background, "background")
        assertEquals(Color(0xFFE1E3DE), scheme.onBackground, "onBackground")
        assertEquals(Color(0xFF191C19), scheme.surface, "surface")
        assertEquals(Color(0xFFE1E3DE), scheme.onSurface, "onSurface")
        assertEquals(Color(0xFF414942), scheme.surfaceVariant, "surfaceVariant")
        assertEquals(Color(0xFFC1C9BF), scheme.onSurfaceVariant, "onSurfaceVariant")
        assertEquals(Color(0xFF8B938A), scheme.outline, "outline")
        assertEquals(Color(0xFF191C19), scheme.inverseOnSurface, "inverseOnSurface")
        assertEquals(Color(0xFFE1E3DE), scheme.inverseSurface, "inverseSurface")
        assertEquals(Color(0xFF006D3B), scheme.inversePrimary, "inversePrimary")
        assertEquals(Color(0xFF43E188), scheme.surfaceTint, "surfaceTint")
        assertEquals(Color(0xFF414942), scheme.outlineVariant, "outlineVariant")
        assertEquals(Color(0xFF000000), scheme.scrim, "scrim")
    }
}
