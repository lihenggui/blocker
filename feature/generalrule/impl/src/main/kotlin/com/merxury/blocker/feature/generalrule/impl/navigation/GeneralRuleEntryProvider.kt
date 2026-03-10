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

package com.merxury.blocker.feature.generalrule.impl.navigation

import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.navigation3.ListDetailSceneStrategy
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import com.merxury.blocker.core.navigation.Navigator
import com.merxury.blocker.core.ui.LocalSnackbarHostState
import com.merxury.blocker.feature.generalrule.api.navigation.GeneralRuleNavKey
import com.merxury.blocker.feature.generalrule.impl.GeneralRulesScreen
import com.merxury.blocker.feature.generalrule.impl.GeneralRulesViewModel
import com.merxury.blocker.feature.ruledetail.api.navigation.navigateToRuleDetail

@OptIn(ExperimentalMaterial3AdaptiveApi::class)
fun EntryProviderScope<NavKey>.generalRuleEntry(navigator: Navigator) {
    entry<GeneralRuleNavKey>(
        metadata = ListDetailSceneStrategy.listPane(),
    ) { key ->
        GeneralRulesScreen(
            navigateToRuleDetail = navigator::navigateToRuleDetail,
            snackbarHostState = LocalSnackbarHostState.current,
            viewModel = hiltViewModel<GeneralRulesViewModel, GeneralRulesViewModel.Factory> { factory ->
                factory.create(key.initialRuleId)
            },
        )
    }
}
