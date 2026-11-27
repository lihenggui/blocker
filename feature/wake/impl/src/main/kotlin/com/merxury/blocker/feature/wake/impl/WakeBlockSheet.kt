// Copyright 2026 Blocker
// SPDX-License-Identifier: Apache-2.0

package com.merxury.blocker.feature.wake.impl

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
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
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.merxury.blocker.core.designsystem.component.BlockerButton
import com.merxury.blocker.core.designsystem.component.BlockerTextButton
import com.merxury.blocker.feature.wake.api.R.string
import kotlinx.coroutines.launch

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
        selectedKeys = choices.filter { it.checkedByDefault }.map { it.key }
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
            val groups = choices.groupBy { it.group }
            for (group in WakeChoiceGroup.entries) {
                val section = groups[group] ?: continue
                WakeChoiceHeader(group)
                section.forEach { choice ->
                    WakeChoiceItem(choice, choice.key in selected, enabled = !closing) { checked ->
                        selectedKeys = if (checked) selectedKeys + choice.key else selectedKeys - choice.key
                    }
                }
            }
            Row(
                Modifier.fillMaxWidth().padding(horizontal = WakeContentSpacing),
                horizontalArrangement = Arrangement.spacedBy(WakeGridUnit, Alignment.End),
            ) {
                BlockerTextButton(onClick = { close(false) }, enabled = !closing) { Text(stringResource(string.feature_wake_api_cancel)) }
                BlockerButton(onClick = { close(true) }, enabled = !closing && selected.any { it != FORCE_STOP_CHOICE }) {
                    Text(stringResource(string.feature_wake_api_block))
                }
            }
        }
    }
}

@Composable
private fun WakeChoiceHeader(group: WakeChoiceGroup) {
    val title = when (group) {
        WakeChoiceGroup.OBSERVED -> string.feature_wake_api_sheet_observed
        WakeChoiceGroup.DORMANT -> string.feature_wake_api_sheet_dormant
        WakeChoiceGroup.SYSTEM -> string.feature_wake_api_sheet_system
    }
    Text(
        text = stringResource(title),
        modifier = Modifier.padding(start = WakeContentSpacing, end = WakeContentSpacing, top = 12.dp, bottom = 4.dp)
            .semantics { heading() },
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun WakeChoiceItem(choice: WakeSheetChoice, checked: Boolean, enabled: Boolean, onCheckedChange: (Boolean) -> Unit) {
    val colors = MaterialTheme.colorScheme
    // The whole row toggles, and the row keeps the sheet's color.
    ListItem(
        modifier = Modifier.toggleable(checked, enabled = enabled, role = Role.Checkbox, onValueChange = onCheckedChange),
        leadingContent = { Checkbox(checked, onCheckedChange = null, enabled = enabled) },
        supportingContent = choice.supporting?.let { { Text(it.resolve(), color = if (choice.warning) colors.error else colors.onSurfaceVariant) } },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
    ) { Text(choice.title.resolve()) }
}
