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
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.testing.TestLifecycleOwner
import com.google.common.truth.Truth.assertThat
import com.merxury.blocker.core.designsystem.theme.BlockerTheme
import com.merxury.blocker.core.domain.model.ZippedRule
import com.merxury.blocker.core.model.component.ControlLayer
import com.merxury.blocker.core.model.control.ControlFailure
import com.merxury.blocker.core.model.control.ControlGoal
import com.merxury.blocker.core.model.system.ReadFailure
import com.merxury.blocker.core.rule.entity.RuleWorkEvent
import com.merxury.blocker.core.rule.entity.RuleWorkResult
import com.merxury.blocker.core.rule.entity.RuleWorkType
import com.merxury.blocker.core.rule.entity.resultMessage
import com.merxury.blocker.core.ui.AppDetailTabs
import com.merxury.blocker.core.ui.TabState
import com.merxury.blocker.feature.appdetail.impl.ui.ComponentRowActions
import com.merxury.blocker.feature.appdetail.impl.ui.TopBarActions
import dagger.hilt.android.testing.HiltTestApplication
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import com.merxury.blocker.core.ui.R.string as uiString
import com.merxury.blocker.feature.appdetail.api.R.string as string

/** How the screen tells its messages, which actions it offers where, and when it reads the app. */
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class)
class AppDetailScreenStateTest {
    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    // No snackbar host is shown, so a snackbar stays until it is dismissed or its caller is cancelled.
    private val snackbar = SnackbarHostState()

    private fun text(id: Int, vararg args: Any) = composeTestRule.activity.getString(id, *args)

    private fun shownMessage(): String? = composeTestRule.runOnIdle { snackbar.currentSnackbarData?.visuals?.message }

    private fun dismiss() {
        composeTestRule.runOnIdle { snackbar.currentSnackbarData?.dismiss() }
        composeTestRule.waitForIdle()
    }

    private val list = ComponentListUiState(tabs = TabState(listOf(AppDetailTabs.Info), AppDetailTabs.Info))

    @Test
    fun givenRuleWorkResult_whenShown_thenItStaysUntilDismissedAndIsConsumedOnce() {
        val event = RuleWorkEvent(RuleWorkType.EXPORT_BLOCKER_RULES, RuleWorkResult.FINISHED, changed = 3)
        var header by mutableStateOf(AppHeaderUiState(ruleWork = event))
        var consumed = 0
        composeTestRule.setContent {
            ScreenMessages(
                header = header,
                list = list,
                control = ControlUiState(),
                snackbarHostState = snackbar,
                onListMessageConsume = {},
                onControlMessageConsume = {},
                onRuleWorkConsume = {
                    consumed++
                    header = header.copy(ruleWork = null)
                },
                onShareConsume = {},
            )
        }

        assertThat(shownMessage()).isEqualTo(event.resultMessage(composeTestRule.activity.resources))
        assertThat(consumed).isEqualTo(0)

        dismiss()

        assertThat(consumed).isEqualTo(1)
        assertThat(shownMessage()).isNull()
    }

    @Test
    fun givenRulesThatCannotBeShared_whenShared_thenTheErrorStaysAfterTheShareIsConsumed() {
        var header by mutableStateOf(AppHeaderUiState(sharedRules = ZippedRule.EMPTY))
        var consumed = 0
        composeTestRule.setContent {
            ScreenMessages(
                header = header,
                list = list,
                control = ControlUiState(),
                snackbarHostState = snackbar,
                onListMessageConsume = {},
                onControlMessageConsume = {},
                onRuleWorkConsume = {},
                onShareConsume = {
                    consumed++
                    header = header.copy(sharedRules = null)
                },
            )
        }

        assertThat(consumed).isEqualTo(1)
        assertThat(shownMessage()).isEqualTo(text(string.feature_appdetail_api_cannot_share_rule_report_issue))
    }

