// Copyright 2026 Blocker
// SPDX-License-Identifier: Apache-2.0

package com.merxury.blocker.feature.wake.impl

import android.annotation.SuppressLint
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.merxury.blocker.feature.wake.api.R.string
import kotlinx.coroutines.launch

@SuppressLint("DesignSystem") // The sheet uses Material's primary button roles.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun WakeBlockSheet(
    appLabel: String,
    choices: List<WakeSheetChoice>,
    onDismiss: () -> Unit,
    onConfirm: (Set<String>) -> Unit,
) {
    val sheetState = rememberBottomSheetState(
        initialValue = SheetValue.Hidden,
        enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded),
    )
    val scope = rememberCoroutineScope()
    val initialChoices = remember { choices }
    var unchecked by remember { mutableStateOf(emptySet<String>()) }
    var closing by remember { mutableStateOf(false) }
    val selected = initialChoices.mapTo(mutableSetOf()) { it.key } - unchecked
    val close: (Boolean) -> Unit = { confirm ->
        if (!closing) {
            closing = true
            if (confirm) onConfirm(selected)
            scope.launch {
                sheetState.hide()
                onDismiss()
            }
        }
    }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        scrimColor = MaterialTheme.colorScheme.scrim.copy(alpha = 0.32f),
        dragHandle = {
            Box(Modifier.height(36.dp), contentAlignment = Alignment.Center) {
                Box(
                    Modifier.size(32.dp, 4.dp)
                        .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f), CircleShape),
                )
            }
        },
    ) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(start = 24.dp, end = 24.dp, bottom = 24.dp)) {
            Text(
                stringResource(string.feature_wake_api_sheet_title, appLabel),
                Modifier.padding(bottom = 12.dp),
                style = MaterialTheme.typography.headlineSmall,
            )
            initialChoices.forEach { choice ->
                val checked = choice.key !in unchecked
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 52.dp)
                        .toggleable(checked, enabled = !closing, role = Role.Checkbox) {
                            unchecked = if (it) unchecked - choice.key else unchecked + choice.key
                        },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Box(Modifier.width(29.dp), contentAlignment = Alignment.CenterStart) {
                        Checkbox(checked, onCheckedChange = null)
                    }
                    Text(choice.title.resolve(), style = MaterialTheme.typography.bodyLarge)
                }
            }
            Row(Modifier.fillMaxWidth().padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
                TextButton(onClick = { close(false) }, enabled = !closing) { Text(stringResource(string.feature_wake_api_cancel)) }
                Button(onClick = { close(true) }, enabled = !closing && selected.isNotEmpty()) {
                    Text(stringResource(string.feature_wake_api_block))
                }
            }
        }
    }
}
