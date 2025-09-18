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

package com.merxury.blocker.ui.twopane.search

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
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.merxury.blocker.core.designsystem.component.SnackbarHostState
import com.merxury.blocker.core.designsystem.theme.IconThemingState
import com.merxury.blocker.core.ui.AppDetailTabs
import com.merxury.blocker.feature.appdetail.AppDetailScreen
import com.merxury.blocker.feature.appdetail.AppDetailViewModel
import com.merxury.blocker.feature.appdetail.navigation.AppDetailRoute
import com.merxury.blocker.feature.ruledetail.RuleDetailScreen
import com.merxury.blocker.feature.ruledetail.RuleDetailViewModel
import com.merxury.blocker.feature.ruledetail.navigation.RuleDetailRoute
import com.merxury.blocker.feature.search.SearchScreen
import com.merxury.blocker.feature.search.navigation.SearchRoute
import com.merxury.blocker.ui.twopane.isDetailPaneVisible
import com.merxury.blocker.ui.twopane.isListPaneVisible
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlin.math.max

@Serializable
internal object SearchPlaceholderRoute

fun NavGraphBuilder.searchListDetailScreen(
    snackbarHostState: SnackbarHostState,
    updateIconThemingState: (IconThemingState) -> Unit,
    navigateToComponentDetail: (String) -> Unit,
) {
    composable<SearchRoute> {
        SearchListDetailScreen(
            snackbarHostState = snackbarHostState,
            updateIconThemingState = updateIconThemingState,
            navigateToComponentDetail = navigateToComponentDetail,
        )
    }
}

@Composable
internal fun SearchListDetailScreen(
    snackbarHostState: SnackbarHostState,
    updateIconThemingState: (IconThemingState) -> Unit,
    navigateToComponentDetail: (String) -> Unit,
    viewModel: Search2PaneViewModel = hiltViewModel(),
    windowAdaptiveInfo: WindowAdaptiveInfo = currentWindowAdaptiveInfo(),
) {
    val selectedPackageName by viewModel.selectedPackageName.collectAsStateWithLifecycle()
    val selectedAppTabs by viewModel.selectedAppTabs.collectAsStateWithLifecycle()
    val searchKeywords by viewModel.searchKeyword.collectAsStateWithLifecycle()
    val selectedRuleId by viewModel.selectedRuleId.collectAsStateWithLifecycle()
    val isAppDetailPage by viewModel.isAppDetailPage.collectAsStateWithLifecycle()
    SearchListDetailScreen(
        snackbarHostState = snackbarHostState,
        selectedPackageName = selectedPackageName,
        selectedRuleId = selectedRuleId,
        onAppClick = viewModel::onAppClick,
        onRuleClick = viewModel::onRuleClick,
        updateIconThemingState = updateIconThemingState,
        navigateToComponentDetail = navigateToComponentDetail,
        windowAdaptiveInfo = windowAdaptiveInfo,
    )
}

@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Composable
internal fun SearchListDetailScreen(
    snackbarHostState: SnackbarHostState,
    windowAdaptiveInfo: WindowAdaptiveInfo,
    selectedPackageName: String? = null,
    selectedRuleId: String? = null,
    onAppClick: (String, String, List<String>) -> Unit = { _, _, _ -> },
    onRuleClick: (String) -> Unit = {},
    updateIconThemingState: (IconThemingState) -> Unit = {},
    navigateToComponentDetail: (String) -> Unit = {},
) {
    val listDetailNavigator = rememberListDetailPaneScaffoldNavigator(
        scaffoldDirective = calculatePaneScaffoldDirective(windowAdaptiveInfo),
        initialDestinationHistory = listOfNotNull(
            ThreePaneScaffoldDestinationItem(ListDetailPaneScaffoldRole.List),
            ThreePaneScaffoldDestinationItem<Nothing>(ListDetailPaneScaffoldRole.Detail).takeIf {
                selectedRuleId != null || selectedPackageName != null
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

    var searchDetailRoute by remember {
        val route =
            if (selectedRuleId != null) {
                RuleDetailRoute(ruleId = selectedRuleId)
            } else if (selectedPackageName != null) {
                SearchPlaceholderRoute
            } else {
                SearchPlaceholderRoute
            }
        mutableStateOf(route)
    }

    fun onPackageNameClickShowDetailPane(
        packageName: String,
    ) {
        onAppClick(packageName, AppDetailTabs.Info.name, listOf())
        searchDetailRoute = AppDetailRoute(
            packageName = packageName,
            tab = AppDetailTabs.Info.name,
            searchKeyword = listOf(),
        )
        coroutineScope.launch {
            listDetailNavigator.navigateTo(ListDetailPaneScaffoldRole.Detail)
        }
        if (paneExpansionState.currentAnchor == PaneExpansionAnchor.Proportion(1f)) {
            coroutineScope.launch {
                paneExpansionState.animateTo(PaneExpansionAnchor.Proportion(0f))
            }
        }
    }

    fun onPackageComponentClickShowDetailPane(
        packageName: String,
        tab: String = AppDetailTabs.Info.name,
        searchKeyword: List<String> = listOf(),
    ) {
        onAppClick(packageName, tab, searchKeyword)
        searchDetailRoute = AppDetailRoute(
            packageName = packageName,
            tab = tab,
            searchKeyword = searchKeyword,
        )
        coroutineScope.launch {
            listDetailNavigator.navigateTo(ListDetailPaneScaffoldRole.Detail)
        }
        if (paneExpansionState.currentAnchor == PaneExpansionAnchor.Proportion(1f)) {
            coroutineScope.launch {
                paneExpansionState.animateTo(PaneExpansionAnchor.Proportion(0f))
            }
        }
    }

    fun onRuleClickShowDetailPane(ruleId: String) {
        onRuleClick(ruleId)
        searchDetailRoute = RuleDetailRoute(ruleId = ruleId)
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
                    SearchScreen(
                        snackbarHostState = snackbarHostState,
                        navigateToAppDetail = ::onPackageComponentClickShowDetailPane,
                        navigateToRuleDetail = ::onRuleClickShowDetailPane,
                        highlightSelectedItem = listDetailNavigator.isDetailPaneVisible(),
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
                    AnimatedContent(searchDetailRoute) { route ->
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
                                    navigateToRuleDetail = ::onRuleClickShowDetailPane,
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

                            is RuleDetailRoute -> {
                                RuleDetailScreen(
                                    showBackButton = !listDetailNavigator.isListPaneVisible(),
                                    onBackClick = {
                                        coroutineScope.launch {
                                            listDetailNavigator.navigateBack()
                                        }
                                    },
                                    snackbarHostState = snackbarHostState,
                                    navigateToAppDetail = ::onPackageNameClickShowDetailPane,
                                    updateIconThemingState = updateIconThemingState,
                                    viewModel = hiltViewModel<RuleDetailViewModel, RuleDetailViewModel.Factory>(
                                        key = route.ruleId,
                                    ) { factory ->
                                        factory.create(route.ruleId, route.tab)
                                    },
                                )
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
                ),
                interactionSource = mutableInteractionSource,
            )
        },
    )
}
