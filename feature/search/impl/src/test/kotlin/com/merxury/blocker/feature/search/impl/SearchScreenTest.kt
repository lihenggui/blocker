/*
 * Copyright 2026 Blocker
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

package com.merxury.blocker.feature.search.impl

import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import com.google.common.truth.Truth.assertThat
import com.merxury.blocker.core.designsystem.theme.BlockerTheme
import com.merxury.blocker.core.model.component.AppKey
import com.merxury.blocker.core.model.component.ComponentId
import com.merxury.blocker.core.model.component.UserId
import com.merxury.blocker.core.model.system.AppAction
import com.merxury.blocker.feature.search.impl.component.SearchActions
import dagger.hilt.android.testing.HiltTestApplication
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import com.merxury.blocker.core.ui.R.plurals as uiPlurals
import com.merxury.blocker.core.ui.R.string as uiString
import com.merxury.blocker.feature.search.api.R.plurals as searchPlurals
import com.merxury.blocker.feature.search.api.R.string as string

/** What the search screen shows and what its results ask for. */
@RunWith(RobolectricTestRunner::class)
// Tall enough to show every result without scrolling.
@Config(application = HiltTestApplication::class, qualifiers = "w411dp-h1600dp")
class SearchScreenTest {
    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private val asked = mutableListOf<String>()
    private val appActions = mutableListOf<Pair<AppKey, AppAction>>()
    private val toggled = mutableListOf<ComponentId>()
    private val selectedUsers = mutableListOf<UserId>()
    private var state by mutableStateOf(SearchStates.idle())
    private var query by mutableStateOf("")

    private val actions = SearchActions(
        onOpenApp = { asked += "openApp ${it.key}" },
        onAppAction = { app, action -> appActions += app to action },
        onOpenGroup = { asked += "openGroup ${it.app}" },
        onOpenComponent = { asked += "openComponent ${it.id.simpleName}" },
        onSelectionModeChange = { asked += "selectionMode $it" },
        onToggleSelected = { toggled += it },
        onToggleSelectedApp = { asked += "toggleApp $it" },
        onGrantAccess = { asked += "grantAccess" },
        onOpenRule = { asked += "openRule $it" },
    )

    private fun show(uiState: SearchUiState) {
        state = uiState
        composeTestRule.setContent {
            BlockerTheme {
                SearchScreen(
                    uiState = state,
                    query = query,
                    onQueryChange = { query = it },
                    actions = actions,
                    onSelectUser = { selectedUsers += it },
                    onBlockSelection = { asked += "block" },
                    onEnableSelection = { asked += "enable" },
                )
            }
        }
    }

    private fun text(id: Int, vararg args: Any) = composeTestRule.activity.getString(id, *args)

    private fun plural(id: Int, count: Int) = composeTestRule.activity.resources.getQuantityString(id, count, count)

    @Test
    fun givenNothingSearched_whenShown_thenIdleAndTypingReachesTheSearch() {
        show(SearchStates.idle())
        composeTestRule.onNodeWithText(text(string.feature_search_api_no_search_result)).assertIsDisplayed()

        composeTestRule.onNodeWithTag("BlockerSearchTextField").performTextInput("push")

        assertThat(query).isEqualTo("push")
    }

    @Test
    fun givenApps_whenAnAppIsTapped_thenItOpensAndTheKeyboardIsHidden() {
        show(SearchStates.shown(SearchTab.APPS))
        composeTestRule.onNodeWithTag("BlockerSearchTextField").assertIsFocused()

        composeTestRule.onNodeWithText("Blocker Test").performClick()

        assertThat(asked).containsExactly("openApp ${AppKey("com.test.blocker", OWNER)}")
        composeTestRule.onNodeWithTag("BlockerSearchTextField").assertIsNotFocused()
    }

    @Test
    fun givenWorkApps_whenUninstallChosen_thenAskedForThatProfileAndActedOnTheWorkApp() {
        show(SearchStates.workProfile())

        composeTestRule.onNodeWithText("Blocker Test").performTouchInput { longClick() }
        composeTestRule.onNodeWithText(text(uiString.core_ui_uninstall)).performClick()
        composeTestRule.onNodeWithText(text(uiString.core_ui_uninstall_for_profile_confirm, "Work")).assertIsDisplayed()
        composeTestRule.onNodeWithText(text(android.R.string.ok)).performClick()

        assertThat(appActions).containsExactly(AppKey("com.test.blocker", WORK) to AppAction.UNINSTALL)
    }

