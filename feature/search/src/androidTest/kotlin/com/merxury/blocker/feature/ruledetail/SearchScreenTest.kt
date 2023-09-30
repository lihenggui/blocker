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

package com.merxury.blocker.feature.ruledetail

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.text.input.TextFieldValue
import com.merxury.blocker.core.testing.testing.data.appInfoTestData
import com.merxury.blocker.core.testing.testing.data.filteredComponentTestData
import com.merxury.blocker.core.testing.testing.data.generalRuleListTestData
import com.merxury.blocker.core.ui.SearchScreenTabs
import com.merxury.blocker.core.ui.SearchScreenTabs.App
import com.merxury.blocker.core.ui.SearchScreenTabs.Component
import com.merxury.blocker.core.ui.TabState
import com.merxury.blocker.feature.search.AppTabUiState
import com.merxury.blocker.feature.search.ComponentTabUiState
import com.merxury.blocker.feature.search.LocalSearchUiState.Idle
import com.merxury.blocker.feature.search.LocalSearchUiState.Loading
import com.merxury.blocker.feature.search.LocalSearchUiState.Success
import com.merxury.blocker.feature.search.R.string
import com.merxury.blocker.feature.search.RuleTabUiState
import com.merxury.blocker.feature.search.SearchScreen
import com.merxury.blocker.feature.search.SearchUiState
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import com.merxury.blocker.core.designsystem.R as designSystemR
import com.merxury.blocker.core.ui.R as uiR

class SearchScreenTest {
    @get:Rule(order = 0)
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private var tabState: TabState<SearchScreenTabs> = TabState(
        items = listOf(
            App(),
            Component(),
            SearchScreenTabs.Rule(),
        ),
        selectedItem = App(),
    )
    private var searchKeyword: String = "blocker"
    private lateinit var noSearchResult: String
    private lateinit var searching: String
    private lateinit var searchIconDescription: String
    private lateinit var clearIconDescription: String
    private lateinit var close: String
    private lateinit var selectAll: String
    private lateinit var blockSelected: String
    private lateinit var enableSelected: String
    private lateinit var checkIconDescription: String

    @Before
    fun setup() {
        composeTestRule.activity.apply {
            noSearchResult = getString(string.feature_search_no_search_result)
            searching = getString(string.feature_search_searching)
            searchIconDescription = getString(designSystemR.string.core_designsystem_search_icon)
            clearIconDescription = getString(designSystemR.string.core_designsystem_clear_icon)
            close = getString(uiR.string.core_ui_close)
            selectAll = getString(uiR.string.core_ui_select_all)
            blockSelected = getString(uiR.string.core_ui_block_selected)
            enableSelected = getString(uiR.string.core_ui_enable_selected)
            checkIconDescription = getString(string.feature_search_check_icon)
        }
    }

    @Test
    fun emptyScreen_whenFirstEnter() {
        composeTestRule.setContent {
            SearchScreen(
                tabState = tabState,
                localSearchUiState = Idle,
                searchUiState = SearchUiState(),
            )
        }

        composeTestRule.onNodeWithText(noSearchResult).assertExists()
        composeTestRule.onNodeWithTag("blockerTopAppBar")
            .assertExists()
    }

    @Test
    fun loadingScreen_whenSearching() {
        composeTestRule.setContent {
            SearchScreen(
                tabState = tabState,
                localSearchUiState = Loading,
                searchUiState = SearchUiState(),
            )
        }

        composeTestRule.onNodeWithContentDescription(searching).assertExists()
    }

    @Test
    fun showClearIcon_whenHaveInput() {
        composeTestRule.setContent {
            SearchScreen(
                tabState = tabState,
                localSearchUiState = Idle,
                searchUiState = SearchUiState(
                    keyword = TextFieldValue(searchKeyword),
                ),
            )
        }
        composeTestRule.onNodeWithText(searchKeyword)
            .requestFocus()
        composeTestRule.onNodeWithContentDescription(clearIconDescription)
            .assertExists()
    }

