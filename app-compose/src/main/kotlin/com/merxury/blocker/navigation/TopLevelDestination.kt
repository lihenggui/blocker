/*
 * Copyright 2024 Blocker
 * Copyright 2022 The Android Open Source Project
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

package com.merxury.blocker.navigation

import androidx.compose.ui.graphics.vector.ImageVector
import com.merxury.blocker.R
import com.merxury.blocker.core.designsystem.icon.BlockerIcons
import com.merxury.blocker.feature.applist.navigation.AppListRoute
import com.merxury.blocker.feature.generalrules.navigation.GeneralRuleRoute
import kotlin.reflect.KClass

/**
 * Type for the top level destinations in the application. Each of these destinations
 * can contain one or more screens (based on the window size). Navigation from one screen to the
 * next within a single destination will be handled directly in composables.
 */
enum class TopLevelDestination(
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val iconTextId: Int,
    val route: KClass<*>,
) {
    APP(
        selectedIcon = BlockerIcons.Apps,
        unselectedIcon = BlockerIcons.Apps,
        iconTextId = R.string.apps,
        route = AppListRoute::class,
    ),
    RULE(
        selectedIcon = BlockerIcons.GeneralRule,
        unselectedIcon = BlockerIcons.GeneralRule,
        iconTextId = R.string.sdk_trackers,
        route = GeneralRuleRoute::class,
    ),
    SEARCH(
        selectedIcon = BlockerIcons.Search,
        unselectedIcon = BlockerIcons.Search,
        iconTextId = R.string.search,
        route = AppListRoute::class,
    ),
}