    @Test
    fun givenApps_whenForceStopChosen_thenItRunsWithoutAsking() {
        show(SearchStates.shown(SearchTab.APPS))

        composeTestRule.onNodeWithText("Blocker").performTouchInput { longClick() }
        composeTestRule.onNodeWithText(text(uiString.core_ui_force_stop)).performClick()

        assertThat(appActions).containsExactly(AppKey("com.merxury.blocker", OWNER) to AppAction.FORCE_STOP)
    }

    @Test
    fun givenComponents_whenAComponentIsTapped_thenItOpensInItsApp() {
        show(SearchStates.shown(SearchTab.COMPONENTS))

        composeTestRule.onNodeWithText("PushService").performClick()

        assertThat(asked).containsExactly("openComponent PushService")
    }

    @Test
    fun givenComponents_whenTheirAppIsTapped_thenTheAppOpens() {
        show(SearchStates.shown(SearchTab.COMPONENTS))

        composeTestRule.onNodeWithText("Mail").performClick()

        assertThat(asked).containsExactly("openGroup ${AppKey("com.example.mail", OWNER)}")
    }

    @Test
    fun givenComponents_whenShown_thenTheirStateAndReasonsShow() {
        show(SearchStates.shown(SearchTab.COMPONENTS))

        composeTestRule.onNodeWithText(text(uiString.core_ui_blocked_by_package_manager)).assertIsDisplayed()
        composeTestRule.onNodeWithText(text(uiString.core_ui_conditional_rule)).assertIsDisplayed()
        composeTestRule.onNodeWithText(text(uiString.core_ui_unknown)).assertIsDisplayed()
    }

    @Test
    fun givenComponents_whenOneIsLongPressed_thenSelectionModeStartsWithIt() {
        show(SearchStates.shown(SearchTab.COMPONENTS))

        composeTestRule.onNodeWithText("PushReceiver").performTouchInput { longClick() }

        assertThat(asked).containsExactly("selectionMode true")
        assertThat(toggled).containsExactly(id("com.example.chat", "PushReceiver"))
    }

    @Test
    fun givenSelectionMode_whenAComponentIsTapped_thenItIsToggledNotOpened() {
        show(SearchStates.selecting())

        composeTestRule.onNodeWithText("PushProvider").performClick()
        composeTestRule.onNodeWithText("Chat").performClick()

        assertThat(toggled).containsExactly(id("com.example.mail", "PushProvider"))
        assertThat(asked).containsExactly("toggleApp ${AppKey("com.example.chat", OWNER)}")
    }

    @Test
    fun givenSelectionAcrossApps_whenBlockIsConfirmed_thenTheSelectionIsBlocked() {
        show(SearchStates.selecting())
        composeTestRule.onNodeWithText(plural(searchPlurals.feature_search_api_selected_app_count, 2)).assertIsDisplayed()

        composeTestRule.onNodeWithContentDescription(text(uiString.core_ui_block_selected)).performClick()
        composeTestRule.onNodeWithText(plural(uiPlurals.core_ui_block_all, 3)).assertIsDisplayed()
        composeTestRule.onNodeWithText(text(android.R.string.ok)).performClick()

        assertThat(asked).containsExactly("block")
    }

    @Test
    fun givenIndexing_whenShown_thenItsProgressShowsAboveTheResults() {
        show(SearchStates.indexing())

        composeTestRule.onNodeWithText(text(string.feature_search_api_indexing_progress, 34, 212)).assertIsDisplayed()
        composeTestRule.onNodeWithText("PushReceiver").assertIsDisplayed()
    }

    @Test
    fun givenNoAccess_whenGrantIsTapped_thenAccessIsAskedFor() {
        show(SearchStates.noAccess())

        composeTestRule.onNodeWithText(text(uiString.core_ui_banner_no_access)).assertIsDisplayed()
        composeTestRule.onNodeWithText(text(uiString.core_ui_grant_access)).performClick()

        assertThat(asked).containsExactly("grantAccess")
    }

    @Test
    fun givenTwoProfiles_whenPersonalIsChosen_thenItsUserIsSelected() {
        show(SearchStates.workProfile())

        composeTestRule.onNodeWithText(text(uiString.core_ui_profile_personal)).performClick()

        assertThat(selectedUsers).containsExactly(OWNER)
    }

    @Test
    fun givenOneUser_whenShown_thenNoProfileSwitcher() {
        show(SearchStates.shown(SearchTab.APPS))

        composeTestRule.onNodeWithTag("profileSwitcher").assertDoesNotExist()
    }

    @Test
    fun givenRules_whenARuleIsTapped_thenItOpens() {
        show(SearchStates.shown(SearchTab.RULES))

        composeTestRule.onNodeWithText("Push SDK").performClick()

        assertThat(asked).containsExactly("openRule 1")
    }
}
