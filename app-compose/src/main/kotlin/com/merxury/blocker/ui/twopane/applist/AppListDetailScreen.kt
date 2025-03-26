/*
 * Copyright 2025 Blocker
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

package com.merxury.blocker.ui.twopane.applist

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.VerticalDragHandle
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.WindowAdaptiveInfo
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.material3.adaptive.layout.AnimatedPane
import androidx.compose.material3.adaptive.layout.ListDetailPaneScaffoldRole
import androidx.compose.material3.adaptive.layout.PaneExpansionAnchor
import androidx.compose.material3.adaptive.layout.ThreePaneScaffoldDestinationItem
import androidx.compose.material3.adaptive.layout.calculatePaneScaffoldDirective
import androidx.compose.material3.adaptive.layout.defaultDragHandleSemantics
import androidx.compose.material3.adaptive.layout.rememberPaneExpansionState
import androidx.compose.material3.adaptive.navigation.BackNavigationBehavior
import androidx.compose.material3.adaptive.navigation.NavigableListDetailPaneScaffold
import androidx.compose.material3.adaptive.navigation.ThreePaneScaffoldPredictiveBackHandler
import androidx.compose.material3.adaptive.navigation.rememberListDetailPaneScaffoldNavigator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.merxury.blocker.core.designsystem.component.SnackbarHostState
import com.merxury.blocker.core.designsystem.theme.IconThemingState
import com.merxury.blocker.feature.appdetail.AppDetailPlaceholder
import com.merxury.blocker.feature.appdetail.AppDetailScreen
import com.merxury.blocker.feature.appdetail.AppDetailViewModel
import com.merxury.blocker.feature.appdetail.navigation.AppDetailRoute
import com.merxury.blocker.feature.applist.AppListScreen
import com.merxury.blocker.feature.applist.navigation.AppListRoute
import com.merxury.blocker.ui.twopane.isDetailPaneVisible
import com.merxury.blocker.ui.twopane.isListPaneVisible
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlin.math.max

@Serializable
internal object AppDetailPlaceholderRoute

fun NavGraphBuilder.appListDetailScreen(
    navigateToSettings: () -> Unit,
    navigateToSupportAndFeedback: () -> Unit,
    snackbarHostState: SnackbarHostState,
    updateIconThemingState: (IconThemingState) -> Unit,
    navigateToComponentDetail: (String) -> Unit,
    navigateToRuleDetail: (String) -> Unit,
) {
    composable<AppListRoute> {
        AppListDetailRoute(
            navigateToSettings = navigateToSettings,
            navigateToSupportAndFeedback = navigateToSupportAndFeedback,
            snackbarHostState = snackbarHostState,
            navigateToComponentDetail = navigateToComponentDetail,
            navigateToRuleDetail = navigateToRuleDetail,
            updateIconThemingState = updateIconThemingState,
        )
    }
}

@Composable
internal fun AppListDetailRoute(
    navigateToSettings: () -> Unit,
    navigateToSupportAndFeedback: () -> Unit,
    snackbarHostState: SnackbarHostState,
    updateIconThemingState: (IconThemingState) -> Unit,
    navigateToComponentDetail: (String) -> Unit,
    navigateToRuleDetail: (String) -> Unit,
    viewModel: AppList2PaneViewModel = hiltViewModel(),
    windowAdaptiveInfo: WindowAdaptiveInfo = currentWindowAdaptiveInfo(),
) {
    val selectedPackageName by viewModel.selectedPackageName.collectAsStateWithLifecycle()
    AppListDetailScreen(
        navigateToSettings = navigateToSettings,
        navigateToSupportAndFeedback = navigateToSupportAndFeedback,
        selectedPackageName = selectedPackageName,
        onAppClick = viewModel::onAppClick,
        snackbarHostState = snackbarHostState,
        navigateToComponentDetail = navigateToComponentDetail,
        navigateToRuleDetail = navigateToRuleDetail,
        updateIconThemingState = updateIconThemingState,
        windowAdaptiveInfo = windowAdaptiveInfo,
    )
}

@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Composable
internal fun AppListDetailScreen(
    navigateToSettings: () -> Unit,
    navigateToSupportAndFeedback: () -> Unit,
    selectedPackageName: String?,
    snackbarHostState: SnackbarHostState,
    updateIconThemingState: (IconThemingState) -> Unit,
    navigateToComponentDetail: (String) -> Unit,
    navigateToRuleDetail: (String) -> Unit,
    onAppClick: (String) -> Unit,
    windowAdaptiveInfo: WindowAdaptiveInfo,
) {
    val listDetailNavigator = rememberListDetailPaneScaffoldNavigator(
        scaffoldDirective = calculatePaneScaffoldDirective(windowAdaptiveInfo),
        initialDestinationHistory = listOfNotNull(
            ThreePaneScaffoldDestinationItem(ListDetailPaneScaffoldRole.List),
            ThreePaneScaffoldDestinationItem<Nothing>(ListDetailPaneScaffoldRole.Detail).takeIf {
                selectedPackageName != null
            },
        ),
    )
    val coroutineScope = rememberCoroutineScope()

    val paneExpansionState = rememberPaneExpansionState(
        anchors = listOf(
            PaneExpansionAnchor.Proportion(0f),
            PaneExpansionAnchor.Proportion(0.5f),
            PaneExpansionAnchor.Proportion(1f),

            ),
    )

    ThreePaneScaffoldPredictiveBackHandler(
        listDetailNavigator,
        BackNavigationBehavior.PopUntilScaffoldValueChange,
    )
    BackHandler(
        paneExpansionState.currentAnchor == PaneExpansionAnchor.Proportion(0f) &&
            listDetailNavigator.isListPaneVisible() &&
            listDetailNavigator.isDetailPaneVisible(),
    ) {
        coroutineScope.launch {
            paneExpansionState.animateTo(PaneExpansionAnchor.Proportion(1f))
        }
    }

    var appDetailRoute by remember {
        val route =
            selectedPackageName?.let { AppDetailRoute(packageName = it) }
                ?: AppDetailPlaceholderRoute
        mutableStateOf(route)
    }

    fun onAppClickShowDetailPane(packageName: String) {
        onAppClick(packageName)
        appDetailRoute = AppDetailRoute(packageName = packageName)
        coroutineScope.launch {
            listDetailNavigator.navigateTo(ListDetailPaneScaffoldRole.Detail)
        }
        if (paneExpansionState.currentAnchor == PaneExpansionAnchor.Proportion(1f)) {
            coroutineScope.launch {
                paneExpansionState.animateTo(PaneExpansionAnchor.Proportion(0f))
            }
        }
    }

    val mutableInteractionSource = remember { MutableInteractionSource() }
    val minPaneWidth = 300.dp

    NavigableListDetailPaneScaffold(
        navigator = listDetailNavigator,
        listPane = {
            AnimatedPane {
                Box(
                    modifier = Modifier
                        .clipToBounds()
                        .layout { measurable, constraints ->
                            val width = max(minPaneWidth.roundToPx(), constraints.maxWidth)
                            val placeable = measurable.measure(
                                constraints.copy(
                                    minWidth = minPaneWidth.roundToPx(),
                                    maxWidth = width,
                                ),
                            )
                            layout(constraints.maxWidth, placeable.height) {
                                placeable.placeRelative(
                                    x = 0,
                                    y = 0,
                                )
                            }
                        },
                ) {
                    AppListScreen(
                        navigateToAppDetail = ::onAppClickShowDetailPane,
                        navigateToSettings = navigateToSettings,
                        navigateToSupportAndFeedback = navigateToSupportAndFeedback,
                        highlightSelectedApp = listDetailNavigator.isDetailPaneVisible(),
                    )
                }

            }
        },
        detailPane = {
            AnimatedPane {
                Box(
                    modifier = Modifier
                        .clipToBounds()
                        .layout { measurable, constraints ->
                            val width = max(minPaneWidth.roundToPx(), constraints.maxWidth)
                            val placeable = measurable.measure(
                                constraints.copy(
                                    minWidth = minPaneWidth.roundToPx(),
                                    maxWidth = width,
                                ),
                            )
                            layout(constraints.maxWidth, placeable.height) {
                                placeable.placeRelative(
                                    x = constraints.maxWidth -
                                        max(constraints.maxWidth, placeable.width),
                                    y = 0,
                                )
                            }
                        },
                ) {
                    AnimatedContent(appDetailRoute) { route ->
                        when (route) {
                            is AppDetailRoute -> {
                                AppDetailScreen(
                                    snackbarHostState = snackbarHostState,
                                    showBackButton = !listDetailNavigator.isListPaneVisible(),
                                    onBackClick = {
                                        coroutineScope.launch {
                                            listDetailNavigator.navigateBack()
                                        }
                                    },
                                    navigateToRuleDetail = navigateToRuleDetail,
                                    updateIconThemingState = updateIconThemingState,
                                    navigateToComponentDetail = navigateToComponentDetail,
                                    viewModel = hiltViewModel<AppDetailViewModel, AppDetailViewModel.Factory>(
                                        key = route.packageName,
                                    ) { factory ->
                                        factory.create(
                                            packageName = route.packageName,
                                            tab = route.tab,
                                            searchKeyword = route.searchKeyword,
                                        )
                                    },
                                )
                            }

                            is AppDetailPlaceholderRoute -> {
                                AppDetailPlaceholder()
                            }
                        }
                    }
                }
            }
        },
        paneExpansionState = paneExpansionState,
        paneExpansionDragHandle = {
            VerticalDragHandle(
                modifier = Modifier.paneExpansionDraggable(
                    state = paneExpansionState,
                    minTouchTargetSize = LocalMinimumInteractiveComponentSize.current,
                    interactionSource = mutableInteractionSource,
                    semanticsProperties = paneExpansionState.defaultDragHandleSemantics(),
                ),
                interactionSource = mutableInteractionSource,
            )
        },
    )
}
