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

package com.merxury.blocker.feature.applist.impl

import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import com.google.common.truth.Truth.assertThat
import com.merxury.blocker.core.designsystem.theme.BlockerTheme
import com.merxury.blocker.core.model.component.AppKey
import com.merxury.blocker.core.model.component.UserId
import com.merxury.blocker.core.model.system.AppAction
import com.merxury.blocker.core.model.system.DeviceUser
import com.merxury.blocker.core.model.system.UserKind
import com.merxury.blocker.core.ui.applist.previewAppRows
import com.merxury.blocker.core.ui.profile.ProfileLabel
import com.merxury.blocker.core.ui.profile.ProfileTab
import com.merxury.blocker.core.ui.profile.toProfileTab
import dagger.hilt.android.testing.HiltTestApplication
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import com.merxury.blocker.core.ui.R.string as uiString
import com.merxury.blocker.feature.applist.api.R.string as string

@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class)
class AppListScreenTest {
    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private val profiles = listOf(
        ProfileTab(OWNER, ProfileLabel.Personal, running = true),
        ProfileTab(WORK, ProfileLabel.Work, running = false),
    )
    private val opened = mutableListOf<Pair<String, Int?>>()
    private val actions = mutableListOf<Pair<AppKey, AppAction>>()
    private val selected = mutableListOf<UserId>()

    private fun show(uiState: AppListUiState) {
        composeTestRule.setContent {
            BlockerTheme {
                AppListScreen(
                    uiState = uiState,
                    navigateToAppDetail = { packageName, userId -> opened += packageName to userId },
                    onSelectUser = { selected += it },
                    onAction = { app, action -> actions += app to action },
                )
            }
        }
    }

    private fun text(id: Int, vararg args: Any) = composeTestRule.activity.getString(id, *args)

    @Test
    fun whenOpeningAppsOverflow_thenRemainingMenuItemsAreDisplayed() {
        show(AppListUiState(OWNER, OWNER, apps = AppsUiState.Loaded(emptyList())))

        composeTestRule.onNodeWithContentDescription(text(uiString.core_ui_more_menu)).performClick()
        composeTestRule.onNodeWithText(text(string.feature_applist_api_app_slimming)).assertIsDisplayed()
        composeTestRule.onNodeWithText(text(string.feature_applist_api_settings)).assertIsDisplayed()
        composeTestRule.onNodeWithText(text(string.feature_applist_api_support_and_feedback)).assertIsDisplayed()
    }

    @Test
    fun givenOwnUserApp_whenTapped_thenDetailOpensWithoutAUser() {
        show(AppListUiState(OWNER, OWNER, apps = AppsUiState.Loaded(previewAppRows(OWNER))))

        composeTestRule.onNodeWithText("Blocker Test").performClick()

        assertThat(opened).containsExactly("com.test.blocker" to null)
    }

    @Test
    fun givenOtherUserApp_whenTapped_thenDetailOpensForThatUser() {
        show(AppListUiState(OWNER, WORK, profiles = profiles, apps = AppsUiState.Loaded(previewAppRows(WORK))))

        composeTestRule.onNodeWithText("Blocker Test").performClick()

        assertThat(opened).containsExactly("com.test.blocker" to WORK.value)
    }

    @Test
    fun givenOtherUserApp_whenEnableChosenFromItsMenu_thenEnabledForThatUser() {
        show(AppListUiState(OWNER, WORK, profiles = profiles, apps = AppsUiState.Loaded(previewAppRows(WORK))))

        composeTestRule.onNodeWithText("Blocker Test").performTouchInput { longClick() }
        composeTestRule.onNodeWithText(text(uiString.core_ui_enable)).performClick()

        assertThat(actions).containsExactly(AppKey("com.test.blocker", WORK) to AppAction.ENABLE)
    }

    @Test
    fun givenTwoProfiles_whenWorkTapped_thenWorkSelectedAndStoppedMarked() {
        show(AppListUiState(OWNER, OWNER, profiles = profiles, apps = AppsUiState.Loaded(previewAppRows(OWNER))))

        composeTestRule.onNodeWithText(text(uiString.core_ui_profile_not_running)).assertIsDisplayed()
        composeTestRule.onNodeWithText(text(uiString.core_ui_profile_work)).performClick()

        assertThat(selected).containsExactly(WORK)
    }

    @Test
    fun givenRunningButLockedProfile_whenSwitcherIsShown_thenItsNameHasTheNotRunningMarker() {
        val locked = DeviceUser(WORK, "Work", UserKind.WORK_PROFILE, OWNER, running = true, unlocked = false)
        val tabs = listOf(profiles.first(), locked.toProfileTab())
        show(AppListUiState(OWNER, OWNER, profiles = tabs, apps = AppsUiState.Loaded(previewAppRows(OWNER))))

        composeTestRule.onNodeWithText(text(uiString.core_ui_profile_not_running)).assertIsDisplayed()
        composeTestRule.onNodeWithText(text(uiString.core_ui_profile_personal)).assertIsDisplayed()
    }

    @Test
    fun givenServiceInfoShown_whenCountsDropped_thenRowsCountAgain() {
        val counted = mutableListOf<String>()
        val rows = previewAppRows(OWNER).take(1)
        var state by mutableStateOf(AppListUiState(OWNER, OWNER, apps = AppsUiState.Loaded(rows), showServiceInfo = true))
        composeTestRule.setContent {
            BlockerTheme {
                AppListScreen(uiState = state, countServices = { counted += it.packageName })
            }
        }
        composeTestRule.waitForIdle()

        state = state.copy(apps = AppsUiState.Loaded(rows.map { it.copy(services = null) }))
        composeTestRule.waitForIdle()

        assertThat(counted).containsExactly("com.merxury.blocker", "com.merxury.blocker")
    }

    @Test
    fun givenSingleUser_whenShown_thenNoSwitcher() {
        show(AppListUiState(OWNER, OWNER, profiles = profiles.take(1), apps = AppsUiState.Loaded(previewAppRows(OWNER))))

        composeTestRule.onNodeWithTag("profileSwitcher").assertDoesNotExist()
    }

    @Test
    fun givenOtherUserApp_whenUninstallConfirmed_thenUninstalledForThatProfile() {
        show(AppListUiState(OWNER, WORK, profiles = profiles, apps = AppsUiState.Loaded(previewAppRows(WORK))))
        composeTestRule.onNodeWithText("Blocker Test").performTouchInput { longClick() }

        composeTestRule.onNodeWithText(text(uiString.core_ui_uninstall)).performClick()

        assertThat(actions).isEmpty()
        composeTestRule.onNodeWithText(text(uiString.core_ui_uninstall_for_profile_confirm, "Work")).assertIsDisplayed()
        composeTestRule.onNodeWithText(text(android.R.string.ok)).performClick()
        assertThat(actions).containsExactly(AppKey("com.test.blocker", WORK) to AppAction.UNINSTALL)
    }

    @Test
    fun givenApp_whenClearDataChosen_thenAsked() {
        show(AppListUiState(OWNER, WORK, profiles = profiles, apps = AppsUiState.Loaded(previewAppRows(WORK))))
        composeTestRule.onNodeWithText("Blocker Test").performTouchInput { longClick() }

        composeTestRule.onNodeWithText(text(uiString.core_ui_clear_data)).performClick()

        composeTestRule.onNodeWithText(text(uiString.core_ui_do_you_want_to_clear_data_of_this_app)).assertIsDisplayed()
    }
}
