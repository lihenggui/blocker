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

package com.merxury.blocker.feature.appdetail.impl

import androidx.activity.ComponentActivity
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.google.common.truth.Truth.assertThat
import com.merxury.blocker.core.designsystem.component.COLLAPSING_TOP_APP_BAR_ICON_TAG
import com.merxury.blocker.core.designsystem.theme.BlockerTheme
import com.merxury.blocker.core.model.component.ControlLayer
import com.merxury.blocker.core.model.control.ControlFailure
import com.merxury.blocker.core.model.control.ControlGoal
import com.merxury.blocker.core.model.privileged.PrivilegedIdentity
import com.merxury.blocker.core.model.system.ReadFailure
import com.merxury.blocker.core.ui.AppDetailTabs
import com.merxury.blocker.core.ui.TabState
import com.merxury.blocker.core.ui.control.ControlMessage
import com.merxury.blocker.core.ui.control.LayerFailure
import com.merxury.blocker.core.ui.control.ModeBanner
import com.merxury.blocker.core.ui.control.text
import com.merxury.blocker.core.ui.profile.ProfileLabel
import com.merxury.blocker.feature.appdetail.impl.ui.ComponentRowActions
import com.merxury.blocker.feature.appdetail.impl.ui.ComponentRowItem
import com.merxury.blocker.feature.appdetail.impl.ui.TopBarActions
import com.merxury.blocker.feature.appdetail.impl.ui.text
import dagger.hilt.android.testing.HiltTestApplication
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import com.merxury.blocker.core.ui.R.plurals as uiPlurals
import com.merxury.blocker.core.ui.R.string as uiString
import com.merxury.blocker.feature.appdetail.api.R.plurals as plurals
import com.merxury.blocker.feature.appdetail.api.R.string as string

@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class)
class AppDetailScreenTest {
    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private val switched = mutableListOf<Pair<String, Boolean>>()
    private val controlled = mutableListOf<Pair<String, ControlGoal>>()
    private val edited = mutableListOf<String>()
    private val opened = mutableListOf<String>()
    private val toggled = mutableListOf<String>()
    private val actions = ComponentRowActions(
        onSwitch = { row, enabled -> switched += row.id.simpleName to enabled },
        onControl = { row, goal -> controlled += row.id.simpleName to goal },
        onClick = { opened += it.id.simpleName },
        onToggleSelected = { toggled += it.id.simpleName },
        onEditIfwRule = { edited += it.id.simpleName },
    )

    private fun text(id: Int, vararg args: Any) = composeTestRule.activity.getString(id, *args)

    private fun row(name: String) = (previewActivityRows + previewServiceRows).single { it.id.simpleName == name }

    private fun show(name: String, inFlight: Boolean = false, isSelectionMode: Boolean = false) {
        composeTestRule.setContent {
            BlockerTheme {
                ComponentRowItem(row = row(name), inFlight = inFlight, actions = actions, isSelectionMode = isSelectionMode)
            }
        }
    }

    @Test
    fun givenRunnableComponent_whenSwitchTapped_thenAsksToTurnItOff() {
        show("MainActivity")

        composeTestRule.onNode(isToggleable()).assertIsOn().performClick()

        assertThat(switched).containsExactly("MainActivity" to false)
    }

    @Test
    fun givenBlockedComponent_whenShown_thenItsSwitchIsOffAndTheReasonsAreNamed() {
        show("SurveyActivity")

        composeTestRule.onNode(isToggleable()).assertIsOff().performClick()
        composeTestRule.onNodeWithText(text(uiString.core_ui_blocked_by_package_manager)).assertIsDisplayed()
        composeTestRule.onNodeWithText(text(uiString.core_ui_blocked_by_intent_firewall)).assertIsDisplayed()

        assertThat(switched).containsExactly("SurveyActivity" to true)
    }

