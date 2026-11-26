// Copyright 2026 Blocker
// SPDX-License-Identifier: Apache-2.0

package com.merxury.blocker.feature.wake.impl

import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import com.merxury.blocker.core.designsystem.theme.BlockerTheme
import com.merxury.blocker.core.model.data.ComponentDetail
import com.merxury.blocker.core.model.system.BackgroundUsage
import com.merxury.blocker.core.model.system.DeepBackgroundRestriction
import com.merxury.blocker.core.model.wake.AppWakeState
import com.merxury.blocker.core.model.wake.JustifiedStarts
import com.merxury.blocker.core.model.wake.OwnWakeKind
import com.merxury.blocker.core.model.wake.WakeBlockMethod
import com.merxury.blocker.core.model.wake.WakeDirection
import com.merxury.blocker.core.model.wake.WakePathBlock
import com.merxury.blocker.core.model.wake.WakePeerLink
import com.merxury.blocker.core.model.wake.WakeVerdict
import com.merxury.blocker.core.testing.util.captureForDevice
import com.merxury.blocker.core.testing.util.captureMultiDevice
import dagger.hilt.android.testing.HiltTestApplication
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.LooperMode
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = HiltTestApplication::class, qualifiers = "en-rUS")
@LooperMode(LooperMode.Mode.PAUSED)
class WakeAppScreenScreenshotTest {

    @get:Rule val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private val apps = listOf(
        wakeTestApp(DETAIL_PACKAGE).copy(label = "Calendar"),
        wakeTestApp("example.notes").copy(label = "Notes"),
    )
    private val component = ComponentDetail("example.notes.SyncService", sdkName = "Sync SDK", disableEffect = "Stops syncing")
    private val lastWake = System.currentTimeMillis() - Duration.ofMinutes(10).plusSeconds(3).toMillis()
    private val base = detailState(
        peers = listOf(
            WakePeerLink(
                "example.notes",
                WakeDirection.MUTUAL,
                listOf(detailPath("in", actor = "example.notes", count = 4, last = lastWake, component = null)),
                listOf(detailPath("out", target = "example.notes", count = 2, last = lastWake, component = component.name, action = "example.notes.SYNC")),
            ),
        ),
        own = listOf(
            detailMethod(OwnWakeKind.RESTART_AFTER_CLOSE, detailPath("restart", count = 3, last = lastWake)),
            detailMethod(OwnWakeKind.SCREEN_OFF_RUN, detailPath("screen", screenOff = Duration.ofMinutes(28).toNanos(), method = WakeBlockMethod.NONE)),
        ),
    )

    @Test
    fun givenKeepAliveWithExpandedRelation_whenViewingDetails_thenCapturesPhoneFoldableAndTablet() {
        val state = state()
        val expanded = state.relations.first().key
        composeTestRule.captureMultiDevice("WakeAppKeepAlive") { Screen(state, expanded) }
        capture("WakeAppKeepAlive", state, expanded)
    }

    @Test
    fun givenAllPathsBlocked_whenViewingDetails_thenCapturesBlockedStatus() {
        val blocked = base.copy(
            allBlocked = true,
            paths = base.paths.mapValues { it.value.copy(block = WakePathBlock.USER, blockedAtMillis = lastWake + 1) },
        )
        capture("WakeAppBlocked", state(blocked).copy(background = BackgroundUsage(false, true)))
    }

    @Test
    fun givenSuspiciousActivity_whenViewingDetails_thenCapturesAttentionStatus() = capture("WakeAppAttention", state(base.copy(activity = base.activity.copy(verdict = WakeVerdict.SUSPICIOUS))))

    @Test
    fun givenNoActivity_whenViewingDetails_thenCapturesEmptyScreen() {
        val empty = detailState(verdict = WakeVerdict.NORMAL, normal = JustifiedStarts(0, 0, 0, 0))
        capture("WakeAppEmpty", state(empty.copy(activity = empty.activity.copy(backgroundStarts = 0))).copy(background = null))
    }

    @Test
    fun givenAllowedChoices_whenOpeningBlockSheet_thenCapturesConfirmation() {
        val entries = listOf(potentialEntry(), potentialEntry(name = "RefreshReceiver"))
        val state = buildWakeAppState(base, lastWake + 2, apps, mapOf(component.name to component), entries).copy(
            background = BackgroundUsage(true, true),
            deepRestriction = DeepBackgroundRestriction(false, false, false, changeable = true),
        )
        capture("WakeAppBlockSheet", state, sheet = true)
    }

    @Test
    fun givenPartialDeepRestriction_whenViewingWithoutWakeHistory_thenCapturesTheClearableSwitch() {
        val empty = detailState(verdict = WakeVerdict.NORMAL, normal = JustifiedStarts(0, 0, 0, 0))
        val state = state(empty.copy(activity = empty.activity.copy(backgroundStarts = 0))).copy(
            background = null,
            deepRestriction = DeepBackgroundRestriction(false, true, false, changeable = true),
        )
        capture("WakeAppDeepRestriction", state)
    }

    private fun state(app: AppWakeState = base) = buildWakeAppState(app, lastWake + 2, apps, mapOf(component.name to component)).copy(background = BackgroundUsage(true, true))

    private fun capture(name: String, state: WakeAppUiState, expanded: String? = null, sheet: Boolean = false) {
        for (dark in listOf(false, true)) {
            composeTestRule.captureForDevice(
                deviceName = if (dark) "portrait_dark" else "portrait",
                deviceSpec = PORTRAIT_PHONE,
                screenshotName = name,
                darkMode = dark,
                captureAllWindows = sheet,
            ) { Screen(state, expanded, sheet) }
        }
    }

    @Composable
    private fun Screen(state: WakeAppUiState, expanded: String? = null, sheet: Boolean = false) {
        BlockerTheme {
            WakeAppScreen(state, onBack = {}, onPeerClick = {}, initiallyExpanded = expanded, initiallyShowingSheet = sheet)
        }
    }

    private companion object {
        const val PORTRAIT_PHONE = "spec:shape=Normal,width=360,height=800,unit=dp,dpi=480"
    }
}
