/*
 * Copyright 2025 Blocker
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

import com.merxury.blocker.R
import com.merxury.blocker.core.designsystem.icon.BlockerIcons
import com.merxury.blocker.core.designsystem.icon.Icon
import com.merxury.blocker.feature.applist.navigation.AppListRoute
import com.merxury.blocker.feature.generalrules.navigation.GeneralRuleRoute
import com.merxury.blocker.feature.search.navigation.SearchRoute
import com.merxury.blocker.feature.sharefilter.navigation.ShareFilterRoute
import kotlin.reflect.KClass

/**
 * Type for the top level destinations in the application. Each of these destinations
 * can contain one or more screens (based on the window size). Navigation from one screen to the
 * next within a single destination will be handled directly in composables.
 */
enum class TopLevelDestination(
    val selectedIcon: Icon,
    val unselectedIcon: Icon,
    val iconTextId: Int,
    val route: KClass<*>,
) {
    APP(
        selectedIcon = Icon.ImageVectorIcon(BlockerIcons.Apps),
        unselectedIcon = Icon.ImageVectorIcon(BlockerIcons.Apps),
        iconTextId = R.string.apps,
        route = AppListRoute::class,
    ),
    RULE(
        selectedIcon = Icon.ImageVectorIcon(BlockerIcons.GeneralRule),
        unselectedIcon = Icon.ImageVectorIcon(BlockerIcons.GeneralRule),
        iconTextId = R.string.sdk_trackers,
        route = GeneralRuleRoute::class,
    ),
    SHARE_FILTER(
        selectedIcon = Icon.DrawableResourceIcon(BlockerIcons.ShareOff),
        unselectedIcon = Icon.DrawableResourceIcon(BlockerIcons.ShareOff),
        iconTextId = R.string.feature_sharefilter_title,
        route = ShareFilterRoute::class,
    ),
    SEARCH(
        selectedIcon = Icon.ImageVectorIcon(BlockerIcons.Search),
        unselectedIcon = Icon.ImageVectorIcon(BlockerIcons.Search),
        iconTextId = R.string.search,
        route = SearchRoute::class,
    ),
}