    @Test
    fun givenTwoEqualControlMessages_whenTold_thenBothAreShownAndLaterOnesToo() {
        val failure = ControlMessage.ComponentNotChanged(
            name = "AdActivity",
            goal = ControlGoal.BLOCK,
            partial = false,
            failures = listOf(LayerFailure(ControlLayer.PACKAGE_MANAGER, ControlFailure.ChannelUnavailable)),
        )
        var control by mutableStateOf(ControlUiState(messages = listOf(QueuedControlMessage(0, failure), QueuedControlMessage(1, failure))))
        val consumed = mutableListOf<Long>()
        composeTestRule.setContent {
            ScreenMessages(
                header = AppHeaderUiState(),
                list = list,
                control = control,
                snackbarHostState = snackbar,
                onListMessageConsume = {},
                onControlMessageConsume = { id ->
                    consumed += id
                    control = control.copy(messages = control.messages.filterNot { it.id == id })
                },
                onRuleWorkConsume = {},
                onShareConsume = {},
            )
        }
        val failureText = text(string.feature_appdetail_api_block_failed, "AdActivity") + "\n" +
            text(string.feature_appdetail_api_layer_reason, "Package Manager", text(string.feature_appdetail_api_reason_no_access))

        assertThat(shownMessage()).isEqualTo(failureText)
        dismiss()
        assertThat(consumed).containsExactly(0L)
        assertThat(shownMessage()).isEqualTo(failureText)
        dismiss()
        assertThat(consumed).containsExactly(0L, 1L).inOrder()

        val summary = ControlMessage.BatchDone(ControlGoal.BLOCK, changed = 0, notChanged = 0, mostCommon = null)
        composeTestRule.runOnIdle { control = control.copy(messages = listOf(QueuedControlMessage(2, summary))) }
        assertThat(shownMessage()).isEqualTo(text(string.feature_appdetail_api_batch_nothing_needed))
    }

    @Test
    fun givenMatchedRulesTab_whenTheMenuIsOpened_thenOnlyThePageActionsAreOffered() {
        var blocked = 0
        var unblocked = 0
        showContent(
            list = sdkTab(),
            topBarActions = TopBarActions(onBlockAllInTab = { blocked++ }, onEnableAllInTab = { unblocked++ }),
        )

        composeTestRule.onNodeWithContentDescription(text(uiString.core_ui_search_icon)).assertDoesNotExist()
        composeTestRule.onNodeWithContentDescription(text(uiString.core_ui_more_menu)).performClick()
        composeTestRule.onNodeWithText(text(string.feature_appdetail_api_select_mutiple)).assertDoesNotExist()
        composeTestRule.onNodeWithText(text(uiString.core_ui_sort_options)).assertDoesNotExist()
        composeTestRule.onNodeWithText(text(string.feature_appdetail_api_enable_all_of_this_page)).assertExists()
        composeTestRule.onNodeWithText(text(string.feature_appdetail_api_block_all_of_this_page)).performClick()

        assertThat(blocked to unblocked).isEqualTo(1 to 0)
    }

    @Test
    fun givenBatchRunning_whenTheMenuIsOpened_thenTheBatchActionsAreDisabled() {
        showContent(
            list = componentTab(),
            control = ControlUiState(batch = BatchProgress(ControlGoal.BLOCK, done = 200, total = 450)),
        )

        composeTestRule.onNodeWithContentDescription(text(uiString.core_ui_more_menu)).performClick()

        composeTestRule.onNodeWithText(text(string.feature_appdetail_api_block_all_of_this_page)).assertIsNotEnabled()
        composeTestRule.onNodeWithText(text(string.feature_appdetail_api_enable_all_of_this_page)).assertIsNotEnabled()
        composeTestRule.onNodeWithText(text(string.feature_appdetail_api_select_mutiple)).assertIsEnabled()
    }

    @Test
    fun givenBatchRunning_whenSelecting_thenTheSelectionCannotBeChanged() {
        showContent(
            list = componentTab().copy(isSelectionMode = true, selected = setOf(previewActivityRows.first().id)),
            control = ControlUiState(batch = BatchProgress(ControlGoal.BLOCK, done = null, total = 3)),
        )

        composeTestRule.onNodeWithContentDescription(text(uiString.core_ui_block_selected)).assertIsNotEnabled()
        composeTestRule.onNodeWithContentDescription(text(uiString.core_ui_enable_selected)).assertIsNotEnabled()
    }

    @Test
    fun givenHeaderNotLoadedYet_whenTheListIsLoaded_thenNotToldTheAppIsNotInstalled() {
        showContent(header = AppHeaderUiState(), list = componentTab())

        composeTestRule.onNodeWithText(text(string.feature_appdetail_api_not_installed)).assertDoesNotExist()
    }

    @Test
    fun givenAppNotCached_whenTheRefreshFailedForAnotherReason_thenThatReasonIsShown() {
        showContent(header = AppHeaderUiState(), list = componentTab().copy(refreshFailure = ReadFailure.UserNotReachable(WORK)))

        composeTestRule.onNodeWithText(text(string.feature_appdetail_api_not_installed)).assertDoesNotExist()
        composeTestRule.onNodeWithText(text(string.feature_appdetail_api_read_user_not_reachable)).assertIsDisplayed()
    }

