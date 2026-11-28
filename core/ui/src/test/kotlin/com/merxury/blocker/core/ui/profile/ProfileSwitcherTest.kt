// Copyright 2026 Blocker
// SPDX-License-Identifier: Apache-2.0

package com.merxury.blocker.core.ui.profile

import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.google.common.truth.Truth.assertThat
import com.merxury.blocker.core.model.component.UserId
import com.merxury.blocker.core.ui.R
import dagger.hilt.android.testing.HiltTestApplication
import kotlinx.coroutines.test.StandardTestDispatcher
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.LooperMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = HiltTestApplication::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
class ProfileSwitcherTest {
    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>(effectContext = StandardTestDispatcher())

    @Test
    fun givenPersonalAndWorkProfiles_whenSelectingWork_thenReportsItsUserIdAndUpdatesSelectedTab() {
        val personal = ProfileTab(UserId(0), ProfileLabel.Personal, running = true)
        val work = ProfileTab(UserId(10), ProfileLabel.Work, running = true)
        val selection = mutableStateOf(personal.user)
        val selectedUsers = mutableListOf<UserId>()
        composeTestRule.setContent {
            MaterialTheme {
                ProfileSwitcher(
                    profiles = listOf(personal, work),
                    selectedUser = selection.value,
                    onSelect = {
                        selectedUsers += it
                        selection.value = it
                    },
                )
            }
        }

        val personalTab = composeTestRule.onNodeWithText(composeTestRule.activity.getString(R.string.core_ui_profile_personal))
        val workTab = composeTestRule.onNodeWithText(composeTestRule.activity.getString(R.string.core_ui_profile_work))
        personalTab.assertIsDisplayed().assertIsSelected()
        workTab.assertIsDisplayed().assertIsNotSelected()

        workTab.performClick()

        composeTestRule.runOnIdle { assertThat(selectedUsers).containsExactly(work.user) }
        personalTab.assertIsNotSelected()
        workTab.assertIsSelected()
    }
}
