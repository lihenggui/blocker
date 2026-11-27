// Copyright 2026 Blocker
// SPDX-License-Identifier: Apache-2.0

package com.merxury.blocker.feature.wake.impl

import android.content.Intent
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.lifecycle.Lifecycle
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import com.google.common.truth.Truth.assertThat
import com.merxury.blocker.core.designsystem.theme.BlockerTheme
import com.merxury.blocker.core.model.system.DeepBackgroundRestriction
import com.merxury.blocker.core.model.wake.JustifiedStarts
import com.merxury.blocker.core.model.wake.OwnWakeKind
import com.merxury.blocker.core.model.wake.PotentialWakeCategory
import com.merxury.blocker.core.model.wake.WakeAppEnvironment
import com.merxury.blocker.core.model.wake.WakeSpecialAccess
import com.merxury.blocker.core.navigation.NavigationState
import com.merxury.blocker.core.navigation.Navigator
import com.merxury.blocker.feature.wake.api.R
import com.merxury.blocker.feature.wake.api.navigation.WakeAppNavKey
import com.merxury.blocker.feature.wake.api.navigation.WakeOverviewNavKey
import dagger.hilt.android.testing.HiltTestApplication
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class)
class WakeAppRouteTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun givenWakeDetail_whenActivityResumesAgain_thenRefreshesOnlyWhenActive() {
        val state = MutableStateFlow(WakeAppUiState(WakeAppIdentity(DETAIL_PACKAGE)))
        val viewModel = mock<WakeAppViewModel> { on { uiState } doReturn state }
        val active = mutableStateOf(true)
        compose.setContent {
            BlockerTheme {
                WakeAppRoute(viewModel, active = active.value, onBack = {}, onPeerClick = {}, onComponents = {})
            }
        }

        compose.waitForIdle()
        verify(viewModel, never()).refreshRestrictions()

        val scenario = compose.activityRule.scenario
        scenario.moveToState(Lifecycle.State.STARTED)
        scenario.moveToState(Lifecycle.State.RESUMED)
        compose.waitForIdle()
        verify(viewModel).refreshRestrictions()

