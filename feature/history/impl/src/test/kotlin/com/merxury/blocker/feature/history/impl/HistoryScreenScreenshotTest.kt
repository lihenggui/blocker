// Copyright 2026 Blocker
// SPDX-License-Identifier: Apache-2.0

package com.merxury.blocker.feature.history.impl

import androidx.activity.ComponentActivity
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import com.merxury.blocker.core.designsystem.theme.BlockerTheme
import com.merxury.blocker.core.model.history.Operation
import com.merxury.blocker.core.model.history.OperationSource
import com.merxury.blocker.core.testing.util.DefaultTestDevices
import com.merxury.blocker.core.testing.util.captureForDevice
import com.merxury.blocker.core.testing.util.captureMultiDevice
import com.merxury.blocker.core.ui.profile.ProfileLabel
import dagger.hilt.android.testing.HiltTestApplication
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.LooperMode
import java.time.LocalDate
import java.time.ZoneOffset
import kotlin.time.Instant

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = HiltTestApplication::class, qualifiers = "en-rUS")
@LooperMode(LooperMode.Mode.PAUSED)
class HistoryScreenScreenshotTest {
    @get:Rule val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private val day = LocalDate.of(2026, 10, 23)
    private val timeline = HistoryUiState(
        zone = ZoneOffset.UTC,
        days = listOf(
            HistoryDay(
                date = day,
                operations = listOf(
                    HistoryOperation(operation(4, "2026-10-23T14:35:00Z", OperationSource.APP_DETAIL), "Example App"),
                    HistoryOperation(operation(3, "2026-10-23T09:00:00Z", OperationSource.RESET_PM)),
                    HistoryOperation(operation(2, "2026-10-23T08:00:00Z", OperationSource.UNDO).copy(ref = "1")),
                ),
                canUndoSince = true,
            ),
            HistoryDay(
                date = day.minusDays(1),
                operations = listOf(
                    HistoryOperation(operation(1, "2026-10-22T18:00:00Z", OperationSource.SEARCH).copy(undoneByOperationId = 2)),
                ),
                canUndoSince = true,
            ),
        ),
    )
    private val change = HistoryEntry("SyncService", "Example App", null, ComponentState.ON, ComponentState.OFF)

    @Test
    fun givenHistoryIsLoading_whenRendered_thenMatchesScreenshot() {
        composeTestRule.captureMultiDevice("HistoryLoading") { ThemedHistory(HistoryUiState()) }
    }

    @Test
    fun givenHistoryIsEmpty_whenRendered_thenMatchesScreenshot() {
        composeTestRule.captureMultiDevice("HistoryEmpty") { ThemedHistory(HistoryUiState(days = emptyList())) }
    }

    @Test
    fun givenRecordedOperations_whenRendered_thenMatchesTimelineScreenshots() {
        composeTestRule.captureMultiDevice("HistoryTimeline") { ThemedHistory(timeline) }
    }

    @Test
    fun givenDarkMode_whenTimelineIsRendered_thenMatchesScreenshot() {
        composeTestRule.captureForDevice(
            deviceName = "phone_dark",
            deviceSpec = DefaultTestDevices.PHONE.spec,
            screenshotName = "HistoryTimeline",
            darkMode = true,
        ) { ThemedHistory(timeline) }
    }

    @Test
    fun givenExpandedOperation_whenRendered_thenMatchesComponentChangeScreenshot() {
        capturePhone("HistoryExpanded", timeline.copy(expanded = ExpandedOperation(4, listOf(change))))
    }

    @Test
    fun givenDayUndoRequested_whenRendered_thenMatchesConfirmationScreenshot() {
        capturePhone(
            name = "HistoryUndoConfirmation",
            state = timeline.copy(confirmation = UndoRequest.Since(day, Instant.parse("2026-10-23T00:00:00Z"))),
            captureAllWindows = true,
        )
    }

    @Test
    fun givenComponentsFromDifferentProfiles_whenRendered_thenMatchesMultiUserScreenshot() {
        val entries = listOf(
            change.copy(profile = ProfileLabel.Personal),
            change.copy(profile = ProfileLabel.Work, before = ComponentState.OFF, after = ComponentState.ON),
        )
        val days = checkNotNull(timeline.days)
        val expandedRow = days.first().operations.first()
        val expandedDay = days.first().copy(
            operations = listOf(expandedRow.copy(operation = expandedRow.operation.copy(entryCount = entries.size))),
        )
        capturePhone(
            "HistoryMultiUser",
            timeline.copy(days = listOf(expandedDay), expanded = ExpandedOperation(expandedRow.operation.id, entries)),
        )
    }

    private fun capturePhone(name: String, state: HistoryUiState, captureAllWindows: Boolean = false) {
        composeTestRule.captureForDevice(
            deviceName = "phone",
            deviceSpec = "spec:shape=Normal,width=412,height=840,unit=dp,dpi=480",
            screenshotName = name,
            captureAllWindows = captureAllWindows,
        ) { ThemedHistory(state) }
    }

    @Composable
    private fun ThemedHistory(state: HistoryUiState) {
        BlockerTheme { Surface { HistoryScreen(state) } }
    }

    private fun operation(id: Long, timestamp: String, source: OperationSource) = Operation(
        id = id,
        timestamp = Instant.parse(timestamp),
        source = source,
        entryCount = 1,
    )
}
