/*
 * Copyright 2023 Blocker
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

package com.merxury.blocker.feature.appdetail

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import com.merxury.blocker.core.testing.testing.data.appInfoTestData
import com.merxury.blocker.core.testing.testing.data.componentInfoTestData
import com.merxury.blocker.core.ui.AppDetailTabs
import com.merxury.blocker.core.ui.AppDetailTabs.Activity
import com.merxury.blocker.core.ui.AppDetailTabs.Info
import com.merxury.blocker.core.ui.AppDetailTabs.Provider
import com.merxury.blocker.core.ui.AppDetailTabs.Receiver
import com.merxury.blocker.core.ui.AppDetailTabs.Service
import com.merxury.blocker.core.ui.TabState
import com.merxury.blocker.core.ui.bottomsheet.ComponentSortInfoUiState
import com.merxury.blocker.core.ui.data.UiMessage
import com.merxury.blocker.core.ui.state.toolbar.AppBarAction.MORE
import com.merxury.blocker.core.ui.state.toolbar.AppBarAction.SEARCH
import com.merxury.blocker.core.ui.state.toolbar.AppBarUiState
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import com.merxury.blocker.core.ui.R as uiR

class AppDetailScreenTest {
    @get:Rule(order = 0)
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private lateinit var errorMessage: UiMessage
    private lateinit var ifwRulesText: String
    private lateinit var tabState: TabState<AppDetailTabs>
    private lateinit var receiverText: String

    @Before
    fun setup() {
        composeTestRule.activity.apply {
            ifwRulesText = getString(R.string.feature_appdetail_ifw_rules)
            errorMessage = UiMessage("Can't find apps in this device.")
            tabState =
                TabState(
                    items = listOf(
                        Info,
                        Receiver,
                        Service,
                        Activity,
                        Provider,
                    ),
                    selectedItem = Info,
                )
            receiverText = getString(uiR.string.core_ui_receiver_with_count, 1)
        }
    }

    @Test
    fun circularProgressIndicator_whenScreenIsLoading_exists() {
        composeTestRule.setContent {
            BoxWithConstraints {
                AppDetailScreen(
                    appInfoUiState = AppInfoUiState.Loading,
                    topAppBarUiState = AppBarUiState(),
                    componentListUiState = ComponentListUiState(),
                    tabState = tabState,
                    bottomSheetState = ComponentSortInfoUiState.Loading,
                    onBackClick = {},
                    onLaunchAppClick = {},
                    switchTab = {},
                )
            }
        }

        composeTestRule
            .onNodeWithContentDescription(
                composeTestRule.activity.resources.getString(uiR.string.core_ui_loading),
            )
            .assertExists()
    }

    @Test
    fun errorIndicator_whenFailToLoadInfo_exists() {
        composeTestRule.setContent {
            BoxWithConstraints {
                AppDetailScreen(
                    appInfoUiState = AppInfoUiState.Error(errorMessage),
                    topAppBarUiState = AppBarUiState(),
                    componentListUiState = ComponentListUiState(),
                    tabState = tabState,
                    bottomSheetState = ComponentSortInfoUiState.Loading,
                    onBackClick = {},
                    onLaunchAppClick = {},
                    switchTab = {},
                )
            }
        }

        composeTestRule
            .onNodeWithContentDescription(
                composeTestRule.activity.resources.getString(uiR.string.core_ui_error),
            )
            .assertExists()
        composeTestRule.onNodeWithText(errorMessage.title).assertExists()
    }

    @Test
    fun tabSelector_whenComponentTabSelected_showSearchMoreIcon() {
        val tabState =
            TabState(
                items = listOf(
                    Info,
                    Receiver,
                ),
                selectedItem = Receiver,
            )
        composeTestRule.setContent {
            BoxWithConstraints {
                AppDetailScreen(
                    appInfoUiState = AppInfoUiState.Success(
                        appInfo = appInfoTestData,
                        iconBasedTheming = null,
                    ),
                    topAppBarUiState = AppBarUiState(
                        actions = listOf(
                            SEARCH,
                            MORE,
                        ),
                    ),
                    componentListUiState = ComponentListUiState(receiver = componentInfoTestData),
                    tabState = tabState,
                    bottomSheetState = ComponentSortInfoUiState.Loading,
                    onBackClick = {},
                    onLaunchAppClick = {},
                    switchTab = {},
                )
            }
        }
        composeTestRule
            .onNodeWithContentDescription(
                composeTestRule.activity.resources.getString(uiR.string.core_ui_search_icon),
            )
            .assertExists()
        composeTestRule
            .onNodeWithContentDescription(
                composeTestRule.activity.resources.getString(uiR.string.core_ui_more_menu),
            )
            .assertExists()
    }

    @Test
    fun search_whenInSearchMode_showSearchSearchTextField() {
        val tabState =
            TabState(
                items = listOf(
                    Info,
                    Receiver,
                ),
                selectedItem = Receiver,
            )
        composeTestRule.setContent {
            BoxWithConstraints {
                AppDetailScreen(
                    appInfoUiState = AppInfoUiState.Success(
                        appInfo = appInfoTestData,
                        iconBasedTheming = null,
                    ),
                    topAppBarUiState = AppBarUiState(
                        actions = listOf(
                            SEARCH,
                            MORE,
                        ),
                        isSearchMode = true,
                    ),
                    componentListUiState = ComponentListUiState(receiver = componentInfoTestData),
                    tabState = tabState,
                    bottomSheetState = ComponentSortInfoUiState.Loading,
                    onBackClick = {},
                    onLaunchAppClick = {},
                    switchTab = {},
                )
            }
        }
        composeTestRule
            .onNodeWithTag("BlockerSearchTextField")
            .assertExists()
    }

    @Test
    fun showAppInfo() {
        composeTestRule.setContent {
            BoxWithConstraints {
                AppDetailScreen(
                    appInfoUiState = AppInfoUiState.Success(
                        appInfo = appInfoTestData,
                        iconBasedTheming = null,
                    ),
                    topAppBarUiState = AppBarUiState(),
                    componentListUiState = ComponentListUiState(),
                    tabState = tabState,
                    bottomSheetState = ComponentSortInfoUiState.Loading,
                    onBackClick = {},
                    onLaunchAppClick = {},
                    switchTab = {},
                )
            }
        }
        composeTestRule.onNodeWithText(appInfoTestData.label).assertExists()
        composeTestRule
            .onNodeWithTag("AppDetailSummaryContent")
            .assertExists()
        composeTestRule.onNodeWithText(ifwRulesText).assertExists()
    }

    @Test
    fun showComponentInfo() {
        val itemCountMap = mapOf(
            Info to 1,
            Receiver to 1,
        )
        val tabState =
            TabState(
                items = listOf(
                    Info,
                    Receiver,
                ),
                selectedItem = Receiver,
                itemCount = itemCountMap,
            )
        composeTestRule.setContent {
            BoxWithConstraints {
                AppDetailScreen(
                    appInfoUiState = AppInfoUiState.Success(
                        appInfo = appInfoTestData,
                        iconBasedTheming = null,
                    ),
                    topAppBarUiState = AppBarUiState(),
                    componentListUiState = ComponentListUiState(receiver = componentInfoTestData),
                    tabState = tabState,
                    bottomSheetState = ComponentSortInfoUiState.Loading,
                    onBackClick = {},
                    onLaunchAppClick = {},
                    switchTab = {},
                )
            }
        }
        composeTestRule.onNodeWithText(receiverText).assertExists().assertIsSelected()
        composeTestRule
            .onNodeWithTag("component:list")
            .assertExists()
        composeTestRule.onNodeWithText(componentInfoTestData[0].name).assertExists()
            .assertHasClickAction()
    }
}
