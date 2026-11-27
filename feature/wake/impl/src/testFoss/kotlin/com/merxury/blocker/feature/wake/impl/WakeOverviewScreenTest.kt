// Copyright 2026 Blocker
// SPDX-License-Identifier: Apache-2.0

package com.merxury.blocker.feature.wake.impl

import androidx.activity.ComponentActivity
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextReplacement
import com.google.common.truth.Truth.assertThat
import com.merxury.blocker.core.designsystem.theme.BlockerTheme
import com.merxury.blocker.feature.wake.api.R.plurals
import com.merxury.blocker.feature.wake.api.R.string
import dagger.hilt.android.testing.HiltTestApplication
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import com.merxury.blocker.core.designsystem.R.string as designString

@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class)
class WakeOverviewScreenTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun givenReadyWithApps_whenSearchingThenClosingViaBack_thenFiltersOpensAppsAndClearsTheQuery() {
        val resources = compose.activity.resources
        val search = resources.getString(string.feature_wake_api_search_apps)
        val empty = resources.getString(string.feature_wake_api_search_empty)
        val clear = resources.getString(designString.core_designsystem_clear_search_text_content_desc)
        val back = resources.getString(designString.core_designsystem_back)
        val dispatcher = compose.activity.onBackPressedDispatcher
        val ready = searchableOverview()
        val opened = mutableListOf<String>()
        compose.setContent {
            BlockerTheme {
                WakeOverviewScreen(ready, onGrantAccess = {}, onCheckNow = {}, onAppClick = { opened += it })
            }
        }

        val field = compose.onNodeWithTag("BlockerSearchTextField")
        field.assertDoesNotExist()
        compose.onNodeWithContentDescription(search).performClick()
        compose.onNodeWithText("Mail").assertExists()
        compose.onNodeWithText("Notes").assertExists()
        field.performTextReplacement(" EXAMPLE.NOTES ")
        compose.onNodeWithText("Mail").assertDoesNotExist()
        compose.onNodeWithText("example.notes").assertExists().performClick()
        field.performTextReplacement("no match")
        compose.onNodeWithText(empty).assertExists()
        compose.onNodeWithContentDescription(clear).performClick()
        compose.onNodeWithText("Mail").assertExists()
        field.performTextReplacement("Notes")

        compose.runOnIdle { dispatcher.onBackPressed() }
        field.assertDoesNotExist()
        compose.onNodeWithText("Mail").assertExists()
        compose.onNodeWithContentDescription(search).performClick()
        compose.onNodeWithText("Mail").assertExists()
        compose.onNodeWithContentDescription(back).performClick()
        field.assertDoesNotExist()
        compose.runOnIdle { assertThat(opened).containsExactly("example.notes") }
    }

    @Test
    fun givenFilteredSearch_whenRestoredThenAvailabilityChanges_thenRetainsTheQueryUntilLeavingReady() {
        val restoration = StateRestorationTester(compose)
        val search = compose.activity.getString(string.feature_wake_api_search_apps)
        val ready = searchableOverview()
        val state = mutableStateOf<WakeOverviewUiState>(ready)
        restoration.setContent {
            BlockerTheme {
                WakeOverviewScreen(state.value, onGrantAccess = {}, onCheckNow = {}, onAppClick = {})
            }
        }
        compose.onNodeWithContentDescription(search).performClick()
        compose.onNodeWithTag("BlockerSearchTextField").performTextReplacement("Notes")
        restoration.emulateSavedInstanceStateRestore()

        compose.onNodeWithText("example.notes").assertExists()
        compose.onNodeWithText("Mail").assertDoesNotExist()
        compose.runOnIdle { state.value = WakeOverviewUiState.Collecting(false) }
        compose.onNodeWithTag("BlockerSearchTextField").assertDoesNotExist()
        compose.runOnIdle { state.value = ready }
        compose.onNodeWithContentDescription(search).performClick()
        compose.onNodeWithText("Mail").assertExists()
    }

    @Test
    fun givenCollectingWithInstalledApps_whenBrowsingThenTappingAnApp_thenOpensItsDetails() {
        val state = WakeOverviewUiState.Collecting(false, listOf(WakeAppIdentity("example.notes", "Notes")))
        val browse = compose.activity.getString(string.feature_wake_api_browse_installed_apps)
        val search = compose.activity.getString(string.feature_wake_api_search_apps)
        var opened: String? = null
        compose.setContent {
            BlockerTheme {
                WakeOverviewScreen(state, onGrantAccess = {}, onCheckNow = {}, onAppClick = { opened = it })
            }
        }

        compose.onNodeWithContentDescription(search).assertDoesNotExist()
        compose.onNodeWithText("Notes").assertDoesNotExist()
        compose.onNode(hasScrollAction()).performScrollToNode(hasText(browse))
        compose.onNodeWithText(browse).performClick()
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("Notes"))
        compose.onNodeWithText("Notes").performClick()
        compose.runOnIdle { assertThat(opened).isEqualTo("example.notes") }
    }

    @Test
    fun givenBlockedAndDormantApps_whenExpandingTheirGroups_thenBothRowsOpenTheCorrespondingApp() {
        val state = WakeOverviewUiState.Ready(
            keepAlive = emptyList(),
            attention = emptyList(),
            handledKeepAlive = 0,
            automaticBlocks = 0,
            otherApps = 1,
            checking = false,
            blockedApps = listOf(WakeAppRow("example.blocked", "Blocked app", null, null)),
            dormantApps = listOf(WakeAppIdentity("example.dormant", "Dormant app")),
        )
        val resources = compose.activity.resources
        val blocked = resources.getQuantityString(plurals.feature_wake_api_section_blocked_apps, 1, 1)
        val dormant = resources.getQuantityString(plurals.feature_wake_api_other_apps, 1, 1)
        val opened = mutableListOf<String>()
        compose.setContent {
            BlockerTheme {
                WakeOverviewScreen(state, onGrantAccess = {}, onCheckNow = {}, onAppClick = { opened += it })
            }
        }

        compose.onNodeWithText("Blocked app").assertDoesNotExist()
        compose.onNodeWithText("Dormant app").assertDoesNotExist()
        compose.onNodeWithText(blocked).performClick()
        compose.onNodeWithText("Blocked app").performClick()
        compose.onNodeWithText(blocked).performClick()
        compose.onNodeWithText(dormant).performClick()
        compose.onNodeWithText("Dormant app").performClick()

        compose.runOnIdle { assertThat(opened).containsExactly("example.blocked", "example.dormant").inOrder() }
    }

    private fun searchableOverview() = WakeOverviewUiState.Ready(
        keepAlive = listOf(WakeAppRow("example.mail", "Mail", null, null)),
        attention = emptyList(),
        handledKeepAlive = 0,
        automaticBlocks = 0,
        otherApps = 1,
        checking = false,
        dormantApps = listOf(WakeAppIdentity("example.notes", "Notes")),
    )
}
