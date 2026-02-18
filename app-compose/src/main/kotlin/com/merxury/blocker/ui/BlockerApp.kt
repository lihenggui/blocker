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
import androidx.compose.foundation.layout.exclude
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration.Long
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.WindowAdaptiveInfo
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.material3.adaptive.navigation3.rememberListDetailSceneStrategy
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.ui.NavDisplay
import com.merxury.blocker.R
import com.merxury.blocker.core.data.util.PermissionStatus.NO_PERMISSION
import com.merxury.blocker.core.data.util.PermissionStatus.SHELL_USER
import com.merxury.blocker.core.designsystem.component.BlockerBackground
import com.merxury.blocker.core.designsystem.component.BlockerNavigationSuiteScaffold
import com.merxury.blocker.core.designsystem.icon.Icon.DrawableResourceIcon
import com.merxury.blocker.core.designsystem.icon.Icon.ImageVectorIcon
import com.merxury.blocker.core.designsystem.theme.IconThemingState
import com.merxury.blocker.core.navigation.Navigator
import com.merxury.blocker.core.navigation.toEntries
import com.merxury.blocker.core.ui.LocalSnackbarHostState
import com.merxury.blocker.feature.appdetail.impl.navigation.appDetailEntry
import com.merxury.blocker.feature.applist.impl.navigation.appListEntry
import com.merxury.blocker.feature.debloater.impl.navigation.debloaterEntry
import com.merxury.blocker.feature.generalrule.impl.navigation.generalRuleEntry
import com.merxury.blocker.feature.impl.helpandfeedback.navigation.supportAndFeedbackEntry
import com.merxury.blocker.feature.impl.licenses.navigation.licensesEntry
import com.merxury.blocker.feature.impl.settings.navigation.settingsEntry
import com.merxury.blocker.feature.ruledetail.impl.navigation.ruleDetailEntry
import com.merxury.blocker.feature.search.impl.navigation.searchEntry
import com.merxury.blocker.navigation.TOP_LEVEL_NAV_ITEMS

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
        CompositionLocalProvider(LocalSnackbarHostState provides snackbarHostState) {
            BlockerApp(
                appState = appState,
                snackbarHostState = snackbarHostState,
                updateIconThemingState = updateIconThemingState,
                windowAdaptiveInfo = windowAdaptiveInfo,
            )
        }
    }
}

@Composable
@OptIn(ExperimentalComposeUiApi::class, ExperimentalMaterial3AdaptiveApi::class)
internal fun BlockerApp(
    appState: BlockerAppState,
    snackbarHostState: SnackbarHostState,
    modifier: Modifier = Modifier,
    updateIconThemingState: (IconThemingState) -> Unit = {},
    windowAdaptiveInfo: WindowAdaptiveInfo = currentWindowAdaptiveInfo(),
) {
    val navigator = remember { Navigator(appState.navigationState) }

    BlockerNavigationSuiteScaffold(
        navigationSuiteItems = {
            TOP_LEVEL_NAV_ITEMS.forEach { (navKey, navItem) ->
                val selected = navKey == appState.navigationState.currentTopLevelKey
                item(
                    selected = selected,
                    onClick = { navigator.navigate(navKey) },
                    icon = {
                        when (val icon = navItem.unselectedIcon) {
                            is ImageVectorIcon -> {
                                Icon(
                                    imageVector = icon.imageVector,
                                    contentDescription = null,
                                )
                            }

                            is DrawableResourceIcon -> {
                                Icon(
                                    painter = painterResource(icon.id),
                                    contentDescription = null,
                                )
                            }
                        }
                    },
                    selectedIcon = {
                        when (val icon = navItem.selectedIcon) {
                            is ImageVectorIcon -> {
                                Icon(
                                    imageVector = icon.imageVector,
                                    contentDescription = null,
                                )
                            }

                            is DrawableResourceIcon -> {
                                Icon(
                                    painter = painterResource(icon.id),
                                    contentDescription = null,
                                )
                            }
                        }
                    },
                    label = { Text(stringResource(navItem.iconTextId)) },
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
            snackbarHost = {
                SnackbarHost(
                    snackbarHostState,
                    modifier = Modifier.windowInsetsPadding(
                        WindowInsets.safeDrawing.exclude(
                            WindowInsets.ime,
                        ),
                    ),
                )
            },
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
                val listDetailStrategy = rememberListDetailSceneStrategy<NavKey>()

                val entryProvider = entryProvider {
                    appListEntry(navigator)
                    appDetailEntry(navigator, updateIconThemingState)
                    generalRuleEntry(navigator)
                    ruleDetailEntry(navigator)
                    debloaterEntry()
                    searchEntry(navigator)
                    supportAndFeedbackEntry(navigator)
                    licensesEntry(navigator)
                    settingsEntry(navigator)
                }

                NavDisplay(
                    entries = appState.navigationState.toEntries(entryProvider),
                    sceneStrategy = listDetailStrategy,
                    onBack = { navigator.goBack() },
                )
            }
        }

        // TODO: We may want to add padding or spacer when the snackbar is shown so that
        //  content doesn't display behind it.
    }
}
