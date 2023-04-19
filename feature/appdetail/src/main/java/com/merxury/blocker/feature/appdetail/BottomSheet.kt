package com.merxury.blocker.feature.appdetail

import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetValue.Expanded
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BottomSheetRoute(
    dismissHandler: () -> Unit,
    modifier: Modifier = Modifier,
    navigateToComponentDetail: (String) -> Unit,
    snackbarHostState: SnackbarHostState,
) {
    val skipPartiallyExpanded by remember { mutableStateOf(false) }
    val bottomSheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = skipPartiallyExpanded,
    )
    ModalBottomSheet(
        onDismissRequest = { dismissHandler() },
        sheetState = bottomSheetState,
        containerColor = MaterialTheme.colorScheme.surfaceVariant,
        dragHandle = {
            if (bottomSheetState.currentValue == Expanded) {
                null
            } else {
                BottomSheetDefaults.DragHandle()
            }
        },
        shape = if (bottomSheetState.currentValue == Expanded) {
            BottomSheetDefaults.HiddenShape
        } else {
            BottomSheetDefaults.ExpandedShape
        },
    ) {
        AppDetailRoute(
            modifier = modifier,
            onBackClick = { dismissHandler() },
            navigateToComponentDetail = navigateToComponentDetail,
            snackbarHostState = snackbarHostState,
            isFullScreen = bottomSheetState.currentValue == Expanded,
        )
    }
}