    @Test
    fun givenAppNotCachedAndItsUserNotReachable_whenShown_thenAccessCanBeGrantedFromTheErrorScreen() {
        var granted = 0
        composeTestRule.setContent {
            BlockerTheme {
                AppDetailContent(
                    header = AppHeaderUiState(),
                    list = componentTab().copy(refreshFailure = ReadFailure.UserNotReachable(WORK)),
                    control = ControlUiState(),
                    actions = AppDetailActions(onGrantAccess = { granted++ }),
                )
            }
        }

        composeTestRule.onNodeWithText(text(string.feature_appdetail_api_grant_access)).performClick()

        assertThat(granted).isEqualTo(1)
    }

    @Test
    fun givenAccessCheckThatFailed_whenAppNotCached_thenSaysSoAndOffersAccess() {
        showContent(header = AppHeaderUiState(), list = componentTab().copy(accessCheckFailed = true))

        composeTestRule.onNodeWithText(text(string.feature_appdetail_api_access_not_checked)).assertIsDisplayed()
        composeTestRule.onNodeWithText(text(string.feature_appdetail_api_grant_access)).assertIsDisplayed()
    }

    @Test
    fun givenAppNotInstalled_whenShown_thenNoAccessIsOffered() {
        showContent(header = AppHeaderUiState(), list = componentTab().copy(refreshFailure = ReadFailure.PackageNotFound(WORK_APP)))

        composeTestRule.onNodeWithText(text(string.feature_appdetail_api_grant_access)).assertDoesNotExist()
    }

    @Test
    fun givenScreenStarted_whenTheAppGoesToTheBackgroundAndBack_thenStoppedAndRefreshedOnReturn() {
        val owner = TestLifecycleOwner(Lifecycle.State.RESUMED)
        var started = 0
        var stopped = 0
        var refreshed = 0
        composeTestRule.setContent {
            CompositionLocalProvider(LocalLifecycleOwner provides owner) {
                KeepFreshWhileStarted(
                    keepFresh = {
                        started++
                        try {
                            awaitCancellation()
                        } finally {
                            stopped++
                        }
                    },
                    refresh = { refreshed++ },
                )
            }
        }
        composeTestRule.runOnIdle { assertThat(Triple(started, stopped, refreshed)).isEqualTo(Triple(1, 0, 0)) }

        composeTestRule.runOnIdle { owner.currentState = Lifecycle.State.CREATED }
        composeTestRule.runOnIdle { assertThat(Triple(started, stopped, refreshed)).isEqualTo(Triple(1, 1, 0)) }

        composeTestRule.runOnIdle { owner.currentState = Lifecycle.State.RESUMED }
        composeTestRule.runOnIdle { assertThat(Triple(started, stopped, refreshed)).isEqualTo(Triple(2, 1, 1)) }
    }

    @Test
    fun givenScreenRecomposing_whenRowActionsAreTaken_thenTheSameObjectCallsTheLatestCallbacks() {
        val fixture = AppDetailFixture(UnconfinedTestDispatcher())
        val taken = mutableListOf<ComponentRowActions>()
        val opened = mutableListOf<Int>()
        var tick by mutableIntStateOf(0)
        composeTestRule.setContent {
            val current = tick
            Text("$current")
            taken += rememberRowActions(fixture.list, fixture.control, { opened += current }, {}, {})
        }

        composeTestRule.runOnIdle { tick++ }
        composeTestRule.runOnIdle { tick++ }
        composeTestRule.runOnIdle { taken.last().onClick(previewActivityRows.first()) }

        assertThat(taken.size).isAtLeast(3)
        assertThat(taken.distinct()).hasSize(1)
        assertThat(opened).containsExactly(2)
    }

    private fun showContent(
        header: AppHeaderUiState = AppHeaderUiState(app = previewHeaderApp),
        list: ComponentListUiState,
        control: ControlUiState = ControlUiState(),
        topBarActions: TopBarActions = TopBarActions(),
    ) {
        composeTestRule.setContent {
            BlockerTheme {
                AppDetailContent(header = header, list = list, control = control, topBarActions = topBarActions)
            }
        }
    }

    private fun componentTab() = ComponentListUiState(
        tabs = TabState(listOf(AppDetailTabs.Info, AppDetailTabs.Activity), AppDetailTabs.Activity, mapOf(AppDetailTabs.Activity to previewActivityRows.size)),
        components = ComponentsUiState.Loaded(previewComponents),
    )

    private fun sdkTab() = ComponentListUiState(
        tabs = TabState(listOf(AppDetailTabs.Info, AppDetailTabs.Activity, AppDetailTabs.Sdk), AppDetailTabs.Sdk, mapOf(AppDetailTabs.Activity to previewActivityRows.size, AppDetailTabs.Sdk to 1)),
        components = ComponentsUiState.Loaded(previewComponents),
        // Still loading, so no rule shows a menu of its own beside the top bar's.
        matchedRules = MatchedRulesUiState.Loading,
    )
}
