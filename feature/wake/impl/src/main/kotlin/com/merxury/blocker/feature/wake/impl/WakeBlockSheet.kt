// Copyright 2026 Blocker
// SPDX-License-Identifier: Apache-2.0

package com.merxury.blocker.feature.wake.impl

import android.annotation.SuppressLint
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import com.merxury.blocker.feature.wake.api.R.string
import kotlinx.coroutines.launch

@SuppressLint("DesignSystem") // The sheet uses Material's primary button roles.
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
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
    var initialized by rememberSaveable { mutableStateOf(false) }
    var selectedKeys by rememberSaveable { mutableStateOf(emptyList<String>()) }
    var closing by remember { mutableStateOf(false) }
    if (!initialized && choices.isNotEmpty()) {
        selectedKeys = choices.map { it.key }.filterNot { it == FORCE_STOP_CHOICE }
        initialized = true
    }
    val liveKeys = choices.mapTo(mutableSetOf()) { it.key }
    val selected = selectedKeys.filterTo(mutableSetOf()) { it in liveKeys }
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
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(bottom = WakeContentSpacing)) {
            Text(
                stringResource(string.feature_wake_api_sheet_title, appLabel),
                Modifier.padding(horizontal = WakeContentSpacing),
                style = MaterialTheme.typography.headlineSmall,
            )
            choices.forEach { choice ->
                // The checkbox list pattern: the whole row toggles, and the row keeps the sheet's color.
                ListItem(
                    headlineContent = { Text(choice.title.resolve()) },
                    modifier = Modifier.toggleable(
                        value = choice.key in selected,
                        enabled = !closing,
                        role = Role.Checkbox,
                        onValueChange = { selectedKeys = if (it) selectedKeys + choice.key else selectedKeys - choice.key },
                    ),
                    leadingContent = { Checkbox(choice.key in selected, onCheckedChange = null, enabled = !closing) },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                )
            }
            Row(
                Modifier.fillMaxWidth().padding(horizontal = WakeContentSpacing),
                horizontalArrangement = Arrangement.spacedBy(WakeGridUnit, Alignment.End),
            ) {
                TextButton(onClick = { close(false) }, enabled = !closing) { Text(stringResource(string.feature_wake_api_cancel)) }
                Button(onClick = { close(true) }, enabled = !closing && selected.any { it != FORCE_STOP_CHOICE }) {
                    Text(stringResource(string.feature_wake_api_block))
                }
            }
        }
    }
}
