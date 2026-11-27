// Copyright 2026 Blocker
// SPDX-License-Identifier: Apache-2.0

package com.merxury.blocker.feature.history.impl

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.merxury.blocker.core.designsystem.icon.BlockerIcons
import com.merxury.blocker.core.model.history.OperationSource
import com.merxury.blocker.core.ui.applist.AppIcon
import com.merxury.blocker.core.ui.data.UiMessage

internal val HistoryMargin = 16.dp
internal val HistorySpacing = 8.dp

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun historySegmentedColors() = ListItemDefaults.segmentedColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun OperationIcon(row: HistoryOperation, large: Boolean = false) {
    val appIcon = row.appIcon
    if (appIcon != null) {
        val size = if (large) IconButtonDefaults.mediumContainerSize() else IconButtonDefaults.smallContainerSize()
        AppIcon(appIcon, Modifier.size(size.height))
    } else {
        HistorySourceIcon(row.operation.source.icon(), large)
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun HistorySourceIcon(icon: ImageVector, large: Boolean = false) {
    val size = if (large) IconButtonDefaults.mediumContainerSize() else IconButtonDefaults.smallContainerSize()
    val iconSize = if (large) IconButtonDefaults.mediumIconSize else IconButtonDefaults.smallIconSize
    Surface(
        modifier = Modifier.size(size),
        shape = if (large) MaterialTheme.shapes.large else MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.secondaryContainer,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(iconSize))
        }
    }
}

private fun OperationSource.icon(): ImageVector = when (this) {
    OperationSource.APP_DETAIL, OperationSource.DEBLOATER -> BlockerIcons.Apps
    OperationSource.SEARCH -> BlockerIcons.Search
    OperationSource.GENERAL_RULE, OperationSource.RULE_DETAIL -> BlockerIcons.Rule
    OperationSource.EXTERNAL_PROVIDER -> BlockerIcons.OpenInNew
    OperationSource.IMPORT_BLOCKER_RULE, OperationSource.IMPORT_MAT_RULE -> BlockerIcons.Inbox
    OperationSource.RESET_PM -> BlockerIcons.RestartAlt
    OperationSource.UNDO -> BlockerIcons.SettingsBackupRestore
}

@Composable
internal fun OperationHeadline.text(): String {
    val label = when (source) {
        OperationSource.IMPORT_BLOCKER_RULE -> R.string.feature_history_impl_source_import_blocker
        OperationSource.IMPORT_MAT_RULE -> R.string.feature_history_impl_source_import_mat
        else -> source.label()
    }
    return when {
        reference == null -> stringResource(label)
        source == OperationSource.EXTERNAL_PROVIDER -> stringResource(R.string.feature_history_impl_provider_headline, reference)
        else -> reference
    }
}

internal fun OperationSource.label(): Int = when (this) {
    OperationSource.APP_DETAIL -> R.string.feature_history_impl_source_app_detail
    OperationSource.SEARCH -> R.string.feature_history_impl_source_search
    OperationSource.GENERAL_RULE -> R.string.feature_history_impl_source_general_rule
    OperationSource.RULE_DETAIL -> R.string.feature_history_impl_source_rule_detail
    OperationSource.DEBLOATER -> R.string.feature_history_impl_source_debloater
    OperationSource.EXTERNAL_PROVIDER -> R.string.feature_history_impl_source_provider
    OperationSource.IMPORT_BLOCKER_RULE, OperationSource.IMPORT_MAT_RULE -> R.string.feature_history_impl_source_import
    OperationSource.RESET_PM -> R.string.feature_history_impl_source_reset_pm
    OperationSource.UNDO -> R.string.feature_history_impl_undo
}

@Composable
internal fun OperationSummary.text(): String = when (this) {
    is OperationSummary.Blocked -> pluralStringResource(R.plurals.feature_history_impl_turned_off, count, count)

    is OperationSummary.Enabled -> pluralStringResource(R.plurals.feature_history_impl_turned_on, count, count)

    is OperationSummary.Changed -> pluralStringResource(R.plurals.feature_history_impl_changed, count, count)

    is OperationSummary.Mixed -> stringResource(
        R.string.feature_history_impl_summary_joiner,
        pluralStringResource(R.plurals.feature_history_impl_off_count, blocked, blocked),
        pluralStringResource(R.plurals.feature_history_impl_on_count, enabled, enabled),
    )

    is OperationSummary.Undo -> {
        val target = when (val value = target) {
            is UndoTarget.Single -> value.headline.text()
            is UndoTarget.Several -> pluralStringResource(R.plurals.feature_history_impl_operation_count, value.count, value.count)
            null -> null
        }
        when {
            target == null -> pluralStringResource(R.plurals.feature_history_impl_restored, restored, restored)
            restored == 0 -> target
            else -> pluralStringResource(R.plurals.feature_history_impl_undo_row, restored, target, restored)
        }
    }
}

@Composable
internal fun HistoryMessage.toUiMessage(): UiMessage = UiMessage(
    when (this) {
        is HistoryMessage.UndoFailed -> stringResource(R.string.feature_history_impl_undo_failed)

        is HistoryMessage.Undone -> {
            val restored = result.restored + result.alreadyAtTarget
            val notRestored = result.skipped + result.failed
            val restoredText = pluralStringResource(R.plurals.feature_history_impl_restored, restored, restored)
            when {
                notRestored == 0 -> restoredText

                restored == 0 -> pluralStringResource(R.plurals.feature_history_impl_none_restored, notRestored, notRestored)

                else -> stringResource(
                    R.string.feature_history_impl_restoration_joiner,
                    restoredText,
                    pluralStringResource(R.plurals.feature_history_impl_not_restored, notRestored, notRestored),
                )
            }
        }
    },
)
