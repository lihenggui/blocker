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
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import com.merxury.blocker.core.testing.testing.data.generalRuleTestData
import com.merxury.blocker.core.testing.testing.data.ruleMatchedAppListTestData
import com.merxury.blocker.core.ui.R
import com.merxury.blocker.core.ui.TabState
import com.merxury.blocker.core.ui.rule.RuleDetailTabs
import com.merxury.blocker.core.ui.rule.RuleDetailTabs.Applicable
import com.merxury.blocker.core.ui.rule.RuleDetailTabs.Description
import com.merxury.blocker.core.ui.rule.RuleMatchedAppListUiState
import com.merxury.blocker.core.ui.state.toolbar.AppBarAction.MORE
import com.merxury.blocker.core.ui.state.toolbar.AppBarUiState
import com.merxury.blocker.feature.ruledetail.model.RuleInfoUiState
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import com.merxury.blocker.core.ui.R as uiR

class RuleDetailScreenTest {
    @get:Rule(order = 0)
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private var tabState: TabState<RuleDetailTabs> = TabState(
        items = listOf(
            Applicable,
            Description,
        ),
        selectedItem = Applicable,
    )
    private lateinit var loadingDescription: String
    private lateinit var moreMenuDescription: String
    private lateinit var applicableApplication: String
    private lateinit var expandMore: String
    private lateinit var description: String

    @Before
    fun setup() {
        composeTestRule.activity.apply {
            loadingDescription = getString(R.string.core_ui_loading)
            moreMenuDescription = getString(uiR.string.core_ui_more_menu)
            applicableApplication = getString(uiR.string.core_ui_applicable_application)
            expandMore = getString(uiR.string.core_ui_expand_more)
            description = getString(uiR.string.core_ui_description)
        }
    }

    @Test
    fun circularProgressIndicator_whenScreenIsLoading_exists() {
        composeTestRule.setContent {
            BoxWithConstraints {
                RuleDetailScreen(
                    ruleMatchedAppListUiState = RuleMatchedAppListUiState.Loading,
                    ruleInfoUiState = RuleInfoUiState.Loading,
                    onBackClick = {},
                    tabState = tabState,
                    switchTab = {},
                )
            }
        }

        composeTestRule.onNodeWithContentDescription(loadingDescription).assertExists()
    }

    @Test
    fun showRuleApplicableApps_showMoreIconExpandMoreIcon() {
        composeTestRule.setContent {
            BoxWithConstraints {
                RuleDetailScreen(
                    ruleMatchedAppListUiState = RuleMatchedAppListUiState.Success(
                        ruleMatchedAppListTestData,
                    ),
                    ruleInfoUiState = RuleInfoUiState.Success(
                        ruleInfo = generalRuleTestData,
                        ruleIcon = null,
                    ),
                    onBackClick = {},
                    tabState = tabState,
                    switchTab = {},
                    appBarUiState = AppBarUiState(actions = listOf(MORE)),
                )
            }
        }

        // Check more icon is shown, and show a more icon in the app item
        composeTestRule
            .onAllNodesWithContentDescription(moreMenuDescription)
            .assertCountEquals(2)
        // Check showing rules info
        composeTestRule.onNodeWithText(generalRuleTestData.name)
            .assertExists()
        // Check tab is selected
        composeTestRule.onNodeWithText(applicableApplication)
            .assertExists()
            .assertIsSelected()
        // Check showing rules matched app list
        composeTestRule.onNodeWithTag("search:ruleMatchedAppList")
            .assertExists()
        composeTestRule.onNodeWithText(ruleMatchedAppListTestData.first().app.label)
            .assertExists()
        composeTestRule.onNodeWithContentDescription(expandMore).assertExists()
    }

    @Test
    fun showRuleDetailDescription_hideMoreIcon() {
        tabState = TabState(
            items = listOf(
                Applicable,
                Description,
            ),
            selectedItem = Description,
        )
        composeTestRule.setContent {
            BoxWithConstraints {
                RuleDetailScreen(
                    ruleMatchedAppListUiState = RuleMatchedAppListUiState.Loading,
                    ruleInfoUiState = RuleInfoUiState.Success(
                        ruleInfo = generalRuleTestData,
                        ruleIcon = null,
                    ),
                    onBackClick = {},
                    tabState = tabState,
                    switchTab = {},
                    appBarUiState = AppBarUiState(actions = listOf()),
                )
            }
        }

        // Check more icon is hidden
        composeTestRule
            .onNodeWithContentDescription(moreMenuDescription)
            .assertDoesNotExist()
        // Check showing rules detail description
        composeTestRule.onNodeWithText(generalRuleTestData.name)
            .assertExists()
        composeTestRule.onNodeWithText(description)
            .assertExists()
            .assertIsSelected()
        composeTestRule.onNodeWithTag("ruleDetail:description")
            .assertExists()
    }
}
