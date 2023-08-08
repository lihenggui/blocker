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

package com.merxury.blocker.ui

import androidx.annotation.StringRes
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.merxury.blocker.MainActivity
import dagger.hilt.android.testing.BindValue
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Before
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import kotlin.properties.ReadOnlyProperty
import com.merxury.blocker.R
import org.junit.Test
import com.merxury.blocker.feature.search.R as FeatureSearchR

@HiltAndroidTest
class NavigationTest {

    /**
     * Manages the components' state and is used to perform injection on your test
     */
    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    /**
     * Create a temporary folder used to create a Data Store file. This guarantees that
     * the file is removed in between each test, preventing a crash.
     */
    @BindValue
    @get:Rule(order = 1)
    val tmpFolder: TemporaryFolder = TemporaryFolder.builder().assureDeletion().build()

    /**
     * Use the primary activity to initialize the app normally.
     */
    @get:Rule(order = 3)
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    private fun AndroidComposeTestRule<*, *>.stringResource(@StringRes resId: Int) =
        ReadOnlyProperty<Any?, String> { _, _ -> activity.getString(resId) }

    // The strings used for matching in these tests
    private val appName by composeTestRule.stringResource(R.string.app_name)
    private val apps by composeTestRule.stringResource(R.string.apps)
    private val rules by composeTestRule.stringResource(R.string.rules)
    private val search by composeTestRule.stringResource(R.string.search)
    private val searchHint by composeTestRule.stringResource(FeatureSearchR.string.search_hint)

    @Before
    fun setup() = hiltRule.inject()

    @Test
    fun firstScreen_isApps() {
        composeTestRule.apply {
            // Verify Apps tab is selected
            onNodeWithText(apps).assertIsSelected()
        }
    }

    @Test
    fun topLevelDestinations_showTopBarWithTitle() {
        composeTestRule.apply {
            // Verify that the top bar contains the app name on the first screen.
            onNodeWithText(appName).assertExists()

            // Go to the rules tab, verify that the top bar contains "rules". This means
            // we'll have 2 elements with the text "rules" on screen. One in the top bar, and
            // one in the bottom navigation.
            onNodeWithText(rules).performClick()
            onAllNodesWithText(rules).assertCountEquals(2)

            // Verify that search bar contains search hint text on the search tab.
            onNodeWithText(search).performClick()
            onNodeWithText(searchHint).assertExists()
        }
    }
}