// Copyright 2026 Blocker
// SPDX-License-Identifier: Apache-2.0

package com.merxury.blocker.feature.wake.impl.navigation

import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.navigation3.ListDetailSceneStrategy
import androidx.compose.ui.unit.LayoutDirection
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import com.merxury.blocker.core.navigation.Navigator
import com.merxury.blocker.feature.wake.api.navigation.WakeOverviewNavKey
import com.merxury.blocker.feature.wake.api.navigation.navigateToWakeApp
import com.merxury.blocker.feature.wake.impl.WakeOverviewRoute

@OptIn(ExperimentalMaterial3AdaptiveApi::class, ExperimentalMaterial3ExpressiveApi::class)
fun EntryProviderScope<NavKey>.wakeOverviewEntry(navigator: Navigator, motion: MotionScheme, layoutDirection: LayoutDirection) {
    entry<WakeOverviewNavKey>(metadata = ListDetailSceneStrategy.listPane() + wakeSceneTransitions(motion, layoutDirection)) {
        WakeOverviewRoute(onAppClick = navigator::navigateToWakeApp)
    }
}
