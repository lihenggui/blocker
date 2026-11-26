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

package com.merxury.blocker.feature.appdetail.impl.navigation

import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.navigation3.ListDetailSceneStrategy
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import com.merxury.blocker.core.designsystem.theme.IconThemingState
import com.merxury.blocker.core.model.ComponentType
import com.merxury.blocker.core.navigation.Navigator
import com.merxury.blocker.core.ui.LocalSnackbarHostState
import com.merxury.blocker.feature.appdetail.api.navigation.AppDetailNavKey
import com.merxury.blocker.feature.appdetail.impl.AppDetailScreen
import com.merxury.blocker.feature.appdetail.impl.AppHeaderViewModel
import com.merxury.blocker.feature.appdetail.impl.ComponentControlViewModel
import com.merxury.blocker.feature.appdetail.impl.ComponentListViewModel
import com.merxury.blocker.feature.ruledetail.api.navigation.navigateToRuleDetail
import com.merxury.blocker.feature.wake.api.navigation.navigateToWakeApp

/**
 * The detail screen of [AppDetailNavKey.packageName] for [AppDetailNavKey.userId]. The view models
 * are keyed by both, so the same app of two users keeps two screens.
 *
 * @param navigateToIfwRuleEditor opens the rule editor with the package, the component's class name
 * and its tag in Intent Firewall rules
 */
@OptIn(ExperimentalMaterial3AdaptiveApi::class)
fun EntryProviderScope<NavKey>.appDetailEntry(
    navigator: Navigator,
    updateIconThemingState: (IconThemingState) -> Unit,
    navigateToIfwRuleEditor: (String, String, String) -> Unit,
) {
    entry<AppDetailNavKey>(
        metadata = ListDetailSceneStrategy.detailPane(),
    ) { key ->
        val viewModelKey = "${key.packageName}:${key.userId}"
        AppDetailScreen(
            headerViewModel = hiltViewModel<AppHeaderViewModel, AppHeaderViewModel.Factory>(key = "header:$viewModelKey") {
                it.create(key.packageName, key.userId)
            },
            listViewModel = hiltViewModel<ComponentListViewModel, ComponentListViewModel.Factory>(key = "list:$viewModelKey") {
                it.create(key.packageName, key.userId, key.tab, key.searchKeyword)
            },
            controlViewModel = hiltViewModel<ComponentControlViewModel, ComponentControlViewModel.Factory>(key = "control:$viewModelKey") {
                it.create(key.packageName, key.userId)
            },
            snackbarHostState = LocalSnackbarHostState.current,
            updateIconThemingState = updateIconThemingState,
            onBackClick = { navigator.goBack() },
            navigateToRuleDetail = { ruleId -> navigator.navigateToRuleDetail(ruleId.toString()) },
            onShowBackgroundActivity = { navigator.navigateToWakeApp(key.packageName) },
            navigateToIfwRuleEditor = { row ->
                val ifwTag = when (row.type) {
                    ComponentType.RECEIVER -> "broadcast"
                    ComponentType.SERVICE -> "service"
                    ComponentType.ACTIVITY -> "activity"
                    ComponentType.PROVIDER -> return@AppDetailScreen
                }
                navigateToIfwRuleEditor(row.id.packageName, row.id.className, ifwTag)
            },
        )
    }
}
