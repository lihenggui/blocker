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

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration.Long
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.WindowAdaptiveInfo
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import com.merxury.blocker.R
import com.merxury.blocker.core.data.util.PermissionStatus.NO_PERMISSION
import com.merxury.blocker.core.data.util.PermissionStatus.SHELL_USER
import com.merxury.blocker.core.designsystem.component.BlockerBackground
import com.merxury.blocker.core.designsystem.component.BlockerNavigationSuiteScaffold
import com.merxury.blocker.core.designsystem.component.SnackbarHost
import com.merxury.blocker.core.designsystem.component.SnackbarHostState
import com.merxury.blocker.core.designsystem.theme.IconThemingState
import com.merxury.blocker.navigation.BlockerNavHost
import kotlin.reflect.KClass

@Composable
fun BlockerApp(
    appState: BlockerAppState,
    modifier: Modifier = Modifier,
    updateIconThemingState: (IconThemingState) -> Unit = {},
    windowAdaptiveInfo: WindowAdaptiveInfo = currentWindowAdaptiveInfo(),
) {
    BlockerBackground(modifier = modifier) {
        val snackbarHostState = remember { SnackbarHostState() }

        val appPermission by appState.currentPermission.collectAsStateWithLifecycle()
        val noPermissionHint = stringResource(R.string.no_permission_hint)
        val shellPermissionHint = stringResource(R.string.shell_permission_hint)
        val actionLabel = stringResource(R.string.close)
        LaunchedEffect(appPermission) {
            if (appPermission == NO_PERMISSION) {
                snackbarHostState.showSnackbar(
                    message = noPermissionHint,
                    actionLabel = actionLabel,
                    duration = Long,
                )
            } else if (appPermission == SHELL_USER) {
                snackbarHostState.showSnackbar(
                    message = shellPermissionHint,
                    actionLabel = actionLabel,
                    duration = Long,
                )
            }
        }
        BlockerApp(
            appState = appState,
            snackbarHostState = snackbarHostState,
            updateIconThemingState = updateIconThemingState,
            windowAdaptiveInfo = windowAdaptiveInfo,
        )
    }
}

@Composable
@OptIn(ExperimentalComposeUiApi::class)
internal fun BlockerApp(
    appState: BlockerAppState,
    snackbarHostState: SnackbarHostState,
    modifier: Modifier = Modifier,
    updateIconThemingState: (IconThemingState) -> Unit = {},
    windowAdaptiveInfo: WindowAdaptiveInfo = currentWindowAdaptiveInfo(),
) {
    val currentDestination = appState.currentDestination

    BlockerNavigationSuiteScaffold(
        navigationSuiteItems = {
            appState.topLevelDestinations.forEach { destination ->
                val selected = currentDestination.isRouteInHierarchy(destination.route)
                item(
                    selected = selected,
                    onClick = { appState.navigateToTopLevelDestination(destination) },
                    icon = {
                        Icon(
                            imageVector = destination.unselectedIcon,
                            contentDescription = null,
                        )
                    },
                    selectedIcon = {
                        Icon(
                            imageVector = destination.selectedIcon,
                            contentDescription = null,
                        )
                    },
                    label = { Text(stringResource(destination.iconTextId)) },
                    modifier =
                    Modifier
                        .testTag("BlockerNavItem"),
                )
            }
        },
        windowAdaptiveInfo = windowAdaptiveInfo,
    ) {
        Scaffold(
            modifier = modifier.semantics {
                testTagsAsResourceId = true
            },
            contentColor = MaterialTheme.colorScheme.onBackground,
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            snackbarHost = { SnackbarHost(snackbarHostState) },
        ) { padding ->
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .consumeWindowInsets(padding)
                    .windowInsetsPadding(
                        WindowInsets.safeDrawing.only(
                            WindowInsetsSides.Horizontal,
                        ),
                    ),
            ) {
                BlockerNavHost(
                    snackbarHostState = snackbarHostState,
                    updateIconThemingState = updateIconThemingState,
                    appState = appState,
                )
            }
        }

        // TODO: We may want to add padding or spacer when the snackbar is shown so that
        //  content doesn't display behind it.
    }
}

private fun NavDestination?.isRouteInHierarchy(route: KClass<*>) =
    this?.hierarchy?.any {
        it.hasRoute(route)
    } ?: false
