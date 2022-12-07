package com.merxury.blocker.feature.applist

import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource

@Composable
fun AppListRoute(
    navigateToAppDetail: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
}

@Composable
fun AppListScreen(
    appInfo: List<AppInfo>,
    navigateToAppDetail: (String) -> Unit,
    modifier: Modifier
) {
    val listContent = remember { appInfo }
    val listState = rememberLazyListState()
    LazyColumn(
        modifier = modifier,
        state = listState
    ) {
        items(listContent, key = { it.packageName }) {
            AppListItem(
                appIconUrl = it.appIconUrl,
                packageName = it.packageName,
                versionName = it.versionName,
                serviceStatus = stringResource(
                    id = R.string.service_status_template,
                    it.appStatus.running,
                    it.appStatus.blocked,
                    it.appStatus.total
                ),
                onClick = navigateToAppDetail
            )
        }
    }
}
