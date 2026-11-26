// Copyright 2026 Blocker
// SPDX-License-Identifier: Apache-2.0

package com.merxury.blocker.feature.wake.impl

import android.annotation.SuppressLint
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import com.merxury.blocker.core.designsystem.icon.BlockerActionIcon
import com.merxury.blocker.core.designsystem.icon.BlockerIcons
import com.merxury.blocker.feature.wake.api.R.string
import com.merxury.blocker.core.designsystem.R.string as designString
import com.merxury.blocker.core.ui.R.string as coreString

@SuppressLint("DesignSystem") // The detail menu needs standard positioning and leading icons.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun WakeAppBar(busy: Boolean, onBack: () -> Unit, onComponents: () -> Unit, onRestore: () -> Unit) {
    var open by remember { mutableStateOf(false) }
    TopAppBar(
        title = {},
        navigationIcon = {
            IconButton(onClick = onBack) {
                BlockerActionIcon(BlockerIcons.Back, stringResource(designString.core_designsystem_back), tint = LocalContentColor.current)
            }
        },
        actions = {
            Box {
                IconButton(onClick = { open = true }) {
                    BlockerActionIcon(BlockerIcons.MoreVert, stringResource(coreString.core_ui_more_menu), tint = LocalContentColor.current)
                }
                DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(string.feature_wake_api_view_components)) },
                        onClick = {
                            open = false
                            onComponents()
                        },
                        leadingIcon = { BlockerActionIcon(BlockerIcons.Widgets, null, tint = LocalContentColor.current) },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(string.feature_wake_api_restore_all)) },
                        onClick = {
                            open = false
                            onRestore()
                        },
                        enabled = !busy,
                        leadingIcon = { BlockerActionIcon(BlockerIcons.SettingsBackupRestore, null, tint = LocalContentColor.current) },
                    )
                }
            }
        },
    )
}
