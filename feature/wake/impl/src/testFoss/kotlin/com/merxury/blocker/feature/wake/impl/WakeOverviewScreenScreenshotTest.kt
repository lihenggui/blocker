// Copyright 2026 Blocker
// SPDX-License-Identifier: Apache-2.0

package com.merxury.blocker.feature.wake.impl

import androidx.activity.ComponentActivity
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextReplacement
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import com.google.common.truth.Truth.assertThat
import com.merxury.blocker.core.designsystem.theme.BlockerTheme
import com.merxury.blocker.core.model.wake.WakeHeadline
import com.merxury.blocker.core.navigation.NavigationState
import com.merxury.blocker.core.navigation.Navigator
import com.merxury.blocker.core.testing.util.DefaultTestDevices
import com.merxury.blocker.core.testing.util.captureForDevice
import com.merxury.blocker.core.testing.util.captureMultiDevice
import com.merxury.blocker.feature.wake.api.R.string
import com.merxury.blocker.feature.wake.api.navigation.WakeAppNavKey
import com.merxury.blocker.feature.wake.api.navigation.WakeOverviewNavKey
import com.merxury.blocker.feature.wake.api.navigation.navigateToWakeApp
import dagger.hilt.android.testing.HiltTestApplication
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.LooperMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = HiltTestApplication::class, qualifiers = "en-rUS")
@LooperMode(LooperMode.Mode.PAUSED)
class WakeOverviewScreenScreenshotTest {

    @get:Rule val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private val ready = WakeOverviewUiState.Ready(
        keepAlive = listOf(
            WakeAppRow("example.mail", "Mail", null, WakeHeadline.RestartedAfterClose(4)),
            WakeAppRow("example.radio", "Radio", null, WakeHeadline.RanWithScreenOff(4_200_000_000_000)),
        ),
        attention = listOf(
            WakeAppRow("example.calendar", "Calendar", null, WakeHeadline.StartedByOthers(1, 3)),
        ),
        handledKeepAlive = 1,
        automaticBlocks = 2,
        otherApps = 68,
        checking = false,
    )

    @Test
    fun givenBackgroundOverview_whenAnAppIsTapped_thenTheAppOpensInTheBackgroundStack() {
        val overview = WakeOverviewNavKey
        val state = NavigationState(
            overview,
            NavBackStack(overview),
            mapOf(overview to NavBackStack<NavKey>(overview)),
        )
        val navigator = Navigator(state)
        val app = ready.keepAlive.first()
        composeTestRule.setContent {
            BlockerTheme {
                WakeOverviewScreen(ready, onGrantAccess = {}, onCheckNow = {}, onAppClick = navigator::navigateToWakeApp)
            }
        }

        composeTestRule.onNode(hasScrollAction()).performScrollToNode(hasText(app.label))
        composeTestRule.onNodeWithText(app.label).performClick()

        composeTestRule.runOnIdle {
            assertThat(state.currentTopLevelKey).isEqualTo(overview)
            assertThat(state.currentSubStack).containsExactly(overview, WakeAppNavKey(app.packageName)).inOrder()
            navigator.goBack()
            assertThat(state.currentKey).isEqualTo(overview)
        }
    }

    @Test
    fun givenFindingsAndAutomaticBlocks_whenReady_thenCaptureOverview() = capture("WakeOverviewReady", ready)

    @Test
    fun givenNoFindings_whenReady_thenCaptureCleanOverview() = capture("WakeOverviewClean", WakeOverviewUiState.Ready(emptyList(), emptyList(), 0, 0, 71, false))

    @Test
    fun givenNoHistory_whenCollecting_thenCaptureFirstRun() = capture("WakeOverviewCollecting", WakeOverviewUiState.Collecting(false))

    @Test
    fun givenInstalledAppsWithNoHistory_whenCollecting_thenCapturesTheCollapsedAppBrowser() {
        val state = WakeOverviewUiState.Collecting(
            checking = false,
            installedApps = listOf(WakeAppIdentity("example.mail", "Mail"), WakeAppIdentity("example.radio", "Radio")),
        )
        capturePortrait("WakeOverviewCollectingApps", state)
    }

    @Test
    fun givenAppsAcrossGroups_whenSearchingByName_thenCapturesInlineSearchAndMatchingStatuses() {
        val state = ready.copy(
            attention = ready.attention + WakeAppRow("example.mail.sync", "Mail Sync", null, null),
            blockedApps = listOf(WakeAppRow("example.mail.archive", "Mail Archive", null, null)),
            dormantApps = listOf(WakeAppIdentity("example.mail.reader", "Mail Reader")),
        )
        val search = composeTestRule.activity.getString(string.feature_wake_api_search_apps)
        capturePortrait("WakeOverviewSearch", state) {
            composeTestRule.onNodeWithContentDescription(search).performClick()
            composeTestRule.onNodeWithTag("BlockerSearchTextField").performTextReplacement("mail")
        }
    }

    @Test
    fun givenNoAccess_whenOpeningOverview_thenCaptureAccessAction() = capture("WakeOverviewNoAccess", WakeOverviewUiState.NoAccess)

    @Test
    fun givenSecondaryProfile_whenOpeningOverview_thenCaptureOwnerRequirement() = capture("WakeOverviewNotOwner", WakeOverviewUiState.NotOwnerProfile)

    @Test
    fun givenReadsRefused_whenOpeningOverview_thenCaptureUpdateRequirement() = capture("WakeOverviewReadsRefused", WakeOverviewUiState.ReadsRefused)

    private fun capture(name: String, state: WakeOverviewUiState) {
        composeTestRule.captureMultiDevice(name) { Overview(state) }
        composeTestRule.captureForDevice(
            deviceName = "phone_dark",
            deviceSpec = DefaultTestDevices.PHONE.spec,
            screenshotName = name,
            darkMode = true,
        ) { Overview(state) }
    }

    private fun capturePortrait(name: String, state: WakeOverviewUiState, beforeCapture: () -> Unit = {}) {
        for (dark in listOf(false, true)) {
            composeTestRule.captureForDevice(
                deviceName = if (dark) "portrait_dark" else "portrait",
                deviceSpec = PORTRAIT_PHONE,
                screenshotName = name,
                darkMode = dark,
                beforeCapture = beforeCapture,
            ) { key(dark) { Overview(state) } }
        }
    }

    @Composable
    private fun Overview(state: WakeOverviewUiState) {
        BlockerTheme {
            Surface {
                WakeOverviewScreen(state, onGrantAccess = {}, onCheckNow = {}, onAppClick = {})
            }
        }
    }

    private companion object {
        const val PORTRAIT_PHONE = "spec:shape=Normal,width=360,height=800,unit=dp,dpi=480"
    }
}
