package com.merxury.blocker.feature.appdetail

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.ExperimentalLifecycleComposeApi
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.merxury.blocker.core.designsystem.component.BlockerTab
import com.merxury.blocker.core.designsystem.component.BlockerTabRow

@OptIn(ExperimentalLifecycleComposeApi::class)
@Composable
fun AppDetailRoute(
    viewModel: AppDetailViewModel = hiltViewModel(),
    onBackClick: () -> Unit,
) {
    val tabState by viewModel.tabState.collectAsStateWithLifecycle()

    AppDetailScreen(
        tabState = tabState,
        switchTab = viewModel::switchTab
    )
}

@Composable
fun AppDetailScreen(
    tabState: TabState,
    switchTab: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier) {
        BlockerTabRow(selectedTabIndex = tabState.currentIndex) {
            tabState.titles.forEachIndexed { index, title ->
                BlockerTab(
                    selected = index == tabState.currentIndex,
                    onClick = { switchTab(index) },
                    text = { Text(text = title) }
                )
            }
        }
        when (tabState.currentIndex) {
            0 -> {
//                AppInfoTabContent()
            }

            1 -> {
//                ServiceTabContent()
            }

            2 -> {
//                ReceiverTabContent()
            }

            3 -> {
//                ActivityTabContent()
            }

            4 -> {
//                ContentProviderTabContent()
            }
        }
    }
}
