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

package com.merxury.blocker.feature.applist

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollToNode
import com.merxury.blocker.core.testing.testing.data.appListTestData
import com.merxury.blocker.core.ui.R
import com.merxury.blocker.core.ui.bottomsheet.AppSortInfoUiState
import com.merxury.blocker.core.ui.data.UiMessage
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import com.merxury.blocker.core.ui.R as uiR

class AppListScreenTest {
    @get:Rule(order = 0)
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private lateinit var errorMessage: UiMessage
    private var processingAppName: String = "blocker"
    private lateinit var descending: String
    private lateinit var error: String
    private lateinit var initializingDatabase: String

    @Before
    fun setup() {
        composeTestRule.activity.apply {
            error = getString(uiR.string.core_ui_error)
            errorMessage = UiMessage(error)
            descending = getString(R.string.core_ui_descending)
            initializingDatabase = getString(R.string.core_ui_initializing_database)
        }
    }

    @Test
    fun circularProgressIndicator_whenScreenIsLoading_exists() {
        composeTestRule.setContent {
            AppListScreen(
                uiState = AppListUiState.Initializing(processingAppName),
                bottomSheetUiState = AppSortInfoUiState.Loading,
                appList = emptyList(),
            )
        }

        composeTestRule
            .onNodeWithContentDescription(processingAppName)
            .assertExists()
        composeTestRule
            .onNodeWithText(initializingDatabase)
            .assertExists()
    }

    @Test
    fun errorIndicator_whenFailToLoadInfo_exists() {
        composeTestRule.setContent {
            AppListScreen(
                uiState = AppListUiState.Error(errorMessage),
                bottomSheetUiState = AppSortInfoUiState.Loading,
                appList = emptyList(),
            )
        }

        composeTestRule.onNodeWithContentDescription(error).assertExists()
        composeTestRule.onNodeWithText(errorMessage.title).assertExists()
    }

    @Test
    fun showAppList() {
        composeTestRule.setContent {
            AppListScreen(
                uiState = AppListUiState.Success,
                bottomSheetUiState = AppSortInfoUiState.Loading,
                appList = appListTestData,
            )
        }

        composeTestRule.onNodeWithText(appListTestData.first().label)
            .assertExists()
            .assertHasClickAction()
    }

    @Test
    fun showFastScrollbar_whenAppListMoreThanOneScreen() {
        composeTestRule.setContent {
            AppListScreen(
                uiState = AppListUiState.Success,
                bottomSheetUiState = AppSortInfoUiState.Loading,
                appList = appListTestData,
            )
        }

        composeTestRule.onNode(hasScrollToNodeAction())
            .performScrollToNode(
                hasText(appListTestData.last().label),
            )
        composeTestRule.onNodeWithTag("appList:scrollbar")
            .assertExists()
    }
}
