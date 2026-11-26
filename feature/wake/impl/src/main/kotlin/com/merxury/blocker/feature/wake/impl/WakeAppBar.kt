// Copyright 2026 Blocker
// SPDX-License-Identifier: Apache-2.0

package com.merxury.blocker.feature.wake.impl

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import com.merxury.blocker.core.designsystem.icon.BlockerActionIcon
import com.merxury.blocker.core.designsystem.icon.BlockerIcons
import com.merxury.blocker.feature.wake.api.R.string
import com.merxury.blocker.core.designsystem.R.string as designString
import com.merxury.blocker.core.ui.R.string as coreString

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun WakeAppBar(busy: Boolean, onBack: () -> Unit, onComponents: () -> Unit, onRestore: () -> Unit) {
    var open by remember { mutableStateOf(false) }
    val colors = MaterialTheme.colorScheme
    val menuButton by animateColorAsState(
        if (open) colors.surfaceContainerHighest else Color.Transparent,
        MaterialTheme.motionScheme.defaultEffectsSpec(),
        label = "Overflow button",
    )
    Row(
        Modifier.fillMaxWidth().height(64.dp).padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) {
            BlockerActionIcon(BlockerIcons.Back, stringResource(designString.core_designsystem_back))
        }
        Box(Modifier.weight(1f))
        Box {
            IconButton(onClick = { open = !open }, modifier = Modifier.background(menuButton, CircleShape)) {
                BlockerActionIcon(BlockerIcons.MoreVert, stringResource(coreString.core_ui_more_menu), tint = colors.onSurfaceVariant)
            }
            WakeAppMenu(
                open,
                onDismiss = { open = false },
                onComponents = {
                    open = false
                    onComponents()
                },
                onRestore = {
                    open = false
                    onRestore()
                },
                restoreEnabled = !busy,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun WakeAppMenu(
    open: Boolean,
    onDismiss: () -> Unit,
    onComponents: () -> Unit,
    onRestore: () -> Unit,
    restoreEnabled: Boolean,
) {
    val motion = MaterialTheme.motionScheme
    val visibility = remember { androidx.compose.animation.core.MutableTransitionState(false) }
    visibility.targetState = open
    if (!visibility.currentState && visibility.isIdle && !open) return
    val extraEndInset = with(LocalDensity.current) { 4.dp.roundToPx() }
    Popup(
        popupPositionProvider = remember(extraEndInset) { WakeMenuPosition(extraEndInset) },
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = open),
    ) {
        AnimatedVisibility(
            visibleState = visibility,
            enter = fadeIn(motion.fastEffectsSpec()) + expandVertically(motion.defaultSpatialSpec(), expandFrom = Alignment.Top),
            exit = fadeOut(motion.fastEffectsSpec()) + shrinkVertically(motion.fastSpatialSpec(), shrinkTowards = Alignment.Top),
        ) {
            Surface(
                modifier = Modifier.width(232.dp),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceContainer,
                shadowElevation = 3.dp,
            ) {
                Column(Modifier.padding(vertical = 4.dp)) {
                    WakeMenuItem(string.feature_wake_api_view_components, BlockerIcons.Widgets, onComponents, open)
                    WakeMenuItem(string.feature_wake_api_restore_all, BlockerIcons.SettingsBackupRestore, onRestore, open && restoreEnabled)
                }
            }
        }
    }
}

@Composable
private fun WakeMenuItem(label: Int, icon: ImageVector, onClick: () -> Unit, enabled: Boolean) {
    DropdownMenuItem(
        text = { Text(stringResource(label), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Medium) },
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.heightIn(min = 48.dp),
        leadingIcon = { BlockerActionIcon(icon, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant) },
        contentPadding = PaddingValues(horizontal = 16.dp),
    )
}

private class WakeMenuPosition(private val extraEndInset: Int) : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset {
        val x = if (layoutDirection == LayoutDirection.Ltr) {
            anchorBounds.right - extraEndInset - popupContentSize.width
        } else {
            anchorBounds.left + extraEndInset
        }
        return IntOffset(x.coerceIn(0, (windowSize.width - popupContentSize.width).coerceAtLeast(0)), anchorBounds.bottom)
    }
}
