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
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.google.common.truth.Truth.assertThat
import com.merxury.blocker.core.designsystem.theme.BlockerTheme
import com.merxury.blocker.core.model.ComponentType
import com.merxury.blocker.core.model.component.AppKey
import com.merxury.blocker.core.model.component.ComponentId
import com.merxury.blocker.core.model.component.ComponentKey
import com.merxury.blocker.core.ui.component.ComponentRow
import com.merxury.blocker.feature.ruledetail.impl.component.MatchedAppActions
import com.merxury.blocker.feature.ruledetail.impl.component.RuleMatchedAppList
import dagger.hilt.android.testing.HiltTestApplication
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import com.merxury.blocker.core.ui.R.string as uiString

/** The matched apps open their detail for their user and their switches ask for the change. */
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class)
class RuleMatchedAppListTest {
    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private val opened = mutableListOf<AppKey>()
    private val switched = mutableListOf<Pair<ComponentKey, Boolean>>()
    private val blocked = mutableListOf<AppKey>()
    private val component = id("com.w", "Work")
    private val workApp = MatchedApp(
        app = AppKey("com.w", WORK),
        label = "Work app",
        icon = null,
        rows = listOf(ComponentRow(component, ComponentType.RECEIVER, enabled = true)),
    )
    private val actions = MatchedAppActions(
        onSwitch = { id, enabled -> switched += id to enabled },
        onBlockAll = { blocked += it.app },
        onOpenApp = { opened += it },
    )

    private fun show(inFlight: Set<ComponentId> = emptySet()) {
        composeTestRule.setContent {
            BlockerTheme {
                RuleMatchedAppList(ruleMatchedAppListUiState = MatchedAppsUiState.Loaded(listOf(workApp)), inFlight = inFlight, actions = actions)
            }
        }
    }

    private fun text(id: Int) = composeTestRule.activity.getString(id)

    @Test
    fun givenWorkApp_whenOpened_thenItsDetailOpensForTheWorkProfile() {
        show()

        composeTestRule.onNodeWithContentDescription(text(uiString.core_ui_more_menu)).performClick()
        composeTestRule.onNodeWithText(text(uiString.core_ui_open_app_detail)).performClick()

        assertThat(opened).containsExactly(AppKey("com.w", WORK))
        assertThat(opened.single().userIdFor(OWNER)).isEqualTo(WORK.value)
        assertThat(AppKey("com.w", OWNER).userIdFor(OWNER)).isNull()
    }

    @Test
    fun givenWorkApp_whenBlockAllIsChosen_thenItsAppIsAsked() {
        show()

        composeTestRule.onNodeWithContentDescription(text(uiString.core_ui_more_menu)).performClick()
        composeTestRule.onNodeWithText(text(uiString.core_ui_block_all_components)).performClick()

        assertThat(blocked).containsExactly(AppKey("com.w", WORK))
    }

    @Test
    fun givenExpandedApp_whenTheSwitchIsTapped_thenItAsksToTurnItOff() {
        show()
        composeTestRule.onNodeWithText("Work app").performClick()

        composeTestRule.onNode(isToggleable()).assertIsOn().performClick()

        // Named with the work profile, the user of the app the row is in.
        assertThat(switched).containsExactly(ComponentKey(component, WORK) to false)
    }

    @Test
    fun givenChangeInFlight_whenTheSwitchIsTapped_thenTheTapIsIgnored() {
        show(inFlight = setOf(component))
        composeTestRule.onAllNodesWithContentDescription(text(uiString.core_ui_expand_list))[0].performClick()

        composeTestRule.onNode(isToggleable()).assertIsOn().assertIsNotEnabled().performClick()

        assertThat(switched).isEmpty()
    }

    @Test
    fun givenBlockedComponent_whenShown_thenItsSwitchIsOff() {
        composeTestRule.setContent {
            BlockerTheme {
                RuleMatchedAppList(
                    ruleMatchedAppListUiState = MatchedAppsUiState.Loaded(listOf(workApp.copy(rows = workApp.rows.map { it.copy(enabled = false) }))),
                    actions = actions,
                )
            }
        }
        composeTestRule.onNodeWithText("Work app").performClick()

        composeTestRule.onNode(isToggleable()).assertIsOff()
    }
}
