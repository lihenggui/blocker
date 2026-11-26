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

package com.merxury.blocker.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CollectionsBookmark
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.CollectionsBookmark
import androidx.compose.material.icons.outlined.Search
import com.google.common.truth.Truth.assertThat
import com.merxury.blocker.core.designsystem.icon.Icon.ImageVectorIcon
import com.merxury.blocker.feature.applist.api.navigation.AppListNavKey
import com.merxury.blocker.feature.generalrule.api.navigation.GeneralRuleNavKey
import com.merxury.blocker.feature.search.api.navigation.SearchNavKey
import com.merxury.blocker.feature.wake.api.navigation.WakeOverviewNavKey
import org.junit.Test

class TopLevelNavItemTest {

    @Test
    fun whenListingDestinations_thenAppsBackgroundRulesAndSearchAppearInOrder() {
        assertThat(TOP_LEVEL_NAV_ITEMS.keys).containsExactly(
            AppListNavKey,
            WakeOverviewNavKey,
            GeneralRuleNavKey(),
            SearchNavKey,
        ).inOrder()
    }

    @Test
    fun whenListingDestinationIcons_thenEveryDestinationUsesFilledAndOutlinedVariants() {
        val icons = TOP_LEVEL_NAV_ITEMS.values.map { it.selectedIcon to it.unselectedIcon }

        assertThat(icons).containsExactly(
            ImageVectorIcon(Icons.Filled.Apps) to ImageVectorIcon(Icons.Outlined.Apps),
            ImageVectorIcon(Icons.Filled.Bolt) to ImageVectorIcon(Icons.Outlined.Bolt),
            ImageVectorIcon(Icons.Filled.CollectionsBookmark) to ImageVectorIcon(Icons.Outlined.CollectionsBookmark),
            ImageVectorIcon(Icons.Filled.Search) to ImageVectorIcon(Icons.Outlined.Search),
        ).inOrder()
    }
}
