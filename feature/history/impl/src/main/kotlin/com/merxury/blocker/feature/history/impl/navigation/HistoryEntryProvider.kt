/*
 * Copyright 2026 Blocker
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

package com.merxury.blocker.feature.history.impl.navigation

import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.navigation3.ListDetailSceneStrategy
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import com.merxury.blocker.core.navigation.Navigator
import com.merxury.blocker.feature.history.api.navigation.HistoryNavKey
import com.merxury.blocker.feature.history.api.navigation.HistoryOperationNavKey
import com.merxury.blocker.feature.history.api.navigation.navigateToHistoryOperation
import com.merxury.blocker.feature.history.impl.HistoryOperationRoute
import com.merxury.blocker.feature.history.impl.HistoryOperationViewModel
import com.merxury.blocker.feature.history.impl.HistoryRoute

@OptIn(ExperimentalMaterial3AdaptiveApi::class)
fun EntryProviderScope<NavKey>.historyEntry(navigator: Navigator) {
    entry<HistoryNavKey>(metadata = ListDetailSceneStrategy.listPane()) {
        val selected = navigator.state.currentKey as? HistoryOperationNavKey
        HistoryRoute(
            selectedOperationId = selected?.id,
            navigateBack = navigator::goBack,
            onOperationClick = { id ->
                if (id != selected?.id) {
                    if (selected != null) navigator.goBack()
                    navigator.navigateToHistoryOperation(id)
                }
            },
        )
    }
    entry<HistoryOperationNavKey>(metadata = ListDetailSceneStrategy.detailPane()) { key ->
        HistoryOperationRoute(
            viewModel = hiltViewModel<HistoryOperationViewModel, HistoryOperationViewModel.Factory> { it.create(key.id) },
            navigateBack = navigator::goBack,
        )
    }
}
