/*
 * Copyright 2025 Blocker
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.merxury.blocker.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.navigation3.ListDetailSceneStrategy
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import com.merxury.blocker.R
import com.merxury.blocker.core.designsystem.component.BlockerTab
import com.merxury.blocker.core.designsystem.component.BlockerTabRow
import com.merxury.blocker.core.navigation.Navigator
import com.merxury.blocker.core.ui.LocalSnackbarHostState
import com.merxury.blocker.feature.generalrule.api.navigation.GeneralRuleNavKey
import com.merxury.blocker.feature.generalrule.impl.GeneralRulesScreen
import com.merxury.blocker.feature.generalrule.impl.GeneralRulesViewModel
import com.merxury.blocker.feature.globalifwrule.impl.GlobalIfwRuleRoute
import com.merxury.blocker.feature.ruledetail.api.navigation.navigateToRuleDetail

private enum class RulesTab(@StringRes val labelRes: Int) {
    TRACKERS(R.string.trackers),
    FIREWALL(R.string.firewall),
}

@OptIn(ExperimentalMaterial3AdaptiveApi::class)
fun EntryProviderScope<NavKey>.rulesEntry(navigator: Navigator) {
    entry<GeneralRuleNavKey>(
        metadata = ListDetailSceneStrategy.listPane(),
    ) { key ->
        RulesRoute(
            key = key,
            navigator = navigator,
        )
    }
}

@Composable
private fun RulesRoute(
    key: GeneralRuleNavKey,
    navigator: Navigator,
    modifier: Modifier = Modifier,
) {
    var selectedTab by rememberSaveable { mutableStateOf(RulesTab.TRACKERS) }
    val rulesTitle = stringResource(R.string.rules)
    val snackbarHostState = LocalSnackbarHostState.current
    val generalRulesViewModel = hiltViewModel<GeneralRulesViewModel, GeneralRulesViewModel.Factory>(
        key = key.initialRuleId,
    ) { factory ->
        factory.create(key.initialRuleId)
    }

    val tabRow: @Composable () -> Unit = {
        BlockerTabRow(selectedTabIndex = selectedTab.ordinal) {
            RulesTab.entries.forEachIndexed { index, tab ->
                BlockerTab(
                    selected = index == selectedTab.ordinal,
                    onClick = { selectedTab = tab },
                    text = {
                        Text(text = stringResource(tab.labelRes))
                    },
                )
            }
        }
    }

    when (selectedTab) {
        RulesTab.TRACKERS -> GeneralRulesScreen(
            navigateToRuleDetail = navigator::navigateToRuleDetail,
            snackbarHostState = snackbarHostState,
            title = rulesTitle,
            belowTopBar = tabRow,
            modifier = modifier.fillMaxSize(),
            viewModel = generalRulesViewModel,
        )

        RulesTab.FIREWALL -> GlobalIfwRuleRoute(
            listTitle = rulesTitle,
            listBelowTopBar = tabRow,
            modifier = modifier.fillMaxSize(),
        )
    }
}
