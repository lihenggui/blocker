// Copyright 2026 Blocker
// SPDX-License-Identifier: Apache-2.0

package com.merxury.blocker.feature.wake.impl.navigation

import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.navigation3.ListDetailSceneStrategy
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import com.merxury.blocker.core.navigation.Navigator
import com.merxury.blocker.feature.appdetail.api.navigation.FIRST_COMPONENT_TAB
import com.merxury.blocker.feature.appdetail.api.navigation.navigateToAppDetail
import com.merxury.blocker.feature.wake.api.navigation.WakeAppNavKey
import com.merxury.blocker.feature.wake.api.navigation.navigateToWakeApp
import com.merxury.blocker.feature.wake.impl.WakeAppRoute
import com.merxury.blocker.feature.wake.impl.WakeAppViewModel

@OptIn(ExperimentalMaterial3AdaptiveApi::class)
fun EntryProviderScope<NavKey>.wakeAppEntry(navigator: Navigator, transitions: WakeTransitions) {
    entry<WakeAppNavKey>(metadata = ListDetailSceneStrategy.detailPane() + transitions.metadata) { key ->
        WakeAppRoute(
            viewModel = hiltViewModel<WakeAppViewModel, WakeAppViewModel.Factory>(key = key.packageName) {
                it.create(key.packageName)
            },
            active = navigator.isCurrent(key),
            onBack = navigator::goBack,
            onPeerClick = navigator::navigateToWakeApp,
            onComponents = { navigator.navigateToAppDetail(key.packageName, tab = FIRST_COMPONENT_TAB) },
        )
    }
}
