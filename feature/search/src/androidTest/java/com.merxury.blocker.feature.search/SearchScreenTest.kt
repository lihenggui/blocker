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

package com.merxury.blocker.feature.search

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.text.input.TextFieldValue
import com.merxury.blocker.core.testing.testing.data.appInfoTestData
import com.merxury.blocker.core.testing.testing.data.filteredComponentTestData
import com.merxury.blocker.core.testing.testing.data.generalRuleListTestData
import com.merxury.blocker.core.ui.TabState
import com.merxury.blocker.core.ui.data.UiMessage
import com.merxury.blocker.feature.search.R.string
import com.merxury.blocker.feature.search.model.AppTabUiState
import com.merxury.blocker.feature.search.model.ComponentTabUiState
import com.merxury.blocker.feature.search.model.LocalSearchUiState
import com.merxury.blocker.feature.search.model.RuleTabUiState
import com.merxury.blocker.feature.search.model.SearchUiState
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class SearchScreenTest {
    @get:Rule(order = 0)
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private lateinit var tabState: TabState<SearchScreenTabs>
    private lateinit var errorMessage: UiMessage
    private lateinit var searchKeyword: String

    @Before
    fun setup() {
        composeTestRule.activity.apply {
            tabState = TabState(
                items = listOf(
                    SearchScreenTabs.App(),
                    SearchScreenTabs.Component(),
                    SearchScreenTabs.Rule(),
                ),
                selectedItem = SearchScreenTabs.App(),
            )
            errorMessage = UiMessage("Cannot search out.")
            searchKeyword = "blocker"
        }
    }

    @Test
    fun emptyScreen_whenFirstEnter() {
        composeTestRule.setContent {
            BoxWithConstraints {
                SearchScreen(
                    tabState = tabState,
                    localSearchUiState = LocalSearchUiState.Idle,
                    searchUiState = SearchUiState(),
                    switchTab = {},
                    onSearchTextChanged = {},
                    onClearClick = {},
                    onSelectAll = {},
                    onBlockAll = {},
                    onEnableAll = {},
                    switchSelectedMode = {},
                    onSelect = {},
                    onDeselect = {},
                )
            }
        }

        composeTestRule.onNodeWithText(
            composeTestRule.activity.resources.getString(string.feature_search_no_search_result),
        ).assertExists()
        composeTestRule.onNodeWithTag("blockerTopAppBar")
            .assertExists()
    }

    @Test
    fun loadingScreen_whenSearching() {
        composeTestRule.setContent {
            BoxWithConstraints {
                SearchScreen(
                    tabState = tabState,
                    localSearchUiState = LocalSearchUiState.Loading,
                    searchUiState = SearchUiState(),
                    switchTab = {},
                    onSearchTextChanged = {},
                    onClearClick = {},
                    onSelectAll = {},
                    onBlockAll = {},
                    onEnableAll = {},
                    switchSelectedMode = {},
                    onSelect = {},
                    onDeselect = {},
                )
            }
        }

        composeTestRule.onNodeWithContentDescription(
            composeTestRule.activity.resources.getString(string.feature_search_searching),
        ).assertExists()
    }

    @Test
    fun errorScreen() {
        composeTestRule.setContent {
            BoxWithConstraints {
                SearchScreen(
                    tabState = tabState,
                    localSearchUiState = LocalSearchUiState.Error(errorMessage),
                    searchUiState = SearchUiState(),
                    switchTab = {},
                    onSearchTextChanged = {},
                    onClearClick = {},
                    onSelectAll = {},
                    onBlockAll = {},
                    onEnableAll = {},
                    switchSelectedMode = {},
                    onSelect = {},
                    onDeselect = {},
                )
            }
        }

        composeTestRule.onNodeWithText(errorMessage.title).assertExists()
    }

    @Test
    fun showSearchResult() {
        composeTestRule.setContent {
            BoxWithConstraints {
                SearchScreen(
                    tabState = tabState,
                    localSearchUiState = LocalSearchUiState.Success(
                        searchKeyword = listOf(searchKeyword),
                        appTabUiState = AppTabUiState(listOf(appInfoTestData)),
                        componentTabUiState = ComponentTabUiState(filteredComponentTestData),
                        ruleTabUiState = RuleTabUiState(generalRuleListTestData),
                    ),
                    searchUiState = SearchUiState(
                        keyword = TextFieldValue(searchKeyword),
                    ),
                    switchTab = {},
                    onSearchTextChanged = {},
                    onClearClick = {},
                    onSelectAll = {},
                    onBlockAll = {},
                    onEnableAll = {},
                    switchSelectedMode = {},
                    onSelect = {},
                    onDeselect = {},
                    appList = listOf(appInfoTestData),
                )
            }
        }

        composeTestRule.onNodeWithText(searchKeyword).assertExists()
        composeTestRule.onNodeWithText(appInfoTestData.label).assertExists()
    }
}
