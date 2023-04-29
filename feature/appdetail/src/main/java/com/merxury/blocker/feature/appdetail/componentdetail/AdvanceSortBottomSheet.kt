package com.merxury.blocker.feature.appdetail.componentdetail

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.merxury.blocker.core.designsystem.component.ItemHeader
import com.merxury.blocker.core.designsystem.segmentedbuttons.SegmentedButtons
import com.merxury.blocker.core.designsystem.theme.BlockerTheme
import com.merxury.blocker.feature.appdetail.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdvanceSortBottomSheetRoute(
    dismissHandler: () -> Unit,
    modifier: Modifier = Modifier,
    onSortModeClick: () -> Unit,
    onSortByRuleClick: () -> Unit,
) {
    val skipPartiallyExpanded by remember { mutableStateOf(false) }
    val bottomSheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = skipPartiallyExpanded,
    )
    ModalBottomSheet(
        onDismissRequest = { dismissHandler() },
        sheetState = bottomSheetState,
        containerColor = MaterialTheme.colorScheme.surfaceVariant,
        scrimColor = Color.Transparent,
    ) {
        AdvanceSortContent(
            modifier = modifier,
            onSortModeClick = onSortModeClick,
            onSortByRuleClick = onSortByRuleClick,
        )
    }
}

@Composable
fun AdvanceSortContent(
    modifier: Modifier = Modifier,
    onSortModeClick: () -> Unit = {},
    onSortByRuleClick: () -> Unit = {},
) {
    val sortModeList = listOf(
        stringResource(id = R.string.name),
        stringResource(id = R.string.updated_time),
    )
    val sortByRuleList = listOf(
        stringResource(id = R.string.enable_first),
        stringResource(id = R.string.disable_first),
    )
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = stringResource(id = R.string.advance_sort),
            style = MaterialTheme.typography.headlineLarge,
            textAlign = TextAlign.Center,
            modifier = modifier.fillMaxWidth(),
        )
        ItemHeader(title = stringResource(id = R.string.sort_mode))
        SegmentedButtons(
            items = sortModeList,
            cornerRadius = 50,
            onItemSelection = { },
        )
        Spacer(modifier = Modifier.height(16.dp))
        ItemHeader(title = stringResource(id = R.string.sort_by_rule))
        SegmentedButtons(
            items = sortByRuleList,
            cornerRadius = 50,
            onItemSelection = { },
        )
        Spacer(modifier = Modifier.height(32.dp))
    }
}

@Composable
@Preview
fun AdvanceSortBottomSheetPreview() {
    BlockerTheme {
        AdvanceSortContent()
    }
}