    @Test
    fun showSearchResult_appTab() {
        tabState = TabState(
            items = listOf(
                App(1),
                Component(),
                SearchScreenTabs.Rule(),
            ),
            selectedItem = App(),
        )

        composeTestRule.setContent {
            SearchScreen(
                tabState = tabState,
                localSearchUiState = Success(
                    searchKeyword = listOf(searchKeyword),
                    appTabUiState = AppTabUiState(listOf(appInfoTestData)),
                    componentTabUiState = ComponentTabUiState(filteredComponentTestData),
                    ruleTabUiState = RuleTabUiState(generalRuleListTestData),
                ),
                searchUiState = SearchUiState(
                    keyword = TextFieldValue(searchKeyword),
                ),
                appList = listOf(appInfoTestData),
            )
        }

        composeTestRule.onNodeWithText(searchKeyword).assertExists()
        composeTestRule.onNodeWithText(
            composeTestRule.activity.resources.getString(tabState.selectedItem.title, 1),
        ).assertExists().assertIsSelected()
        composeTestRule.onNodeWithText(appInfoTestData.label)
            .assertExists()
            .assertHasClickAction()
    }

    @Test
    fun showSearchResult_componentTab() {
        tabState = TabState(
            items = listOf(
                App(1),
                Component(1),
                SearchScreenTabs.Rule(5),
            ),
            selectedItem = Component(1),
        )

        composeTestRule.setContent {
            SearchScreen(
                tabState = tabState,
                localSearchUiState = Success(
                    searchKeyword = listOf(searchKeyword),
                    appTabUiState = AppTabUiState(listOf(appInfoTestData)),
                    componentTabUiState = ComponentTabUiState(filteredComponentTestData),
                    ruleTabUiState = RuleTabUiState(generalRuleListTestData),
                ),
                searchUiState = SearchUiState(
                    keyword = TextFieldValue(searchKeyword),
                ),
                appList = listOf(appInfoTestData),
            )
        }

        composeTestRule.onNodeWithText(searchKeyword).assertExists()
        composeTestRule.onNodeWithText(
            composeTestRule.activity.resources.getString(tabState.selectedItem.title, 1),
        ).assertExists().assertIsSelected()
        composeTestRule.onNodeWithText(filteredComponentTestData.first().app.label)
            .assertExists()
            .assertHasClickAction()
    }

    @Test
    fun showSearchResult_ruleTab() {
        tabState = TabState(
            items = listOf(
                App(1),
                Component(1),
                SearchScreenTabs.Rule(5),
            ),
            selectedItem = SearchScreenTabs.Rule(5),
        )

        composeTestRule.setContent {
            SearchScreen(
                tabState = tabState,
                localSearchUiState = Success(
                    searchKeyword = listOf(searchKeyword),
                    appTabUiState = AppTabUiState(listOf(appInfoTestData)),
                    componentTabUiState = ComponentTabUiState(filteredComponentTestData),
                    ruleTabUiState = RuleTabUiState(generalRuleListTestData),
                ),
                searchUiState = SearchUiState(
                    keyword = TextFieldValue(searchKeyword),
                ),
                appList = listOf(appInfoTestData),
            )
        }

        composeTestRule.onNodeWithText(searchKeyword).assertExists()
        composeTestRule.onNodeWithText(
            composeTestRule.activity.resources.getString(tabState.selectedItem.title, 5),
        ).assertExists().assertIsSelected()
        composeTestRule.onNodeWithText(generalRuleListTestData.first().name)
            .assertExists()
            .assertHasClickAction()
    }

    @Test
    fun showIcons_whenInSelectMode() {
        tabState = TabState(
            items = listOf(
                App(1),
                Component(1),
                SearchScreenTabs.Rule(5),
            ),
            selectedItem = Component(1),
        )

        composeTestRule.setContent {
            SearchScreen(
                tabState = tabState,
                localSearchUiState = Success(
                    searchKeyword = listOf(searchKeyword),
                    appTabUiState = AppTabUiState(listOf(appInfoTestData)),
                    componentTabUiState = ComponentTabUiState(filteredComponentTestData),
                    ruleTabUiState = RuleTabUiState(generalRuleListTestData),
                ),
                searchUiState = SearchUiState(
                    isSelectedMode = true,
                    selectedAppList = filteredComponentTestData,
                ),
                appList = listOf(appInfoTestData),
            )
        }

        composeTestRule.onNodeWithContentDescription(close).assertExists().assertHasClickAction()
        composeTestRule.onNodeWithContentDescription(selectAll).assertExists()
            .assertHasClickAction()
        composeTestRule.onNodeWithContentDescription(blockSelected).assertExists()
            .assertHasClickAction()
        composeTestRule.onNodeWithContentDescription(enableSelected).assertExists()
            .assertHasClickAction()
        composeTestRule.onNodeWithContentDescription(checkIconDescription).assertExists()
        composeTestRule.onNodeWithContentDescription(searchIconDescription).assertDoesNotExist()
        composeTestRule.onNodeWithContentDescription(clearIconDescription).assertDoesNotExist()
    }
}
