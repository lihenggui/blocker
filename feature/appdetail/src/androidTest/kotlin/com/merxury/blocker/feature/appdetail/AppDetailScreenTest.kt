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
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import com.merxury.blocker.core.testing.testing.data.appInfoTestData
import com.merxury.blocker.core.testing.testing.data.receiverTestData
import com.merxury.blocker.core.ui.AppDetailTabs
import com.merxury.blocker.core.ui.AppDetailTabs.Activity
import com.merxury.blocker.core.ui.AppDetailTabs.Info
import com.merxury.blocker.core.ui.AppDetailTabs.Provider
import com.merxury.blocker.core.ui.AppDetailTabs.Receiver
import com.merxury.blocker.core.ui.AppDetailTabs.Service
import com.merxury.blocker.core.ui.TabState
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
    private var tabState: TabState<AppDetailTabs> = TabState(
        items = listOf(
            Info,
            Receiver,
            Service,
            Activity,
            Provider,
        ),
        selectedItem = Info,
    )
    private lateinit var receiverText: String
    private lateinit var searchIconDescription: String
    private lateinit var moreMenuDescription: String
    private lateinit var error: String
    private lateinit var loadingDescription: String
    private lateinit var close: String
    private lateinit var selectAll: String
    private lateinit var blockSelected: String
    private lateinit var enableSelected: String

    @Before
    fun setup() {
        composeTestRule.activity.apply {
            error = getString(uiR.string.core_ui_error)
            ifwRulesText = getString(R.string.feature_appdetail_ifw_rules)
            errorMessage = UiMessage(error)
            receiverText = getString(uiR.string.core_ui_receiver_with_count, 1)
            searchIconDescription = getString(uiR.string.core_ui_search_icon)
            moreMenuDescription = getString(uiR.string.core_ui_more_menu)
            loadingDescription = getString(uiR.string.core_ui_loading)
            close = getString(uiR.string.core_ui_close)
            selectAll = getString(uiR.string.core_ui_select_all)
            blockSelected = getString(uiR.string.core_ui_block_selected)
            enableSelected = getString(uiR.string.core_ui_enable_selected)
        }
    }

    @Test
    fun circularProgressIndicator_whenScreenIsLoading_exists() {
        composeTestRule.setContent {
            AppDetailScreen(
                appInfoUiState = AppInfoUiState.Loading,
                topAppBarUiState = AppBarUiState(),
                componentListUiState = ComponentListUiState(),
                tabState = tabState,
            )
        }

        composeTestRule
            .onNodeWithContentDescription(loadingDescription)
            .assertExists()
    }

    @Test
    fun errorIndicator_whenFailToLoadInfo_exists() {
        composeTestRule.setContent {
            AppDetailScreen(
                appInfoUiState = AppInfoUiState.Error(errorMessage),
                topAppBarUiState = AppBarUiState(),
                componentListUiState = ComponentListUiState(),
                tabState = tabState,
            )
        }

        composeTestRule.onNodeWithContentDescription(error).assertExists()
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
            AppDetailScreen(
                appInfoUiState = AppInfoUiState.Success(
                    appInfo = appInfoTestData,
                ),
                topAppBarUiState = AppBarUiState(
                    actions = listOf(
                        SEARCH,
                        MORE,
                    ),
                ),
                componentListUiState = ComponentListUiState(receiver = receiverTestData),
                tabState = tabState,
            )
        }
        composeTestRule
            .onNodeWithContentDescription(searchIconDescription).assertExists()
        composeTestRule
            .onNodeWithContentDescription(moreMenuDescription).assertExists()
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
            AppDetailScreen(
                appInfoUiState = AppInfoUiState.Success(
                    appInfo = appInfoTestData,
                ),
                topAppBarUiState = AppBarUiState(
                    actions = listOf(
                        SEARCH,
                        MORE,
                    ),
                    isSearchMode = true,
                ),
                componentListUiState = ComponentListUiState(receiver = receiverTestData),
                tabState = tabState,
            )
        }
        composeTestRule
            .onNodeWithTag("BlockerSearchTextField")
            .assertExists()
    }

    @Test
    fun showAppInfo() {
        composeTestRule.setContent {
            AppDetailScreen(
                appInfoUiState = AppInfoUiState.Success(
                    appInfo = appInfoTestData,
                ),
                topAppBarUiState = AppBarUiState(),
                componentListUiState = ComponentListUiState(),
                tabState = tabState,
            )
        }
        composeTestRule.onNodeWithText(appInfoTestData.label).assertExists()
        composeTestRule
            .onNodeWithTag("appDetail:summaryContent")
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
            AppDetailScreen(
                appInfoUiState = AppInfoUiState.Success(
                    appInfo = appInfoTestData,
                ),
                topAppBarUiState = AppBarUiState(),
                componentListUiState = ComponentListUiState(receiver = receiverTestData),
                tabState = tabState,
            )
        }
        composeTestRule.onNodeWithText(receiverText).assertExists().assertIsSelected()
        composeTestRule
            .onNodeWithTag("component:list")
            .assertExists()
        composeTestRule.onNodeWithText(receiverTestData[0].name).assertExists()
            .assertHasClickAction()
    }

    @Test
    fun showIcons_whenInSelectMode() {
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
            AppDetailScreen(
                appInfoUiState = AppInfoUiState.Success(
                    appInfo = appInfoTestData,
                ),
                topAppBarUiState = AppBarUiState(
                    isSelectedMode = true,
                ),
                componentListUiState = ComponentListUiState(receiver = receiverTestData),
                tabState = tabState,
            )
        }
        composeTestRule.onNodeWithContentDescription(close).assertExists().assertHasClickAction()
        composeTestRule.onNodeWithContentDescription(selectAll).assertExists()
            .assertHasClickAction()
        composeTestRule.onNodeWithContentDescription(blockSelected).assertExists()
            .assertHasClickAction()
        composeTestRule.onNodeWithContentDescription(enableSelected).assertExists()
            .assertHasClickAction()
        composeTestRule
            .onNodeWithContentDescription(searchIconDescription).assertDoesNotExist()
        composeTestRule
            .onNodeWithContentDescription(moreMenuDescription).assertDoesNotExist()
    }

    @Test
    fun showIcons_whenInSearchModeThenSelectMode() {
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
            AppDetailScreen(
                appInfoUiState = AppInfoUiState.Success(
                    appInfo = appInfoTestData,
                ),
                topAppBarUiState = AppBarUiState(
                    isSearchMode = true,
                    isSelectedMode = true,
                    actions = listOf(
                        SEARCH,
                        MORE,
                    ),
                ),
                componentListUiState = ComponentListUiState(receiver = receiverTestData),
                tabState = tabState,
            )
        }
        composeTestRule.onNodeWithContentDescription(close).assertExists().assertHasClickAction()
        composeTestRule.onNodeWithContentDescription(selectAll).assertExists()
            .assertHasClickAction()
        composeTestRule.onNodeWithContentDescription(blockSelected).assertExists()
            .assertHasClickAction()
        composeTestRule.onNodeWithContentDescription(enableSelected).assertExists()
            .assertHasClickAction()
        composeTestRule
            .onNodeWithTag("BlockerSearchTextField")
            .assertDoesNotExist()
    }
}
