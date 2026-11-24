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

package com.merxury.blocker.feature.ruledetail.impl

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.size.Scale
import coil3.toBitmap
import com.materialkolor.ktx.themeColorOrNull
import com.merxury.blocker.core.designsystem.theme.IconThemingState
import java.io.File

/**
 * Themes the screen after the rule's [icon], which is null while the user did not choose dynamic
 * colours, and returns to the app's theme when the screen leaves.
 */
@Composable
internal fun RuleSeedColor(icon: File?, updateIconThemingState: (IconThemingState) -> Unit) {
    val context = LocalContext.current
    val update by rememberUpdatedState(updateIconThemingState)
    LaunchedEffect(icon) {
        if (icon == null) {
            update(IconThemingState())
            return@LaunchedEffect
        }
        val request = ImageRequest.Builder(context)
            .data(icon)
            // Scaled to cover 128px x 128px, which is enough to pick a colour from.
            .size(128).scale(Scale.FILL)
            // Palette reads the pixels, which hardware bitmaps do not allow.
            .allowHardware(false)
            // Kept apart from the icon shown, so the shown one is not replaced in the cache.
            .memoryCacheKey("$icon.palette")
            .build()
        val result = SingletonImageLoader.get(context).execute(request) as? SuccessResult
        update(IconThemingState(seedColor = result?.image?.toBitmap()?.asImageBitmap()?.themeColorOrNull()))
    }
    DisposableEffect(Unit) {
        onDispose { update(IconThemingState()) }
    }
}
