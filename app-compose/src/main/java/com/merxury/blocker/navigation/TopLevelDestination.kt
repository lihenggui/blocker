/*
 * Copyright 2023 Blocker
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
import com.merxury.blocker.core.designsystem.icon.Icon.ImageVectorIcon

/**
 * Type for the top level destinations in the application. Each of these destinations
 * can contain one or more screens (based on the window size). Navigation from one screen to the
 * next within a single destination will be handled directly in composables.
 */
enum class TopLevelDestination(
    val selectedIcon: Icon,
    val unselectedIcon: Icon,
    val iconTextId: Int,
    val titleTextId: Int,
) {
    APP_LIST(
        selectedIcon = ImageVectorIcon(BlockerIcons.Apps),
        unselectedIcon = ImageVectorIcon(BlockerIcons.Apps),
        iconTextId = R.string.apps,
        titleTextId = R.string.app_name,
    ),
    GENERAL_RULE(
        selectedIcon = ImageVectorIcon(BlockerIcons.GeneralRule),
        unselectedIcon = ImageVectorIcon(BlockerIcons.GeneralRule),
        iconTextId = R.string.rules,
        titleTextId = R.string.rules,
    ),
    GLOBAL_SEARCH(
        selectedIcon = ImageVectorIcon(BlockerIcons.Search),
        unselectedIcon = ImageVectorIcon(BlockerIcons.Search),
        iconTextId = R.string.search,
        titleTextId = R.string.search,
    ),
}
