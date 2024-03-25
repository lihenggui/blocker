/*
 * Copyright 2024 Blocker
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
import androidx.compose.ui.platform.LocalContext
import com.materialkolor.DynamicMaterialTheme

const val MIN_CONTRAST_OF_PRIMARY_VS_SURFACE = 3f

@Composable
fun BlockerDynamicTheme(
    iconThemingState: IconThemingState,
    darkTheme: Boolean = isSystemInDarkTheme(),
    useDynamicTheming: Boolean = false,
    content: @Composable () -> Unit,
) {
    val defaultColorScheme = when {
        useDynamicTheming && supportsDynamicTheming() -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        else -> if (darkTheme) DarkBlockerColorScheme else LightBlockerColorScheme
    }
    val seedColor = if (useDynamicTheming) {
        iconThemingState.seedColor ?: defaultColorScheme.primary
    } else {
        defaultColorScheme.primary
    }
    DynamicMaterialTheme(
        seedColor = seedColor,
        useDarkTheme = darkTheme,
        animate = true,
        content = content,
    )
}
