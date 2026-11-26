// Copyright 2026 Blocker
// SPDX-License-Identifier: Apache-2.0

package com.merxury.blocker.feature.wake.impl

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.google.common.truth.Truth.assertThat
import com.merxury.blocker.core.designsystem.theme.BlockerTheme
import com.merxury.blocker.feature.wake.api.R.plurals
import dagger.hilt.android.testing.HiltTestApplication
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class)
class WakeOverviewScreenTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

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
}
