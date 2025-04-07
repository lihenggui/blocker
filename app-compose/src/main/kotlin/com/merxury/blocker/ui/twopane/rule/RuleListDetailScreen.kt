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

package com.merxury.blocker.ui.twopane.rule

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
import com.merxury.blocker.feature.generalrules.GeneralRulesScreen
import com.merxury.blocker.feature.generalrules.navigation.GeneralRuleRoute
import com.merxury.blocker.feature.ruledetail.RuleDetailPlaceholder
import com.merxury.blocker.feature.ruledetail.RuleDetailScreen
import com.merxury.blocker.feature.ruledetail.RuleDetailViewModel
import com.merxury.blocker.feature.ruledetail.navigation.RuleDetailRoute
import com.merxury.blocker.ui.twopane.isDetailPaneVisible
import com.merxury.blocker.ui.twopane.isListPaneVisible
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlin.math.max

@Serializable
internal object RuleDetailPlaceholderRoute

fun NavGraphBuilder.ruleListDetailScreen(
    snackbarHostState: SnackbarHostState,
    navigateToAppDetail: (String) -> Unit,
    updateIconThemingState: (IconThemingState) -> Unit,
) {
    composable<GeneralRuleRoute> {
        RuleListDetailRoute(
            snackbarHostState = snackbarHostState,
            updateIconThemingState = updateIconThemingState,
            navigateToAppDetail = navigateToAppDetail,
        )
    }
}

@Composable
internal fun RuleListDetailRoute(
    snackbarHostState: SnackbarHostState,
    updateIconThemingState: (IconThemingState) -> Unit,
    navigateToAppDetail: (String) -> Unit,
    viewModel: RuleList2PaneViewModel = hiltViewModel(),
    windowAdaptiveInfo: WindowAdaptiveInfo = currentWindowAdaptiveInfo(),
) {
    val selectedRuleId by viewModel.selectedRuleId.collectAsStateWithLifecycle()
    RuleListDetailScreen(
        selectedRuleId = selectedRuleId,
        onRuleClick = viewModel::onRuleClick,
        snackbarHostState = snackbarHostState,
        updateIconThemingState = updateIconThemingState,
        navigateToAppDetail = navigateToAppDetail,
        windowAdaptiveInfo = windowAdaptiveInfo,
    )
}

@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Composable
internal fun RuleListDetailScreen(
    selectedRuleId: String?,
    snackbarHostState: SnackbarHostState,
    updateIconThemingState: (IconThemingState) -> Unit,
    navigateToAppDetail: (String) -> Unit,
    onRuleClick: (String) -> Unit,
    windowAdaptiveInfo: WindowAdaptiveInfo,
) {
    val listDetailNavigator = rememberListDetailPaneScaffoldNavigator(
        scaffoldDirective = calculatePaneScaffoldDirective(windowAdaptiveInfo),
        initialDestinationHistory = listOfNotNull(
            ThreePaneScaffoldDestinationItem(ListDetailPaneScaffoldRole.List),
            ThreePaneScaffoldDestinationItem<Nothing>(ListDetailPaneScaffoldRole.Detail).takeIf {
                selectedRuleId != null
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

    var ruleDetailRoute by remember {
        val route =
            selectedRuleId?.let { RuleDetailRoute(ruleId = it) } ?: RuleDetailPlaceholderRoute
        mutableStateOf(route)
    }

    fun onRuleClickShowDetailPane(ruleId: String) {
        onRuleClick(ruleId)
        ruleDetailRoute = RuleDetailRoute(ruleId = ruleId)
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
                    GeneralRulesScreen(
                        highlightSelectedRule = listDetailNavigator.isDetailPaneVisible(),
                        navigateToRuleDetail = ::onRuleClickShowDetailPane,
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
                    AnimatedContent(ruleDetailRoute) { route ->
                        when (route) {
                            is RuleDetailRoute -> {
                                RuleDetailScreen(
                                    showBackButton = !listDetailNavigator.isListPaneVisible(),
                                    onBackClick = {
                                        coroutineScope.launch {
                                            listDetailNavigator.navigateBack()
                                        }
                                    },
                                    snackbarHostState = snackbarHostState,
                                    navigateToAppDetail = navigateToAppDetail,
                                    updateIconThemingState = updateIconThemingState,
                                    viewModel = hiltViewModel<RuleDetailViewModel, RuleDetailViewModel.Factory>(
                                        key = route.ruleId,
                                    ) { factory ->
                                        factory.create(route.ruleId, route.tab)
                                    },
                                )
                            }

                            is RuleDetailPlaceholderRoute -> {
                                RuleDetailPlaceholder()
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
