/*
 * Copyright 2023 Blocker
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

package com.merxury.blocker.feature.sort.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptions
import com.google.accompanist.navigation.material.ExperimentalMaterialNavigationApi
import com.google.accompanist.navigation.material.bottomSheet
import com.merxury.blocker.feature.sort.ComponentSortBottomSheetRoute

const val COMPONENT_SORT_ROUTE = "component_sort_route"
fun NavController.navigateToComponentSortScreen(navOptions: NavOptions? = null) {
    this.navigate(COMPONENT_SORT_ROUTE, navOptions)
}

@OptIn(ExperimentalMaterialNavigationApi::class)
fun NavGraphBuilder.componentSortScreen(
    dismissHandler: () -> Unit,
) {
    bottomSheet(route = COMPONENT_SORT_ROUTE) {
        ComponentSortBottomSheetRoute(dismissHandler = dismissHandler)
    }
}
