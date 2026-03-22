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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import com.merxury.blocker.core.ui.R.string as uistring

private const val COMPLETED_DISPLAY_MILLIS = 2000

data class ProcessingProgress(
    val current: Int,
    val total: Int,
    val isEnabling: Boolean,
)

@Composable
fun ProcessingProgressSnackbar(
    progress: ProcessingProgress?,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isCompleted = progress != null && progress.current >= progress.total
    val context = LocalContext.current
    LaunchedEffect(isCompleted) {
        if (isCompleted) {
            val timeout = getRecommendedTimeout(context, COMPLETED_DISPLAY_MILLIS)
            delay(timeout)
            onDismiss()
        }
    }
    var lastProgress by remember { mutableStateOf(progress) }
    if (progress != null) {
        lastProgress = progress
    }
    AnimatedVisibility(
        visible = progress != null,
        modifier = modifier,
        enter = fadeIn() + slideInVertically { it },
        exit = fadeOut() + slideOutVertically { it },
    ) {
        lastProgress?.let { displayProgress ->
            val message = if (displayProgress.current >= displayProgress.total) {
                stringResource(uistring.core_ui_operation_completed)
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
