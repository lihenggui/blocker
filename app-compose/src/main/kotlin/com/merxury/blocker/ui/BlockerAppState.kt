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

package com.merxury.blocker.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.DisposableEffectResult
import androidx.compose.runtime.DisposableEffectScope
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalView
import androidx.metrics.performance.PerformanceMetricsState
import androidx.metrics.performance.PerformanceMetricsState.Holder
import com.merxury.blocker.core.data.util.PermissionMonitor
import com.merxury.blocker.core.navigation.NavigationState
import com.merxury.blocker.core.navigation.rememberNavigationState
import com.merxury.blocker.feature.applist.api.navigation.AppListNavKey
import com.merxury.blocker.navigation.TOP_LEVEL_NAV_ITEMS
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn

@Composable
fun rememberBlockerAppState(
    permissionMonitor: PermissionMonitor,
    coroutineScope: CoroutineScope = rememberCoroutineScope(),
): BlockerAppState {
    val navigationState =
        rememberNavigationState(startKey = AppListNavKey, topLevelKeys = TOP_LEVEL_NAV_ITEMS.keys)

    NavigationTrackingSideEffect(navigationState)

    return remember(
        navigationState,
        coroutineScope,
        permissionMonitor,
    ) {
        BlockerAppState(
            navigationState = navigationState,
            coroutineScope = coroutineScope,
            permissionMonitor = permissionMonitor,
        )
    }
}

@Stable
class BlockerAppState(
    val navigationState: NavigationState,
    coroutineScope: CoroutineScope,
    permissionMonitor: PermissionMonitor,
) {
    val currentPermission = permissionMonitor.permissionStatus
        .stateIn(
            scope = coroutineScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = null,
        )
}

/**
 * Stores information about navigation events to be used with JankStats
 */
@Composable
private fun NavigationTrackingSideEffect(navigationState: NavigationState) {
    TrackDisposableJank(navigationState.currentKey) { metricsHolder ->
        metricsHolder.state?.putState("Navigation", navigationState.currentKey.toString())
        onDispose {}
    }
}

@Composable
private fun TrackDisposableJank(
    vararg keys: Any,
    reportMetric: DisposableEffectScope.(state: Holder) -> DisposableEffectResult,
) {
    val localView = LocalView.current
    val metrics = remember(localView) {
        PerformanceMetricsState.getHolderForHierarchy(localView)
    }
    DisposableEffect(metrics, *keys) {
        reportMetric(this, metrics)
    }
}
