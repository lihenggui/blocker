package com.merxury.blocker.feature.onlinerules

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.consumedWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.ExperimentalLifecycleComposeApi
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.merxury.blocker.core.database.generalrule.GeneralRuleEntity
import com.merxury.blocker.core.designsystem.component.BlockerHomeTopAppBar
import com.merxury.blocker.core.designsystem.component.BlockerLoadingWheel
import com.merxury.blocker.core.designsystem.theme.BlockerTheme
import com.merxury.blocker.core.ui.data.ErrorMessage
import com.merxury.blocker.feature.onlinerules.R.string
import com.merxury.blocker.feature.onlinerules.component.RuleCard
import com.merxury.blocker.feature.onlinerules.model.OnlineRulesUiState
import com.merxury.blocker.feature.onlinerules.model.OnlineRulesUiState.Error
import com.merxury.blocker.feature.onlinerules.model.OnlineRulesUiState.Loading
import com.merxury.blocker.feature.onlinerules.model.OnlineRulesUiState.OnlineRulesResult
import com.merxury.blocker.feature.onlinerules.model.OnlineRulesViewModel

@OptIn(ExperimentalLifecycleComposeApi::class)
@Composable
fun OnlineRulesRoute(
    viewModel: OnlineRulesViewModel = hiltViewModel()
) {
    val uiState by viewModel.onlineRulesUiState.collectAsStateWithLifecycle()
    OnlineRulesScreen(uiState = uiState)
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun OnlineRulesScreen(
    modifier: Modifier = Modifier,
    uiState: OnlineRulesUiState
) {
    Scaffold(
        topBar = {
            BlockerHomeTopAppBar(titleRes = string.online_rules, actions = {})
        }
    ) { padding ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(padding)
                .consumedWindowInsets(padding)
                .windowInsetsPadding(
                    WindowInsets.safeDrawing.only(
                        WindowInsetsSides.Horizontal
                    )
                ),
            verticalArrangement = Arrangement.Top,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            when (uiState) {
                Loading -> {
                    Column(
                        modifier = modifier
                            .fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        BlockerLoadingWheel(
                            modifier = modifier,
                            contentDesc = stringResource(id = string.loading),
                        )
                    }
                }

                is OnlineRulesResult -> {
                    OnlineRulesContent(uiState = uiState)
                }

                is Error -> {
                    ErrorScreen(message = uiState.message)
                }
            }
        }
    }
}

@Composable
fun ErrorScreen(message: ErrorMessage) {
    Text(text = message.message)
}

@Composable
fun OnlineRulesContent(
    modifier: Modifier = Modifier,
    uiState: OnlineRulesResult
) {
    val listContent = remember { uiState.rules }
    val listState = rememberLazyListState()
    LazyColumn(
        modifier = modifier,
        state = listState
    ) {
        items(listContent, key = { it.id }) {
            RuleCard(item = it)
        }
    }
}

@Composable
@Preview
fun OnlineRulesScreenPreview() {
    val uiState = OnlineRulesResult(
        listOf(
            GeneralRuleEntity(
                id = 100,
                name = "Blocker",
                iconUrl = null,
                company = "Merxury blocker",
                description = "Merxury Merxury Merxury Merxury Merxury Merxury Merxury Merxury",
                sideEffect = "unknown",
                safeToBlock = true,
                contributors = listOf("blocker")
            ),
            GeneralRuleEntity(
                id = 10,
                name = "Blocker2",
                iconUrl = null,
                company = "Blocker Merxury",
                description = "Blocker Blocker Blocker Blocker Blocker",
                sideEffect = "Blocker",
                safeToBlock = false,
                contributors = listOf("merxury")
            )
        )
    )
    BlockerTheme {
        OnlineRulesScreen(uiState = uiState)
    }
}
