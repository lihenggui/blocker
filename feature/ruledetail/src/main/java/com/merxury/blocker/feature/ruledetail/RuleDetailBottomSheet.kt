package com.merxury.blocker.feature.ruledetail

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppDetailBottomSheetRoute(
    dismissHandler: () -> Unit,
    navigateToAppDetail: (String) -> Unit,
    navigateToAppDetailBottomSheet: (String) -> Unit,
    useBottomSheetStyleInDetail: Boolean,
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
        RuleDetailRoute(
            onBackClick = dismissHandler,
            navigateToAppDetail = navigateToAppDetail,
            navigateToAppDetailBottomSheet = navigateToAppDetailBottomSheet,
            useBottomSheetStyleInDetail = useBottomSheetStyleInDetail,
        )
    }
}