    @Test
    fun givenComponentTheAppShipsDisabled_whenShown_thenSaysTheAppDisabledIt() {
        show("DebugActivity")

        composeTestRule.onNodeWithText(text(uiString.core_ui_disabled_by_app)).assertIsDisplayed()
    }

    @Test
    fun givenChangeInFlight_whenSwitchTapped_thenTheTapIsIgnored() {
        show("MainActivity", inFlight = true)

        composeTestRule.onNode(isToggleable()).assertDoesNotExist()
        composeTestRule.onNodeWithContentDescription(text(string.feature_appdetail_api_changing), useUnmergedTree = true).performClick()

        assertThat(switched).isEmpty()
    }

    @Test
    fun givenConditionalRule_whenItsTagTapped_thenTheRuleEditorOpens() {
        show("ShareActivity")

        composeTestRule.onNode(isToggleable()).assertIsOn()
        composeTestRule.onNodeWithText(text(uiString.core_ui_conditional_rule)).performClick()

        assertThat(edited).containsExactly("ShareActivity")
    }

    @Test
    fun givenUnknownState_whenExplained_thenTheCauseIsShownAndTheUserCanBlock() {
        show("SettingsActivity")

        composeTestRule.onNode(isToggleable()).assertDoesNotExist()
        composeTestRule.onNodeWithContentDescription(text(string.feature_appdetail_api_unknown_state_title)).performClick()
        composeTestRule.onNodeWithText(text(string.feature_appdetail_api_unknown_rules_unreadable)).assertIsDisplayed()
        composeTestRule.onNodeWithText(text(string.feature_appdetail_api_block)).performClick()

        assertThat(controlled).containsExactly("SettingsActivity" to ControlGoal.BLOCK)
        composeTestRule.onNodeWithTag("component:unknownDialog").assertDoesNotExist()
    }

    @Test
    fun givenSelectionMode_whenRowTapped_thenItIsSelectedInsteadOfOpened() {
        show("MainActivity", isSelectionMode = true)

        composeTestRule.onNodeWithText("MainActivity").performClick()

        assertThat(toggled).containsExactly("MainActivity")
        assertThat(opened).isEmpty()
    }

    @Test
    fun givenNoAccess_whenGrantTapped_thenAccessIsAskedFor() {
        var granted = 0
        composeTestRule.setContent {
            BlockerTheme {
                AppDetailContent(
                    header = AppHeaderUiState(app = previewHeaderApp),
                    list = componentTab(banners = listOf(ModeBanner.NO_ACCESS)),
                    control = ControlUiState(),
                    actions = AppDetailActions(onGrantAccess = { granted++ }),
                )
            }
        }

        composeTestRule.onNodeWithText(text(uiString.core_ui_banner_no_access)).assertIsDisplayed()
        composeTestRule.onNodeWithText(text(uiString.core_ui_grant_access)).performClick()

        assertThat(granted).isEqualTo(1)
    }

    @Test
    fun givenWorkProfileApp_whenShown_thenTheHeaderNamesTheProfile() {
        composeTestRule.setContent {
            BlockerTheme {
                AppDetailContent(
                    header = AppHeaderUiState(app = previewHeaderApp, profile = ProfileLabel.Work),
                    list = componentTab(),
                    control = ControlUiState(),
                )
            }
        }

        composeTestRule.onNodeWithText("2.0.4 (2004) · ${text(string.feature_appdetail_api_profile_work)}").assertExists()
    }

    @Test
    fun givenAppOfAnotherUserOutsideTheProfileGroup_whenShown_thenNoLaunchAction() {
        showHeader(LaunchMode.NONE)

        composeTestRule.onNodeWithTag(COLLAPSING_TOP_APP_BAR_ICON_TAG).assertExists().assert(hasClickAction().not())
    }

