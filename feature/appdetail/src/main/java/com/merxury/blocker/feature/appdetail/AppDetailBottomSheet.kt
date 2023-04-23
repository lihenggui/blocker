package com.merxury.blocker.feature.appdetail

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppDetailBottomSheetRoute(
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
    ) {
        AppDetailRoute(
            modifier = modifier,
            onBackClick = { dismissHandler() },
            navigateToComponentDetail = navigateToComponentDetail,
            snackbarHostState = snackbarHostState,
        )
    }
}
