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

package com.merxury.blocker.feature.sort

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import com.merxury.blocker.core.testing.testing.data.AppSortInfoTestData
import com.merxury.blocker.feature.sort.viewmodel.AppSortInfoUiState
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import com.merxury.blocker.core.ui.R as uiR

class AppSortBottomSheetTest {
    @get:Rule(order = 0)
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private lateinit var loadingDescription: String
    private lateinit var sortOptions: String
    private lateinit var sortBy: String
    private lateinit var name: String
    private lateinit var installDate: String
    private lateinit var lastUpdated: String
    private lateinit var order: String
    private lateinit var ascending: String
    private lateinit var descending: String
    private lateinit var showRunningAppsOnTop: String
    private lateinit var on: String
    private lateinit var off: String

    @Before
    fun setup() {
        composeTestRule.activity.apply {
            loadingDescription = getString(uiR.string.core_ui_loading)
            sortOptions = getString(uiR.string.core_ui_sort_options)
            sortBy = getString(R.string.feature_sort_sort_by)
            name = getString(R.string.feature_sort_name)
            installDate = getString(R.string.feature_sort_install_date)
            lastUpdated = getString(R.string.feature_sort_last_updated)
            order = getString(R.string.feature_sort_order)
            ascending = getString(R.string.feature_sort_ascending)
            descending = getString(R.string.feature_sort_descending)
            showRunningAppsOnTop = getString(R.string.feature_sort_show_running_apps_on_top)
            on = getString(R.string.feature_sort_on)
            off = getString(R.string.feature_sort_on)
        }
    }

    @Test
    fun circularProgressIndicator_whenScreenIsLoading_exists() {
        composeTestRule.setContent {
            AppSortBottomSheet(
                uiState = AppSortInfoUiState.Loading,
            )
        }

        composeTestRule.onNodeWithContentDescription(loadingDescription).assertExists()
    }

    @Test
    fun appSortBottomSheet() {
        composeTestRule.setContent {
            AppSortBottomSheet(
                uiState = AppSortInfoUiState.Success(
                    appSortInfo = AppSortInfoTestData,
                ),
            )
        }

        composeTestRule.onNodeWithText(sortOptions).assertExists().assertHasNoClickAction()
        composeTestRule.onNodeWithText(sortBy).assertExists().assertHasNoClickAction()
        composeTestRule.onNodeWithText(name).assertExists().assertHasClickAction()
        composeTestRule.onNodeWithText(installDate).assertExists().assertHasClickAction()
        composeTestRule.onNodeWithText(lastUpdated).assertExists().assertHasClickAction()
        composeTestRule.onNodeWithText(showRunningAppsOnTop).assertExists().assertHasNoClickAction()
        composeTestRule.onNodeWithText(ascending).assertExists().assertHasClickAction()
        composeTestRule.onNodeWithText(descending).assertExists().assertHasClickAction()
        composeTestRule.onNodeWithText(order).assertExists().assertHasNoClickAction()
        composeTestRule.onNodeWithText(on).assertExists().assertHasClickAction()
        composeTestRule.onNodeWithText(off).assertExists().assertHasClickAction()
    }
}
