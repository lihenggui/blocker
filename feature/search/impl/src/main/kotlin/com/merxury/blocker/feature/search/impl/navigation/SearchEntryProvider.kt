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

package com.merxury.blocker.feature.search.impl.navigation

import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.navigation3.ListDetailSceneStrategy
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import com.merxury.blocker.core.navigation.Navigator
import com.merxury.blocker.core.ui.LocalSnackbarHostState
import com.merxury.blocker.feature.appdetail.api.navigation.navigateToAppDetail
import com.merxury.blocker.feature.ruledetail.api.navigation.navigateToRuleDetail
import com.merxury.blocker.feature.search.api.navigation.SearchNavKey
import com.merxury.blocker.feature.search.impl.OpenAppDetail
import com.merxury.blocker.feature.search.impl.SearchScreen

/** The search, as the list pane; the app and rule details it opens show next to it where there is room. */
@OptIn(ExperimentalMaterial3AdaptiveApi::class)
fun EntryProviderScope<NavKey>.searchEntry(navigator: Navigator) {
    entry<SearchNavKey>(
        metadata = ListDetailSceneStrategy.listPane(),
    ) {
        SearchScreen(
            snackbarHostState = LocalSnackbarHostState.current,
            navigateToAppDetail = navigator.openAppDetail(),
            navigateToRuleDetail = navigator::navigateToRuleDetail,
        )
    }
}

/** Opens the detail screen of an app on a tab, with the components filtered by the keyword. */
internal fun Navigator.openAppDetail(): OpenAppDetail = { packageName, userId, tab, keyword ->
    navigateToAppDetail(packageName, tab = tab.routeName, searchKeyword = keyword, userId = userId)
}
