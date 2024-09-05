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

package com.merxury.blocker.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration.Long
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.merxury.blocker.R
import com.merxury.blocker.core.data.util.PermissionStatus.NO_PERMISSION
import com.merxury.blocker.core.data.util.PermissionStatus.SHELL_USER
import com.merxury.blocker.core.designsystem.component.BlockerBackground
import com.merxury.blocker.core.designsystem.component.BlockerGradientBackground
import com.merxury.blocker.core.designsystem.component.BlockerNavigationBar
import com.merxury.blocker.core.designsystem.component.BlockerNavigationBarItem
import com.merxury.blocker.core.designsystem.component.BlockerNavigationRail
import com.merxury.blocker.core.designsystem.component.BlockerNavigationRailItem
import com.merxury.blocker.core.designsystem.component.SnackbarHost
import com.merxury.blocker.core.designsystem.component.SnackbarHostState
import com.merxury.blocker.core.designsystem.icon.Icon.DrawableResourceIcon
import com.merxury.blocker.core.designsystem.icon.Icon.ImageVectorIcon
import com.merxury.blocker.core.designsystem.theme.GradientColors
import com.merxury.blocker.core.designsystem.theme.IconThemingState
import com.merxury.blocker.core.designsystem.theme.LocalGradientColors
import com.merxury.blocker.navigation.BlockerNavHost
import com.merxury.blocker.navigation.TopLevelDestination

@Composable
fun BlockerApp(
    appState: BlockerAppState,
    updateIconBasedThemingState: (IconThemingState) -> Unit = {},
) {
    val shouldShowGradientBackground =
        appState.currentTopLevelDestination == TopLevelDestination.APP

    BlockerBackground {
        BlockerGradientBackground(
            gradientColors = if (shouldShowGradientBackground) {
                LocalGradientColors.current
            } else {
                GradientColors()
            },
        ) {
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
                updateIconBasedThemingState = updateIconBasedThemingState,
            )
        }
    }
}

@Composable
@OptIn(ExperimentalComposeUiApi::class)
internal fun BlockerApp(
    appState: BlockerAppState,
    snackbarHostState: SnackbarHostState,
    updateIconBasedThemingState: (IconThemingState) -> Unit = {},
) {
    Scaffold(
        modifier = Modifier.semantics {
            testTagsAsResourceId = true
        },
        containerColor = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onBackground,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = {
            val modifier = if (appState.shouldShowNavRail) {
                Modifier.windowInsetsPadding(
                    WindowInsets.safeDrawing.only(
                        WindowInsetsSides.Vertical,
                    ),
                )
            } else {
                Modifier
            }
            SnackbarHost(
                hostState = snackbarHostState,
                modifier = modifier,
            )
        },
        bottomBar = {
            if (appState.shouldShowBottomBar) {
                BlockerBottomBar(
                    destinations = appState.topLevelDestinations,
                    onNavigateToDestination = appState::navigateToTopLevelDestination,
                    currentTopLevelDestination = appState.currentTopLevelDestination,
                    modifier = Modifier.testTag("BlockerBottomBar"),
                )
            }
        },
    ) { padding ->
        Row(
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
            if (appState.shouldShowNavRail) {
                BlockerNavRail(
                    destinations = appState.topLevelDestinations,
                    onNavigateToDestination = appState::navigateToTopLevelDestination,
                    currentTopLevelDestination = appState.currentTopLevelDestination,
                    modifier = Modifier
                        .testTag("BlockerNavRail")
                        .safeDrawingPadding(),
                )
            }

            Column(Modifier.fillMaxSize()) {
                // TODO Show the top app bar on top level destinations.

                BlockerNavHost(
                    bottomSheetNavigator = appState.bottomSheetNavigator,
                    navController = appState.navController,
                    onBackClick = appState::onBackClick,
                    dismissBottomSheet = appState::dismissBottomSheet,
                    snackbarHostState = snackbarHostState,
                    updateIconBasedThemingState = updateIconBasedThemingState,
                )
            }

            // TODO: We may want to add padding or spacer when the snackbar is shown so that
            //  content doesn't display behind it.
        }
    }
}

@Composable
private fun BlockerNavRail(
    destinations: List<TopLevelDestination>,
    onNavigateToDestination: (TopLevelDestination) -> Unit,
    currentTopLevelDestination: TopLevelDestination?,
    modifier: Modifier = Modifier,
) {
    BlockerNavigationRail(modifier = modifier) {
        Spacer(Modifier.weight(1f))
        destinations.forEach { destination ->
            val selected = destination == currentTopLevelDestination
            BlockerNavigationRailItem(
                selected = selected,
                onClick = { onNavigateToDestination(destination) },
                icon = {
                    val icon = if (selected) {
                        destination.selectedIcon
                    } else {
                        destination.unselectedIcon
                    }
                    when (icon) {
                        is ImageVectorIcon -> Icon(
                            imageVector = icon.imageVector,
                            contentDescription = null,
                        )

                        is DrawableResourceIcon -> Icon(
                            painter = painterResource(id = icon.id),
                            contentDescription = null,
                        )
                    }
                },
                label = { Text(stringResource(destination.iconTextId)) },
                modifier = Modifier.testTag(destination.name),
            )
        }
        Spacer(Modifier.weight(1f))
    }
}

@Composable
private fun BlockerBottomBar(
    destinations: List<TopLevelDestination>,
    onNavigateToDestination: (TopLevelDestination) -> Unit,
    currentTopLevelDestination: TopLevelDestination?,
    modifier: Modifier = Modifier,
) {
    BlockerNavigationBar(
        modifier = modifier,
    ) {
        destinations.forEach { destination ->
            val selected = destination == currentTopLevelDestination
            BlockerNavigationBarItem(
                selected = selected,
                onClick = { onNavigateToDestination(destination) },
                icon = {
                    val icon = if (selected) {
                        destination.selectedIcon
                    } else {
                        destination.unselectedIcon
                    }
                    when (icon) {
                        is ImageVectorIcon -> Icon(
                            imageVector = icon.imageVector,
                            contentDescription = null,
                        )

                        is DrawableResourceIcon -> Icon(
                            painter = painterResource(id = icon.id),
                            contentDescription = null,
                        )
                    }
                },
                label = { Text(stringResource(destination.iconTextId)) },
                modifier = Modifier.testTag(destination.name),
            )
        }
    }
}
