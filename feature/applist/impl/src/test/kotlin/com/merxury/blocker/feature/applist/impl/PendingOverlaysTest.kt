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
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import com.google.common.truth.Truth.assertThat
import com.merxury.blocker.core.designsystem.theme.BlockerTheme
import com.merxury.blocker.core.model.component.AppKey
import com.merxury.blocker.core.ui.profile.ProfileLabel
import com.merxury.blocker.feature.applist.impl.component.previewAppRows
import dagger.hilt.android.testing.HiltTestApplication
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import com.merxury.blocker.core.ui.R.string as uiString
import com.merxury.blocker.feature.applist.api.R.string as string

/**
 * An open confirmation belongs to one app of one user. The same package is
 * installed for both users here, so acting on the wrong user would be possible.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class)
class PendingOverlaysTest {
    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private val profiles = listOf(
        ProfileTab(OWNER, ProfileLabel.Personal, running = true),
        ProfileTab(WORK, ProfileLabel.Work, running = true),
    )
    private val workShown = AppListUiState(OWNER, WORK, profiles = profiles, apps = AppsUiState.Loaded(previewAppRows(WORK)))
    private val personalShown = workShown.copy(selectedUser = OWNER, apps = AppsUiState.Loaded(previewAppRows(OWNER)))
    private val actions = mutableListOf<Pair<AppKey, AppAction>>()
    private var state by mutableStateOf(workShown)
    private val restoration = StateRestorationTester(composeTestRule)

    private fun text(id: Int, vararg args: Any) = composeTestRule.activity.getString(id, *args)

    private fun show() {
        restoration.setContent {
            BlockerTheme {
                AppListScreen(uiState = state, onAction = { app, action -> actions += app to action })
            }
        }
    }

    private fun openWorkUninstallConfirmation() {
        composeTestRule.onNodeWithText("Blocker Test").performTouchInput { longClick() }
        composeTestRule.onNodeWithText(text(uiString.core_ui_uninstall)).performClick()
        composeTestRule.onNodeWithText(text(string.feature_applist_api_uninstall_for_profile_confirm, "Work")).assertIsDisplayed()
    }

    @Test
    fun givenConfirmationOpen_whenSelectionFallsBackToPersonal_thenDismissedAndNothingActs() {
        show()
        openWorkUninstallConfirmation()

        state = personalShown
        composeTestRule.waitForIdle()
        state = workShown
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText(text(android.R.string.ok)).assertDoesNotExist()
        assertThat(actions).isEmpty()
    }

    @Test
    fun givenConfirmationOpen_whenRestoredWithPersonalSelected_thenDismissedAndNothingActs() {
        show()
        openWorkUninstallConfirmation()

        state = personalShown
        restoration.emulateSavedInstanceStateRestore()

        composeTestRule.onNodeWithText(text(android.R.string.ok)).assertDoesNotExist()
        assertThat(actions).isEmpty()
    }

    @Test
    fun givenConfirmationOpen_whenRestoredWithWorkStillSelected_thenConfirmActsOnTheWorkApp() {
        show()
        openWorkUninstallConfirmation()

        restoration.emulateSavedInstanceStateRestore()
        composeTestRule.onNodeWithText(text(android.R.string.ok)).performClick()

        assertThat(actions).containsExactly(AppKey("com.test.blocker", WORK) to AppAction.UNINSTALL)
    }
}
