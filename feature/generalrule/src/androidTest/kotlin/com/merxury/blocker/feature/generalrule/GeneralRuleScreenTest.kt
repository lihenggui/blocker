/*
 * Copyright 2024 Blocker
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

package com.merxury.blocker.feature.generalrule

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollToNode
import com.merxury.blocker.core.testing.testing.data.generalRuleListTestData
import com.merxury.blocker.core.ui.R
import com.merxury.blocker.core.ui.data.UiMessage
import com.merxury.blocker.feature.generalrules.GeneralRuleUiState
import com.merxury.blocker.feature.generalrules.GeneralRulesScreen
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class GeneralRuleScreenTest {
    @get:Rule(order = 0)
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private lateinit var errorMessage: UiMessage
    private lateinit var loadingDescription: String
    private lateinit var error: String

    @Before
    fun setup() {
        composeTestRule.activity.apply {
            error = getString(R.string.core_ui_error)
            errorMessage = UiMessage(error)
            loadingDescription = getString(R.string.core_ui_loading)
        }
    }

    @Test
    fun circularProgressIndicator_whenScreenIsLoading_exists() {
        composeTestRule.setContent {
            GeneralRulesScreen(
                uiState = GeneralRuleUiState.Loading,
                navigateToRuleDetail = {},
            )
        }

        composeTestRule.onNodeWithContentDescription(loadingDescription).assertExists()
    }

    @Test
    fun errorIndicator_whenFailToLoadInfo_exists() {
        composeTestRule.setContent {
            GeneralRulesScreen(
                uiState = GeneralRuleUiState.Error(errorMessage),
                navigateToRuleDetail = {},
            )
        }

        composeTestRule.onNodeWithContentDescription(error).assertExists()
        composeTestRule.onNodeWithText(errorMessage.title).assertExists()
    }

    @Test
    fun showRulesList() {
        composeTestRule.setContent {
            val matchedRules = generalRuleListTestData.filter { it.matchedAppCount > 0 }
            val unmatchedRules = generalRuleListTestData.filter { it.matchedAppCount == 0 }
            GeneralRulesScreen(
                uiState = GeneralRuleUiState.Success(
                    matchedRules = matchedRules,
                    unmatchedRules = unmatchedRules,
                ),
                navigateToRuleDetail = {},
            )
        }

        composeTestRule.onNodeWithText(generalRuleListTestData.first().name)
            .assertExists()
            .assertHasClickAction()
    }

    @Test
    fun showDraggableScrollbar_whenRulesListMoreThanOneScreen() {
        composeTestRule.setContent {
            val matchedRules = generalRuleListTestData.filter { it.matchedAppCount > 0 }
            val unmatchedRules = generalRuleListTestData.filter { it.matchedAppCount == 0 }
            GeneralRulesScreen(
                uiState = GeneralRuleUiState.Success(
                    matchedRules = matchedRules,
                    unmatchedRules = unmatchedRules,
                ),
                navigateToRuleDetail = {},
            )
        }

        composeTestRule.onNode(hasScrollToNodeAction())
            .performScrollToNode(
                hasText(generalRuleListTestData.last().name),
            )
        composeTestRule.onNodeWithTag("rule:scrollbar")
            .assertExists()
    }
}
