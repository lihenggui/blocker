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

package com.merxury.blocker.core.ui

import android.content.Context
import android.os.Build
import android.view.accessibility.AccessibilityManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import com.merxury.blocker.core.ui.R.plurals as uiplurals
import com.merxury.blocker.core.ui.R.string as uistring

private const val COMPLETED_DISPLAY_MILLIS = 2000

// Long enough to read how many components could not be changed.
private const val COMPLETED_WITH_FAILURES_DISPLAY_MILLIS = 4000

/**
 * Progress of a batch operation. [current] counts processed components, including the
 * [failed] ones that could not be changed.
 *
 * @property isIndeterminate whether the batch cannot report how far it got, so no counts are shown
 */
data class ProcessingProgress(
    val current: Int,
    val total: Int,
    val isEnabling: Boolean,
    val isComplete: Boolean = current >= total,
    val failed: Int = 0,
    val isIndeterminate: Boolean = false,
)

@Composable
fun ProcessingProgressSnackbar(
    progress: ProcessingProgress?,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val currentOnDismiss by rememberUpdatedState(onDismiss)
    val completedProgress = progress?.takeIf { it.isComplete }
    var dismissed by remember(completedProgress) { mutableStateOf(false) }
    if (completedProgress != null && !dismissed) {
        LaunchedEffect(completedProgress, context) {
            val displayMillis = if (completedProgress.failed > 0) COMPLETED_WITH_FAILURES_DISPLAY_MILLIS else COMPLETED_DISPLAY_MILLIS
            val timeout = getRecommendedTimeout(context, displayMillis)
            delay(timeout)
            dismissed = true
            currentOnDismiss()
        }
    }
    val visibleProgress = progress?.takeUnless { dismissed }
    var lastProgress by remember { mutableStateOf<ProcessingProgress?>(null) }
    if (visibleProgress != null) {
        SideEffect { lastProgress = visibleProgress }
    }
    val displayProgress = visibleProgress ?: lastProgress
    AnimatedVisibility(
        visible = visibleProgress != null,
        modifier = modifier,
        enter = fadeIn() + slideInVertically { it },
        exit = fadeOut() + slideOutVertically { it },
    ) {
        if (displayProgress != null) {
            val message = if (displayProgress.isComplete && displayProgress.failed > 0) {
                pluralStringResource(
                    uiplurals.core_ui_operation_completed_with_failures,
                    displayProgress.failed,
                    displayProgress.failed,
                )
            } else if (displayProgress.isComplete) {
                stringResource(uistring.core_ui_operation_completed)
            } else if (displayProgress.total == 0) {
                stringResource(uistring.core_ui_loading)
            } else if (displayProgress.isIndeterminate) {
                stringResource(if (displayProgress.isEnabling) uistring.core_ui_enabling_components else uistring.core_ui_disabling_components)
            } else if (displayProgress.isEnabling) {
                stringResource(
                    uistring.core_ui_enabling_component_hint,
                    displayProgress.current,
                    displayProgress.total,
                )
            } else {
                stringResource(
                    uistring.core_ui_disabling_component_hint,
                    displayProgress.current,
                    displayProgress.total,
                )
            }
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.small,
                color = MaterialTheme.colorScheme.inverseSurface,
                shadowElevation = 6.dp,
            ) {
                Text(
                    text = message,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                    color = MaterialTheme.colorScheme.inverseOnSurface,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

private fun getRecommendedTimeout(context: Context, originalTimeoutMs: Int): Long {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        val a11yManager = context.getSystemService(AccessibilityManager::class.java)
        if (a11yManager != null) {
            return a11yManager.getRecommendedTimeoutMillis(
                originalTimeoutMs,
                AccessibilityManager.FLAG_CONTENT_TEXT,
            ).toLong()
        }
    }
    return originalTimeoutMs.toLong()
}
