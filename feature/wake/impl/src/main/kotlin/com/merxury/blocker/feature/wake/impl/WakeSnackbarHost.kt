// Copyright 2026 Blocker
// SPDX-License-Identifier: Apache-2.0

package com.merxury.blocker.feature.wake.impl

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.animateFloat
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalAccessibilityManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.merxury.blocker.feature.wake.api.R.string
import kotlinx.coroutines.delay

// The stock host adds a scale transform. Keep the reveal and its timeout local to this screen.
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@android.annotation.SuppressLint("DesignSystem")
@Composable
internal fun WakeSnackbarHost(
    notice: WakeNotice?,
    onUndo: (Long) -> Unit,
    onDismiss: (Long) -> Unit,
    undoEnabled: Boolean,
    onHeight: (Dp) -> Unit,
    modifier: Modifier = Modifier,
) {
    val accessibility = LocalAccessibilityManager.current
    val density = LocalDensity.current
    val scheme = MaterialTheme.colorScheme
    val motion = MaterialTheme.motionScheme
    var previous by remember { mutableStateOf<WakeNotice?>(null) }
    if (notice != null) SideEffect { previous = notice }
    LaunchedEffect(notice?.id) {
        if (notice == null) return@LaunchedEffect
        delay(accessibility?.calculateRecommendedTimeoutMillis(4400, containsText = true, containsControls = notice.undo != null) ?: 4400)
        onDismiss(notice.id)
    }
    AnimatedVisibility(visible = notice != null, modifier = modifier, enter = EnterTransition.None, exit = ExitTransition.None) {
        val message = notice ?: previous ?: return@AnimatedVisibility
        DisposableEffect(Unit) { onDispose { onHeight(0.dp) } }
        val reveal by transition.animateFloat(transitionSpec = { motion.defaultSpatialSpec() }, label = "Snackbar reveal") {
            if (it == EnterExitState.Visible) 1f else 0f
        }
        val alpha by transition.animateFloat(transitionSpec = { motion.fastEffectsSpec() }, label = "Snackbar opacity") {
            if (it == EnterExitState.Visible) 1f else 0f
        }
        Snackbar(
            modifier = Modifier.fillMaxWidth()
                .onSizeChanged { onHeight(with(density) { it.height.toDp() }) }
                .graphicsLayer { this.alpha = alpha.coerceIn(0f, 1f) }
                .drawWithContent { clipRect(top = size.height * (1f - reveal.coerceIn(0f, 1f))) { this@drawWithContent.drawContent() } }
                .semantics { liveRegion = LiveRegionMode.Polite },
            shape = RoundedCornerShape(4.dp),
            containerColor = scheme.inverseSurface,
            contentColor = scheme.inverseOnSurface,
            action = if (message.undo != null) {
                {
                    TextButton(
                        onClick = { onUndo(message.id) },
                        enabled = notice != null && undoEnabled,
                        colors = ButtonDefaults.textButtonColors(contentColor = scheme.inversePrimary),
                    ) { Text(stringResource(string.feature_wake_api_undo)) }
                }
            } else {
                null
            },
        ) { Text(message.text.resolve()) }
    }
}
