// Copyright 2026 Blocker
// SPDX-License-Identifier: Apache-2.0

package com.merxury.blocker.feature.history.impl

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.merxury.blocker.core.designsystem.component.BlockerTopAppBar
import com.merxury.blocker.core.designsystem.icon.BlockerIcons
import com.merxury.blocker.core.ui.data.UiMessage
import com.merxury.blocker.core.ui.screen.ErrorScreen
import com.merxury.blocker.core.ui.screen.LoadingScreen
import java.time.LocalDate

@Composable
internal fun HistoryRoute(
    selectedOperationId: Long?,
    navigateBack: () -> Unit,
    onOperationClick: (Long) -> Unit,
    viewModel: HistoryViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    HistoryScreen(state, selectedOperationId, navigateBack, onOperationClick)
}

@Composable
internal fun HistoryScreen(
    state: HistoryUiState,
    selectedOperationId: Long? = null,
    navigateBack: () -> Unit = {},
    onOperationClick: (Long) -> Unit = {},
    today: LocalDate = LocalDate.now(state.zone),
) {
    Scaffold(
        topBar = {
            BlockerTopAppBar(
                title = stringResource(R.string.feature_history_impl_title),
                hasNavigationIcon = true,
                onNavigationClick = navigateBack,
            )
        },
    ) { padding ->
        val modifier = Modifier.padding(padding).consumeWindowInsets(padding)
        val days = state.days
        when {
            state.loadFailed -> ErrorScreen(UiMessage(stringResource(R.string.feature_history_impl_load_failed)), modifier)
            days == null -> LoadingScreen(modifier)
            days.isEmpty() -> HistoryEmpty(modifier)
            else -> HistoryList(days, rememberHistoryDates(state.zone, today), selectedOperationId, onOperationClick, modifier)
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun HistoryList(
    days: List<HistoryDay>,
    dates: HistoryDates,
    selectedOperationId: Long?,
    onOperationClick: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(HistoryMargin),
        verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
    ) {
        days.forEachIndexed { dayIndex, day ->
            item(key = day.date.toEpochDay(), contentType = "day") {
                HistorySectionTitle(
                    text = dates.day(day.date),
                    modifier = Modifier.animateItem()
                        .then(if (dayIndex > 0) Modifier.padding(top = HistoryMargin) else Modifier)
                        .padding(bottom = HistorySpacing),
                )
            }
            itemsIndexed(
                items = day.operations,
                key = { _, row -> "operation:${row.operation.id}" },
                contentType = { _, _ -> "operation" },
            ) { index, row ->
                val id = row.operation.id
                val summary = row.summary.text()
                SegmentedListItem(
                    selected = id == selectedOperationId,
                    onClick = { onOperationClick(id) },
                    shapes = ListItemDefaults.segmentedShapes(index, day.operations.size),
                    modifier = Modifier.animateItem(),
                    colors = historySegmentedColors(),
                    leadingContent = { OperationIcon(row) },
                    supportingContent = {
                        Text(
                            text = if (row.isUndone) stringResource(R.string.feature_history_impl_undone_suffix, summary) else summary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                    trailingContent = { Text(dates.time(row.operation.timestamp)) },
                ) {
                    Text(row.headline.text(), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

@Composable
internal fun HistorySectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier.semantics { heading() },
    )
}

@Composable
private fun HistoryEmpty(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize().padding(HistoryMargin),
        verticalArrangement = Arrangement.spacedBy(HistorySpacing, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        HistorySourceIcon(BlockerIcons.Update, large = true)
        Text(
            text = stringResource(R.string.feature_history_impl_empty_title),
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center,
        )
        Text(
            text = stringResource(R.string.feature_history_impl_empty_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}
