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

package com.merxury.blocker.feature.applist.impl.navigation

import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.navigation3.ListDetailSceneStrategy
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import com.merxury.blocker.core.navigation.Navigator
import com.merxury.blocker.feature.appdetail.api.navigation.navigateToAppDetail
import com.merxury.blocker.feature.applist.api.navigation.AppListNavKey
import com.merxury.blocker.feature.applist.impl.AppListScreen
import com.merxury.blocker.feature.applist.impl.AppListViewModel
import com.merxury.blocker.feature.settings.api.navigation.navigateToSettings
import com.merxury.blocker.feature.settings.api.navigation.navigateToSupportAndFeedback

@OptIn(ExperimentalMaterial3AdaptiveApi::class)
fun EntryProviderScope<NavKey>.appListEntry(navigator: Navigator) {
    entry<AppListNavKey>(
        metadata = ListDetailSceneStrategy.listPane(),
    ) { key ->
        val initialPackageName = key.initialPackageName
        AppListScreen(
            navigateToAppDetail = navigator::navigateToAppDetail,
            navigateToSettings = navigator::navigateToSettings,
            navigateToSupportAndFeedback = navigator::navigateToSupportAndFeedback,
            viewModel = hiltViewModel<AppListViewModel, AppListViewModel.Factory>(
                key = initialPackageName,
            ) { factory ->
                factory.create(initialPackageName)
            },
        )
    }
}
