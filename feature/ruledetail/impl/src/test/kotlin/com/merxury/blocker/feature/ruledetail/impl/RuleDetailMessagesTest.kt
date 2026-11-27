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

package com.merxury.blocker.feature.ruledetail.impl

import androidx.activity.ComponentActivity
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.google.common.truth.Truth.assertThat
import com.merxury.blocker.core.designsystem.theme.BlockerTheme
import dagger.hilt.android.testing.HiltTestApplication
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import com.merxury.blocker.core.ui.R.string as uiString

/** A failed launch is told in the app detail's words, offering access when it was missing. */
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class)
class RuleDetailMessagesTest {
    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private var granted = 0
    private var shown = 0

    private fun show(failure: ActivityLaunchFailed) {
        composeTestRule.setContent {
            val hostState = remember { SnackbarHostState() }
            var current by remember { mutableStateOf<ActivityLaunchFailed?>(failure) }
            BlockerTheme {
                SnackbarHost(hostState)
                ActivityLaunchFailedSnackbar(
                    failure = current,
                    snackbarHostState = hostState,
                    onShow = {
                        shown++
                        current = null
                    },
                    onGrantAccess = { granted++ },
                )
            }
        }
    }

    private fun text(id: Int, vararg args: Any) = composeTestRule.activity.getString(id, *args)

    @Test
    fun givenNoAccess_whenALaunchFails_thenTheAppDetailTextAndTheAccessHintAreShown() {
        show(ActivityLaunchFailed("MainActivity", needsAccess = true))

        composeTestRule.onNodeWithText("${text(uiString.core_ui_launch_failed, "MainActivity")}\n${text(uiString.core_ui_reason_no_access)}").assertIsDisplayed()
        composeTestRule.onNodeWithText(text(uiString.core_ui_grant_access)).performClick()
        composeTestRule.waitForIdle()

        assertThat(granted).isEqualTo(1)
        assertThat(shown).isEqualTo(1)
    }

    @Test
    fun givenNotPermitted_whenALaunchFails_thenOnlyTheAppDetailTextIsShown() {
        show(ActivityLaunchFailed("MainActivity", needsAccess = false))

        composeTestRule.onNodeWithText(text(uiString.core_ui_launch_failed, "MainActivity")).assertIsDisplayed()
        assertThat(composeTestRule.onAllNodesWithText(text(uiString.core_ui_grant_access)).fetchSemanticsNodes()).isEmpty()
    }
}
