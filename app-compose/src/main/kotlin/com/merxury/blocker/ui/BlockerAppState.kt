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
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import com.merxury.blocker.core.data.util.NetworkMonitor
import com.merxury.blocker.core.data.util.PermissionMonitor
import com.merxury.blocker.core.data.util.TimeZoneMonitor
import com.merxury.blocker.core.navigation.NavigationState
import com.merxury.blocker.core.navigation.rememberNavigationState
import com.merxury.blocker.core.ui.TrackDisposableJank
import com.merxury.blocker.feature.applist.api.navigation.AppListNavKey
import com.merxury.blocker.navigation.TOP_LEVEL_NAV_ITEMS
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.datetime.TimeZone

@Composable
fun rememberBlockerAppState(
    networkMonitor: NetworkMonitor,
    permissionMonitor: PermissionMonitor,
    timeZoneMonitor: TimeZoneMonitor,
    coroutineScope: CoroutineScope = rememberCoroutineScope(),
): BlockerAppState {
    val navigationState =
        rememberNavigationState(startKey = AppListNavKey, topLevelKeys = TOP_LEVEL_NAV_ITEMS.keys)

    NavigationTrackingSideEffect(navigationState)

    return remember(
        navigationState,
        coroutineScope,
        networkMonitor,
        permissionMonitor,
        timeZoneMonitor,
    ) {
        BlockerAppState(
            navigationState = navigationState,
            coroutineScope = coroutineScope,
            networkMonitor = networkMonitor,
            permissionMonitor = permissionMonitor,
            timeZoneMonitor = timeZoneMonitor,
        )
    }
}

@Stable
class BlockerAppState(
    val navigationState: NavigationState,
    coroutineScope: CoroutineScope,
    networkMonitor: NetworkMonitor,
    permissionMonitor: PermissionMonitor,
    timeZoneMonitor: TimeZoneMonitor,
) {
    val isOffline = networkMonitor.isOnline
        .map(Boolean::not)
        .stateIn(
            scope = coroutineScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = false,
        )

    val currentPermission = permissionMonitor.permissionStatus
        .stateIn(
            scope = coroutineScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = null,
        )

    val currentTimeZone = timeZoneMonitor.currentTimeZone
        .stateIn(
            coroutineScope,
            SharingStarted.WhileSubscribed(5_000),
            TimeZone.currentSystemDefault(),
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
