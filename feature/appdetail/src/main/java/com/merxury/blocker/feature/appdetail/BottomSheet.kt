package com.merxury.blocker.feature.appdetail

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
import com.merxury.blocker.core.ui.AppDetailTabs

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BottomSheetRoute(
    dismissHandler: () -> Unit,
    modifier: Modifier = Modifier,
    navigateToComponentDetail: (String) -> Unit,
    navigateToAppDetail: (String, AppDetailTabs, List<String>) -> Unit = { _, _, _ -> },
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
    ) {
        AppDetailRoute(
            modifier = modifier,
            onBackClick = { dismissHandler() },
            navigateToComponentDetail = navigateToComponentDetail,
            snackbarHostState = snackbarHostState,
            isFullScreen = bottomSheetState.currentValue == Expanded,
        )
    }
    if (bottomSheetState.currentValue == Expanded) {
        dismissHandler()
//        TODO()
//        navigateToAppDetail()
    }
}
