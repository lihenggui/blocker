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

package com.merxury.blocker.feature.ifwrule.impl.navigation

import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import com.merxury.blocker.core.navigation.Navigator
import com.merxury.blocker.core.ui.LocalSnackbarHostState
import com.merxury.blocker.feature.ifwrule.api.navigation.IfwRuleEditorNavKey
import com.merxury.blocker.feature.ifwrule.impl.IfwRuleEditorScreen
import com.merxury.blocker.feature.ifwrule.impl.IfwRuleEditorViewModel

@OptIn(ExperimentalMaterial3AdaptiveApi::class)
fun EntryProviderScope<NavKey>.ifwRuleEditorEntry(navigator: Navigator) {
    entry<IfwRuleEditorNavKey> { key ->
        IfwRuleEditorScreen(
            snackbarHostState = LocalSnackbarHostState.current,
            onBackClick = navigator::goBack,
            viewModel = hiltViewModel<IfwRuleEditorViewModel, IfwRuleEditorViewModel.Factory>(
                key = "${key.packageName}/${key.componentName}",
            ) { factory ->
                factory.create(key.packageName, key.componentName, key.componentType)
            },
        )
    }
}
