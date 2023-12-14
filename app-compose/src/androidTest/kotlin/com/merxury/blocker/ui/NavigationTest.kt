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

import com.merxury.blocker.core.ui.R as UiR
import com.merxury.blocker.feature.applist.R as FeatureApplistR
import com.merxury.blocker.feature.search.R as FeatureSearchR
import androidx.annotation.StringRes
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.test.espresso.Espresso
import androidx.test.espresso.NoActivityResumedException
import com.merxury.blocker.MainActivity
import com.merxury.blocker.R
import com.merxury.blocker.core.data.respository.generalrule.GeneralRuleRepository
import com.merxury.blocker.core.model.data.GeneralRule
import dagger.hilt.android.testing.BindValue
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import javax.inject.Inject
import kotlin.properties.ReadOnlyProperty

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

    @Inject
    lateinit var rulesRepository: GeneralRuleRepository

    private fun AndroidComposeTestRule<*, *>.stringResource(@StringRes resId: Int) =
        ReadOnlyProperty<Any?, String> { _, _ -> activity.getString(resId) }

    // The strings used for matching in these tests
    private val appName by composeTestRule.stringResource(R.string.feature_applist_app_name)
    private val apps by composeTestRule.stringResource(R.string.apps)
    private val rules by composeTestRule.stringResource(R.string.rules)
    private val search by composeTestRule.stringResource(R.string.search)
    private val searchHint by composeTestRule.stringResource(FeatureSearchR.string.feature_search_search_hint)
    private val moreMenu by composeTestRule.stringResource(UiR.string.core_ui_more_menu)
    private val supportAndFeedback by composeTestRule.stringResource(FeatureApplistR.string.feature_applist_support_and_feedback)
    private val sortMenu by composeTestRule.stringResource(FeatureApplistR.string.feature_applist_sort_menu)
    private val sortOptions by composeTestRule.stringResource(UiR.string.core_ui_sort_options)

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

    /*
     * more icon and sort icon only shows on the Apps tab
     */
    @Test
    fun topLevelDestinations_showMoreAndSortIcon() {
        composeTestRule.apply {
            onNodeWithContentDescription(moreMenu).assertExists()
            onNodeWithContentDescription(sortMenu).assertExists()

            onNodeWithText(rules).performClick()
            onNodeWithContentDescription(moreMenu).assertDoesNotExist()
            onNodeWithContentDescription(sortMenu).assertDoesNotExist()

            onNodeWithText(search).performClick()
            onNodeWithContentDescription(moreMenu).assertDoesNotExist()
            onNodeWithContentDescription(sortMenu).assertDoesNotExist()
        }
    }

    @Test
    fun whenMoreIconIsClicked_moreDialogIsShown() {
        composeTestRule.apply {
            onNodeWithContentDescription(moreMenu).performClick()

            // Check that one of the more menu item is actually displayed.
            onNodeWithText(supportAndFeedback).assertExists()
        }
    }

    @Test
    fun whenMoreDialogDismissed_previousScreenIsDisplayed() {
        composeTestRule.apply {
            // Open the more menu dialog, then close it.
            onNodeWithContentDescription(moreMenu).performClick()
            onNodeWithText(appName).performClick()

            // Check that the apps screen is still visible and selected.
            onNode(
                hasText(apps) and
                    hasAnyAncestor(
                        hasTestTag("BlockerBottomBar") or hasTestTag("BlockerNavRail"),
                    ),
            ).assertIsSelected()
        }
    }

    @Test
    fun whenSortIconIsClicked_sortBottomSheetIsShown() {
        composeTestRule.apply {
            onNodeWithContentDescription(sortMenu).performClick()

            // Check that one of the sort menu item is actually displayed.
            onNodeWithText(sortOptions).assertExists()
        }
    }

    @Test
    fun whenSortBottomSheetDismissed_previousScreenIsDisplayed() {
        composeTestRule.apply {
            // Open the more menu dialog, then close it.
            onNodeWithContentDescription(sortMenu).performClick()
            onNodeWithText(appName).performClick()

            // Check that the apps screen is still visible and selected.
            onNode(
                hasText(apps) and
                    hasAnyAncestor(
                        hasTestTag("BlockerBottomBar") or hasTestTag("BlockerNavRail"),
                    ),
            ).assertIsSelected()
        }
    }

    /*
     * There should always be at most one instance of a top-level destination at the same time.
     */
    @Test(expected = NoActivityResumedException::class)
    fun homeDestination_back_quitsApp() {
        composeTestRule.apply {
            // GIVEN the user navigates to the rules destination
            onNodeWithText(rules).performClick()
            // and then navigates to the apps destination
            onNodeWithText(apps).performClick()
            // WHEN the user uses the system button/gesture to go back
            Espresso.pressBack()
            // THEN the app quits
        }
    }

    /*
     * When pressing back from any top level destination except "apps", the app navigates back
     * to the "apps" destination, no matter which destinations you visited in between.
     */
    @Test
    fun navigationBar_backFromAnyDestination_returnsToApps() {
        composeTestRule.apply {
            // GIVEN the user navigated to the rules destination
            onNodeWithText(rules).performClick()
            // TODO: Add another destination here to increase test coverage, see b/226357686.
            // WHEN the user uses the system button/gesture to go back,
            Espresso.pressBack()
            // THEN the app shows the Apps destination
            onNodeWithText(apps).assertExists()
        }
    }

    @Test
    fun navigationBar_multipleBackStackRules() = runTest {
        composeTestRule.apply {
            // GIVEN the user navigated to the rules destination
            onNodeWithText(rules).performClick()

            // Select the last rule
            val rule =
                rulesRepository.getGeneralRules().first().sortedBy(GeneralRule::name).last().name
            onNodeWithTag("rule:list").performScrollToNode(hasText(rule))
            onNodeWithText(rule).performClick()

            // Switch tab
            onNodeWithText(apps).performClick()

            // Come back to rules
            onNodeWithText(rules).performClick()

            // Verify we're not in the list of rules, keep the last rule selected
            onNodeWithTag("rule:list").assertDoesNotExist()
        }
    }
}