    @Test
    fun givenLaunchableApp_whenIconTapped_thenLaunched() {
        var launched = 0
        showHeader(LaunchMode.PROFILE) { launched++ }

        composeTestRule.onNodeWithTag(COLLAPSING_TOP_APP_BAR_ICON_TAG).assert(hasClickAction()).performClick()

        assertThat(launched).isEqualTo(1)
    }

    @Test
    fun givenDataDirectory_whenAppInfoShown_thenItIsListed() {
        composeTestRule.setContent {
            BlockerTheme {
                AppDetailContent(
                    header = AppHeaderUiState(app = previewHeaderApp.copy(dataDir = "/data/user/10/com.merxury.blocker")),
                    list = componentTab().copy(tabs = TabState(listOf(AppDetailTabs.Info), AppDetailTabs.Info)),
                    control = ControlUiState(),
                )
            }
        }

        composeTestRule.onNodeWithText(text(string.feature_appdetail_api_data_dir)).assertExists()
        composeTestRule.onNodeWithText("/data/user/10/com.merxury.blocker").assertExists()
    }

    @Test
    fun givenNoDataDirectory_whenAppInfoShown_thenNoRow() {
        composeTestRule.setContent {
            BlockerTheme {
                AppDetailContent(
                    header = AppHeaderUiState(app = previewHeaderApp.copy(dataDir = null)),
                    list = componentTab().copy(tabs = TabState(listOf(AppDetailTabs.Info), AppDetailTabs.Info)),
                    control = ControlUiState(),
                )
            }
        }

        composeTestRule.onNodeWithText(text(string.feature_appdetail_api_data_dir)).assertDoesNotExist()
    }

    private fun showHeader(launch: LaunchMode, onLaunch: () -> Unit = {}) {
        composeTestRule.setContent {
            BlockerTheme {
                AppDetailContent(
                    header = AppHeaderUiState(app = previewHeaderApp, launch = launch),
                    list = componentTab(),
                    control = ControlUiState(),
                    topBarActions = TopBarActions(onLaunchApp = onLaunch),
                )
            }
        }
    }

    @Test
    fun givenAppNotInstalled_whenShown_thenSaysSo() {
        composeTestRule.setContent {
            BlockerTheme {
                AppDetailContent(
                    header = AppHeaderUiState(),
                    list = componentTab().copy(components = ComponentsUiState.Loaded(AppComponents()), refreshFailure = ReadFailure.PackageNotFound(OWN_APP)),
                    control = ControlUiState(),
                )
            }
        }

        composeTestRule.onNodeWithText(text(string.feature_appdetail_api_not_installed)).assertIsDisplayed()
    }

    @Test
    fun givenPartialBlockRefusedToTheShell_whenTold_thenNamesTheLayerAndThatRootIsNeeded() {
        showText { refused(PrivilegedIdentity.SHELL, "shell").text() }

        composeTestRule.onNodeWithText(
            text(uiString.core_ui_block_component_partial, "AdActivity") + "\n" +
                text(uiString.core_ui_layer_reason, "Intent Firewall", text(uiString.core_ui_reason_component_needs_root)),
        ).assertIsDisplayed()
    }

    @Test
    fun givenBlockRefusedToRoot_whenTold_thenSaysTheSystemDoesNotAllowItWithItsMessage() {
        showText { refused(PrivilegedIdentity.ROOT, "Cannot disable a protected package").text() }

        composeTestRule.onNodeWithText(
            text(uiString.core_ui_block_component_partial, "AdActivity") + "\n" +
                text(
                    uiString.core_ui_layer_reason,
                    "Intent Firewall",
                    text(uiString.core_ui_reason_not_permitted_detail, "Cannot disable a protected package"),
                ),
        ).assertIsDisplayed()
        composeTestRule.onNodeWithText(text(uiString.core_ui_reason_component_needs_root), substring = true).assertDoesNotExist()
    }