        compose.runOnIdle { active.value = false }
        compose.waitForIdle()
        scenario.moveToState(Lifecycle.State.STARTED)
        scenario.moveToState(Lifecycle.State.RESUMED)
        compose.waitForIdle()
        verify(viewModel).refreshRestrictions()
    }

    @Test
    fun givenDetailKeptInComposition_whenOpeningPeerAndGoingBack_thenExpiresPreviousUndo() {
        val overview = WakeOverviewNavKey
        val app = WakeAppNavKey(DETAIL_PACKAGE)
        val peer = WakeAppNavKey("example.peer")
        val navigator = Navigator(
            NavigationState(overview, NavBackStack(overview), mapOf(overview to NavBackStack<NavKey>(overview, app))),
        )
        val viewModel = mock<WakeAppViewModel> {
            on { uiState } doReturn MutableStateFlow(WakeAppUiState(WakeAppIdentity(DETAIL_PACKAGE)))
        }
        compose.setContent {
            BlockerTheme {
                WakeAppRoute(
                    viewModel = viewModel,
                    active = navigator.isCurrent(app),
                    onBack = navigator::goBack,
                    onPeerClick = {},
                    onComponents = {},
                )
            }
        }

        compose.runOnIdle { navigator.navigate(peer) }
        compose.waitForIdle()
        verify(viewModel).leaveScreen()

        compose.runOnIdle { navigator.goBack() }
        compose.waitForIdle()
        verify(viewModel).leaveScreen()
    }

    @Test
    fun givenNormalStarts_whenExpandingAndChoosingViewComponents_thenOpensComponents() {
        val state = WakeAppUiState(
            WakeAppIdentity(DETAIL_PACKAGE),
            normal = JustifiedStarts(0, 0, 1, 0),
            loaded = true,
        )
        val resources = compose.activity.resources
        val normalStarts = resources.getQuantityString(R.plurals.feature_wake_api_normal_starts, 1, 1)
        val viewComponents = resources.getString(R.string.feature_wake_api_view_components)
        var openedComponents = false
        compose.setContent {
            BlockerTheme {
                WakeAppScreen(state, onBack = {}, onPeerClick = {}, onComponents = { openedComponents = true })
            }
        }

        compose.onNodeWithText(viewComponents).assertDoesNotExist()
        compose.onNodeWithText(normalStarts).performClick()
        compose.onNodeWithText(viewComponents).performClick()

        compose.runOnIdle { assertThat(openedComponents).isTrue() }
    }

    @Test
    fun givenDormantEntry_whenExpandingThenToggling_thenShowsFactsAndRoutesSwitch() {
        val entry = potentialEntry(reEnabledByApp = true)
        val state = buildWakeAppState(detailState(normal = JustifiedStarts(0, 0, 0, 0)), null, emptyList(), emptyMap(), listOf(entry))
        val resources = compose.activity.resources
        val header = resources.getQuantityString(R.plurals.feature_wake_api_potential_entries, 1, 1)
        val title = resources.getString(R.string.feature_wake_api_potential_boot)
        val warning = resources.getString(R.string.feature_wake_api_re_enabled_by_app)
        val choice = resources.getString(R.string.feature_wake_api_choice_with_component, title, "BootReceiver")
        val switch = resources.getString(R.string.feature_wake_api_allow_path, choice)
        var toggled: Pair<String, Boolean>? = null
        compose.setContent {
            BlockerTheme {
                WakeAppScreen(state, onBack = {}, onPeerClick = {}, onToggle = { key, allowed -> toggled = key to allowed })
            }
        }

        compose.onNodeWithText(title).assertDoesNotExist()
        compose.onNodeWithText(header).performClick()
        compose.onNodeWithText(warning).assertExists()
        compose.onNodeWithText("BOOT_COMPLETED").assertDoesNotExist()
        compose.onNodeWithText(title).performClick()
        compose.onNodeWithText("BOOT_COMPLETED").assertExists()
        compose.onNodeWithContentDescription(switch).assertIsOn().performClick()

        compose.runOnIdle { assertThat(toggled).isEqualTo(state.potential.single().key to false) }
    }

    @Test
    fun givenLiveDeepRestrictionAndExemptions_whenViewingApp_thenShowsReadbackAndBypassFactors() {
        val resources = compose.activity.resources
        val deep = resources.getString(R.string.feature_wake_api_deep_restriction)
        val battery = resources.getString(R.string.feature_wake_api_battery_allowlist)
        val accessibility = resources.getString(R.string.feature_wake_api_accessibility)
        val state = MutableStateFlow(WakeAppUiState(WakeAppIdentity(DETAIL_PACKAGE), loaded = true))
        val viewModel = mock<WakeAppViewModel> { on { uiState } doReturn state }
        compose.setContent {
            BlockerTheme {
                WakeAppRoute(viewModel, active = true, onBack = {}, onPeerClick = {}, onComponents = {})
            }
        }

        compose.onNodeWithText(deep).assertDoesNotExist()
        compose.onNodeWithText(battery, substring = true).assertDoesNotExist()
        compose.runOnIdle {
            state.value = state.value.copy(
                deepRestriction = DeepBackgroundRestriction(false, true, false, changeable = true),
                environment = WakeAppEnvironment(systemIdleAllowlisted = true, specialAccess = setOf(WakeSpecialAccess.ACCESSIBILITY)),
            )
        }

        compose.onNodeWithText(battery, substring = true).assertExists()
        compose.onNodeWithText(accessibility, substring = true).assertExists()
        compose.onNodeWithContentDescription(deep).assertIsOn().performClick()
        verify(viewModel).setDeepRestricted(false)

        compose.runOnIdle { state.value = state.value.copy(busy = true) }
        compose.onNodeWithContentDescription(deep).assertIsNotEnabled()
    }

    @Test
    fun givenExemptions_whenTappingTheCard_thenOpensAndroidSettingsForThatApp() {
        val activity = compose.activity
        val state = WakeAppUiState(
            WakeAppIdentity(DETAIL_PACKAGE),
            environment = WakeAppEnvironment(userIdleAllowlisted = true),
            loaded = true,
        )
        val viewModel = mock<WakeAppViewModel> { on { uiState } doReturn MutableStateFlow(state) }
        compose.setContent {
            BlockerTheme {
                WakeAppRoute(viewModel, active = true, onBack = {}, onPeerClick = {}, onComponents = {})
            }
        }

        val card = compose.onNodeWithText(activity.getString(R.string.feature_wake_api_environment))
        assertThat(card.fetchSemanticsNode().config[SemanticsActions.OnClick].label)
            .isEqualTo(activity.getString(R.string.feature_wake_api_environment_manage))
        card.performClick()

        val intent = shadowOf(activity).nextStartedActivity
        assertThat(intent.action).isEqualTo(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
        assertThat(intent.dataString).isEqualTo("package:$DETAIL_PACKAGE")
        assertThat(intent.flags and Intent.FLAG_ACTIVITY_NEW_TASK).isNotEqualTo(0)
    }

    @Test
    fun givenObservedDormantAndSystemChoices_whenOpeningSheet_thenShowsGroupsAndSafeDefaults() {
        val resources = compose.activity.resources
        val entries = listOf(potentialEntry(), potentialEntry(PotentialWakeCategory.PUSH_RECEIVER, name = "PushReceiver"))
        val observed = detailState(own = listOf(detailMethod(OwnWakeKind.SELF_START, detailPath("observed"))))
        val state = buildWakeAppState(observed, null, emptyList(), emptyMap(), entries)
        compose.setContent {
            BlockerTheme {
                WakeAppScreen(state, onBack = {}, onPeerClick = {}, initiallyShowingSheet = true)
            }
        }

        compose.onNodeWithText(resources.getString(R.string.feature_wake_api_sheet_observed)).assertExists()
        compose.onNodeWithText(resources.getString(R.string.feature_wake_api_sheet_dormant)).assertExists()
        compose.onNodeWithText(resources.getString(R.string.feature_wake_api_sheet_system)).assertExists()
        compose.onNode(hasText(state.choices.first().title.resolve(resources)) and isToggleable()).assertIsOn()
        compose.onNodeWithText(state.choices[1].title.resolve(resources)).assertIsOn()
        compose.onNodeWithText(state.choices[2].title.resolve(resources)).assertIsOff()
        compose.onNodeWithText(resources.getString(R.string.feature_wake_api_sheet_force_stop)).assertIsOff()
        compose.onNodeWithText(resources.getString(R.string.feature_wake_api_sheet_push_warning)).assertExists()
        compose.onNodeWithText(resources.getString(R.string.feature_wake_api_sheet_force_stop_hint)).assertExists()
    }

    @Test
    fun givenBlockSheet_whenOnlyForceStopIsSelected_thenBlockIsDisabled() {
        val resources = compose.activity.resources
        val category = resources.getString(R.string.feature_wake_api_potential_boot)
        val boot = resources.getString(R.string.feature_wake_api_choice_with_component, category, "BootReceiver")
        val forceStop = resources.getString(R.string.feature_wake_api_sheet_force_stop)
        val block = resources.getString(R.string.feature_wake_api_block)
        val state = buildWakeAppState(detailState(), null, emptyList(), emptyMap(), listOf(potentialEntry()))
        var confirmed: Set<String>? = null
        compose.setContent {
            BlockerTheme {
                WakeAppScreen(state, onBack = {}, onPeerClick = {}, onBlock = { confirmed = it }, initiallyShowingSheet = true)
            }
        }

        compose.onNodeWithText(forceStop).assertIsOff()
        compose.onNodeWithText(block).assertIsEnabled()
        compose.onNodeWithText(forceStop).performClick()
        compose.onNodeWithText(boot).performClick()
        compose.onNodeWithText(block).assertIsNotEnabled()
        compose.onNodeWithText(forceStop).performClick()
        compose.onNodeWithText(boot).performClick()
        compose.onNodeWithText(block).performClick()

        compose.runOnIdle { assertThat(confirmed).containsExactly(state.potential.single().key) }
    }

    @Test
    fun givenOpenSheet_whenNewChoicesArrive_thenTheyStayUncheckedUntilSelected() {
        val resources = compose.activity.resources
        val initial = WakeSheetChoice("first", WakeText.Literal("First receiver"))
        val added = WakeSheetChoice("second", WakeText.Literal("Second receiver"))
        val forceStop = WakeSheetChoice(FORCE_STOP_CHOICE, WakeText.Literal("Force stop"), WakeChoiceGroup.SYSTEM, checkedByDefault = false)
        val choices = MutableStateFlow(listOf(initial, forceStop))
        val block = resources.getString(R.string.feature_wake_api_block)
        var confirmed: Set<String>? = null
        compose.setContent {
            val current = choices.collectAsState()
            BlockerTheme { WakeBlockSheet("App", current.value, onDismiss = {}, onConfirm = { confirmed = it }) }
        }
        compose.onNodeWithText("Force stop").performClick()
        compose.runOnIdle { choices.value = listOf(forceStop) }

        compose.onNodeWithText("First receiver").assertDoesNotExist()
        compose.onNodeWithText(block).assertIsNotEnabled()
        compose.runOnIdle { choices.value = listOf(added, forceStop) }

        compose.onNodeWithText("Second receiver").assertIsOff()
        compose.onNodeWithText("Force stop").assertIsOn()
        compose.onNodeWithText(block).assertIsNotEnabled()
        compose.onNodeWithText("Second receiver").performClick()
        compose.onNodeWithText(block).performClick()
        compose.runOnIdle { assertThat(confirmed).containsExactly(added.key, forceStop.key) }
    }

    @Test
    fun givenEditedSheetChoices_whenRestoringSavedComposition_thenUncheckedAndCheckedChoicesArePreserved() {
        val restoration = StateRestorationTester(compose)
        val receiver = WakeSheetChoice("receiver", WakeText.Literal("Boot receiver"))
        val forceStop = WakeSheetChoice(FORCE_STOP_CHOICE, WakeText.Literal("Force stop"), WakeChoiceGroup.SYSTEM, checkedByDefault = false)
        val choices = MutableStateFlow(listOf(receiver, forceStop))
        val block = compose.activity.resources.getString(R.string.feature_wake_api_block)
        restoration.setContent {
            val current = choices.collectAsState()
            BlockerTheme { WakeBlockSheet("App", current.value, onDismiss = {}, onConfirm = {}) }
        }
        compose.onNodeWithText("Boot receiver").assertIsOn().performClick()
        compose.onNodeWithText("Force stop").assertIsOff().performClick()

        restoration.emulateSavedInstanceStateRestore()
        compose.runOnIdle {
            choices.value += WakeSheetChoice("new", WakeText.Literal("New receiver"))
        }

        compose.onNodeWithText("Boot receiver").assertIsOff()
        compose.onNodeWithText("Force stop").assertIsOn()
        compose.onNodeWithText("New receiver").assertIsOff()
        compose.onNodeWithText(block).assertIsNotEnabled()
    }
}
