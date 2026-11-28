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
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.google.common.truth.Truth.assertThat
import com.merxury.blocker.core.designsystem.component.COLLAPSING_TOP_APP_BAR_ICON_TAG
import com.merxury.blocker.core.designsystem.theme.BlockerTheme
import com.merxury.blocker.core.model.component.ControlLayer
import com.merxury.blocker.core.model.control.ControlFailure
import com.merxury.blocker.core.model.control.ControlGoal
import com.merxury.blocker.core.model.privileged.PrivilegedIdentity
import com.merxury.blocker.core.model.system.ReadFailure
import com.merxury.blocker.core.ui.TabState
import com.merxury.blocker.core.ui.control.ControlMessage
import com.merxury.blocker.core.ui.control.text
import com.merxury.blocker.core.ui.profile.ProfileLabel
import com.merxury.blocker.feature.appdetail.api.navigation.AppDetailTabs
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
import com.merxury.blocker.feature.wake.api.R.string as wakeString

@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class)
class AppDetailScreenTest {
    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private val switched = mutableListOf<Pair<String, Boolean>>()
    private val opened = mutableListOf<String>()
    private val toggled = mutableListOf<String>()
    private val actions = ComponentRowActions(
        onSwitch = { row, enabled -> switched += row.id.simpleName to enabled },
        onClick = { opened += it.id.simpleName },
        onToggleSelected = { toggled += it.id.simpleName },
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
    fun givenBlockedComponent_whenShown_thenItsSwitchIsOffWithoutReasons() {
        show("TrackerActivity")

        composeTestRule.onNode(isToggleable()).assertIsOff().performClick()

        assertThat(switched).containsExactly("TrackerActivity" to true)
    }

    @Test
    fun givenChangeInFlight_whenSwitchTapped_thenTheTapIsIgnored() {
        show("MainActivity", inFlight = true)

        composeTestRule.onNode(isToggleable()).assertIsOn().assertIsNotEnabled().performClick()

        assertThat(switched).isEmpty()
    }

    @Test
    fun givenComponent_whenTapped_thenItsDetailOpens() {
        show("MainActivity")

        composeTestRule.onNodeWithText("MainActivity").performClick()

        assertThat(opened).containsExactly("MainActivity")
    }

    @Test
    fun givenSelectionMode_whenRowTapped_thenItIsSelectedInsteadOfOpened() {
        show("MainActivity", isSelectionMode = true)

        composeTestRule.onNodeWithText("MainActivity").performClick()

        assertThat(toggled).containsExactly("MainActivity")
        assertThat(opened).isEmpty()
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

    @Test
    fun givenReadyOwnUserApp_whenBackgroundActivityTapped_thenOpensTheAppActivity() {
        var openedBackground = false
        showInfo(AppHeaderUiState(launch = LaunchMode.OWN_USER, backgroundActivityAvailable = true)) { openedBackground = true }

        val row = composeTestRule.onNodeWithText(text(wakeString.feature_wake_api_title))
        row.performScrollTo()
        composeTestRule.onNodeWithText(text(wakeString.feature_wake_api_app_detail_summary)).assertIsDisplayed()
        row.performClick()

        composeTestRule.runOnIdle { assertThat(openedBackground).isTrue() }
    }

    @Test
    fun givenAnotherUsersApp_whenViewingInfo_thenBackgroundActivityIsHidden() {
        showInfo(AppHeaderUiState(launch = LaunchMode.PROFILE, backgroundActivityAvailable = true))

        composeTestRule.onNodeWithTag("appDetail:summaryContent").assertExists()
        composeTestRule.onNodeWithText(text(wakeString.feature_wake_api_title)).assertDoesNotExist()
    }

    @Test
    fun givenOwnUserAppWithUnavailableWake_whenViewingInfo_thenBackgroundActivityIsHidden() {
        showInfo(AppHeaderUiState(launch = LaunchMode.OWN_USER))

        composeTestRule.onNodeWithTag("appDetail:summaryContent").assertExists()
        composeTestRule.onNodeWithText(text(wakeString.feature_wake_api_title)).assertDoesNotExist()
    }

    private fun showInfo(header: AppHeaderUiState, openBackground: () -> Unit = {}) {
        composeTestRule.setContent {
            BlockerTheme {
                AppDetailContent(
                    header = header.copy(app = previewHeaderApp),
                    list = componentTab().copy(tabs = TabState(listOf(AppDetailTabs.Info), AppDetailTabs.Info)),
                    control = ControlUiState(),
                    actions = AppDetailActions(onShowBackgroundActivity = openBackground),
                )
            }
        }
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
    fun givenPartialBlock_whenTold_thenOnlySaysItCouldNotBeBlocked() {
        showText { ControlMessage.ComponentNotChanged("AdActivity", ControlGoal.BLOCK).text() }

        composeTestRule.onNodeWithText(text(uiString.core_ui_block_component_failed, "AdActivity")).assertIsDisplayed()
    }

    @Test
    fun givenEnableWithoutAccess_whenTold_thenSaysHowToGetAccess() {
        showText { ControlMessage.ComponentNotChanged("AdActivity", ControlGoal.ENABLE, needsAccess = true).text() }

        composeTestRule.onNodeWithText(
            text(uiString.core_ui_enable_component_failed, "AdActivity") + "\n" + text(uiString.core_ui_reason_no_access),
        ).assertIsDisplayed()
    }

    @Test
    fun givenBatchWithoutFailures_whenTold_thenSaysTheOperationCompleted() {
        showText { ControlMessage.BatchDone(notChanged = 0).text() }

        composeTestRule.onNodeWithText(text(uiString.core_ui_operation_completed)).assertIsDisplayed()
    }

    @Test
    fun givenBatchWithFailures_whenTold_thenCountsTheComponentsNotChanged() {
        showText { ControlMessage.BatchDone(notChanged = 2).text() }

        composeTestRule.onNodeWithText(plural(uiPlurals.core_ui_operation_completed_with_failures, 2)).assertIsDisplayed()
    }

    private fun plural(id: Int, count: Int) = composeTestRule.activity.resources.getQuantityString(id, count, count)

    private fun showText(message: @Composable () -> String) {
        composeTestRule.setContent {
            BlockerTheme { Text(text = message()) }
        }
    }

    private fun componentTab() = ComponentListUiState(
        tabs = TabState(listOf(AppDetailTabs.Info, AppDetailTabs.Activity), AppDetailTabs.Activity, mapOf(AppDetailTabs.Activity to previewActivityRows.size)),
        components = ComponentsUiState.Loaded(previewComponents),
    )
}