    @Test
    fun givenBlockRefusedToRootWithoutAMessage_whenTold_thenSaysTheSystemDoesNotAllowIt() {
        showText { refused(PrivilegedIdentity.SYSTEM, null).text() }

        composeTestRule.onNodeWithText(
            text(uiString.core_ui_block_component_partial, "AdActivity") + "\n" +
                text(uiString.core_ui_layer_reason, "Intent Firewall", text(uiString.core_ui_reason_not_permitted)),
        ).assertIsDisplayed()
    }

    private fun refused(identity: PrivilegedIdentity, message: String?) = ControlMessage.ComponentNotChanged(
        name = "AdActivity",
        goal = ControlGoal.BLOCK,
        partial = true,
        failures = listOf(LayerFailure(ControlLayer.INTENT_FIREWALL, ControlFailure.NotPermitted(message))),
        identity = identity,
    )

    @Test
    fun givenBatchThatLeftOnlyShippedDisabledComponentsOff_whenTold_thenSaysWhyTheyStayOffInsteadOfNothingNeeded() {
        showText { ControlMessage.BatchDone(ControlGoal.UNBLOCK, changed = 0, notChanged = 0, mostCommon = null, staysOffAsShipped = 3).text() }

        composeTestRule.onNodeWithText(plural(uiPlurals.core_ui_batch_stays_off_as_shipped, 3)).assertIsDisplayed()
        composeTestRule.onNodeWithText(text(uiString.core_ui_batch_nothing_needed), substring = true).assertDoesNotExist()
    }

    @Test
    fun givenBatchThatChangedSomeAndLeftShippedDisabledOff_whenTold_thenSaysBoth() {
        showText { ControlMessage.BatchDone(ControlGoal.UNBLOCK, changed = 2, notChanged = 0, mostCommon = null, staysOffAsShipped = 1).text() }

        composeTestRule.onNodeWithText(
            plural(uiPlurals.core_ui_batch_changed, 2) + "\n" + plural(uiPlurals.core_ui_batch_stays_off_as_shipped, 1),
        ).assertIsDisplayed()
    }

    private fun plural(id: Int, count: Int) = composeTestRule.activity.resources.getQuantityString(id, count, count)

    @Test
    fun givenBatchWithNothingToChange_whenTold_thenSaysNothingNeededChanging() {
        showText { ControlMessage.BatchDone(ControlGoal.BLOCK, changed = 0, notChanged = 0, mostCommon = null).text() }

        composeTestRule.onNodeWithText(text(uiString.core_ui_batch_nothing_needed)).assertIsDisplayed()
    }

    @Test
    fun givenBatchWithFailures_whenTold_thenCountsAndTheMostCommonReason() {
        showText {
            ControlMessage.BatchDone(
                goal = ControlGoal.ENABLE,
                changed = 23,
                notChanged = 2,
                mostCommon = LayerFailure(ControlLayer.PACKAGE_MANAGER, ControlFailure.ChannelUnavailable),
            ).text()
        }

        composeTestRule.onNodeWithText(
            "23 components changed, 2 could not be changed\nMost often: Package Manager: " + text(uiString.core_ui_reason_no_access),
        ).assertIsDisplayed()
    }

    @Test
    fun givenLaunchBlockedByRule_whenTold_thenSaysItDidNotOpen() {
        showText { ListMessage.LaunchBlockedByRule("AdActivity").text() }

        composeTestRule.onNodeWithText(text(string.feature_appdetail_api_launch_blocked, "AdActivity")).assertIsDisplayed()
    }

    private fun showText(message: @Composable () -> String) {
        composeTestRule.setContent {
            BlockerTheme { Text(text = message()) }
        }
    }

    private fun componentTab(banners: List<ModeBanner> = emptyList()) = ComponentListUiState(
        tabs = TabState(listOf(AppDetailTabs.Info, AppDetailTabs.Activity), AppDetailTabs.Activity, mapOf(AppDetailTabs.Activity to previewActivityRows.size)),
        components = ComponentsUiState.Loaded(previewComponents),
        banners = banners,
    )
}
