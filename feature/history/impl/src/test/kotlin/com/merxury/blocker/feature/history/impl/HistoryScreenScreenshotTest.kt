// Copyright 2026 Blocker
// SPDX-License-Identifier: Apache-2.0

package com.merxury.blocker.feature.history.impl

import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.merxury.blocker.core.designsystem.theme.BlockerTheme
import com.merxury.blocker.core.model.component.ComponentId
import com.merxury.blocker.core.model.component.ComponentKey
import com.merxury.blocker.core.model.component.UserId
import com.merxury.blocker.core.model.history.Operation
import com.merxury.blocker.core.model.history.OperationSource
import com.merxury.blocker.core.model.history.UndoResult
import com.merxury.blocker.core.testing.util.captureForDevice
import com.merxury.blocker.core.ui.coil.ApkIcon
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

    private val today = LocalDate.of(2026, 10, 23)
    private val app = ApkIcon("com.example.notes", null, 1, null)
    private val otherApp = ApkIcon("com.example.photos", null, 1, null)
    private val appChange = entry(app, "SyncService")
    private val appRow = HistoryOperation(
        operation = Operation(
            id = 4,
            timestamp = Instant.parse("2026-10-23T14:35:00Z"),
            source = OperationSource.APP_DETAIL,
            entryCount = 2,
            blockedCount = 2,
        ),
        headline = OperationHeadline(OperationSource.APP_DETAIL, "Example Notes"),
        appIcon = app,
    )
    private val searchRow = HistoryOperation(
        Operation(
            id = 1,
            timestamp = Instant.parse("2026-10-22T18:00:00Z"),
            source = OperationSource.SEARCH,
            undoneByOperationId = 3,
            entryCount = 4,
            blockedCount = 3,
            enabledCount = 1,
        ),
    )
    private val undoRow = HistoryOperation(
        operation = Operation(
            id = 3,
            timestamp = Instant.parse("2026-10-23T09:00:00Z"),
            source = OperationSource.UNDO,
            entryCount = 4,
        ),
        undoTarget = UndoTarget.Single(searchRow.headline),
    )
    private val list = HistoryUiState(
        zone = ZoneOffset.UTC,
        days = listOf(
            HistoryDay(today, listOf(appRow, undoRow)),
            HistoryDay(today.minusDays(1), listOf(searchRow)),
            HistoryDay(
                LocalDate.of(2025, 12, 10),
                listOf(
                    HistoryOperation(
                        Operation(
                            id = 2,
                            timestamp = Instant.parse("2025-12-10T11:00:00Z"),
                            source = OperationSource.RESET_PM,
                            entryCount = 1,
                        ),
                    ),
                ),
            ),
        ),
    )
    private val detail = HistoryOperationUiState(
        row = appRow,
        zone = ZoneOffset.UTC,
        groups = listOf(HistoryAppGroup(app, "Example Notes", listOf(appChange, entry(app, "WidgetReceiver")))),
    )

    @Test
    fun givenHistoryIsEmpty_whenRendered_thenMatchesScreenshot() {
        capture("HistoryEmpty") { HistoryScreen(HistoryUiState(days = emptyList()), today = today) }
    }

    @Test
    fun givenRecordedOperations_whenRendered_thenMatchesListScreenshot() {
        capture("HistoryList") { HistoryScreen(list, today = today) }
        listLabelsAreDisplayed()
    }

    @Test
    fun givenDarkTheme_whenHistoryIsRendered_thenMatchesListScreenshot() {
        capture("HistoryList", darkMode = true) { HistoryScreen(list, today = today) }
    }

    @Test
    fun givenSystemUses24HourTime_whenHistoryIsDisplayed_thenTimeFormatFollowsTheSetting() {
        Settings.System.putString(composeTestRule.activity.contentResolver, Settings.System.TIME_12_24, "24")
        composeTestRule.setContent { HistoryScreen(list, today = today) }

        composeTestRule.onNodeWithText("14:35").assertIsDisplayed()
    }

    @Test
    fun givenSingleAppOperation_whenOpened_thenMatchesDetailScreenshot() {
        captureDetail("HistoryDetailSingleApp", detail)
        composeTestRule.onNodeWithText("Example Notes").assertIsDisplayed()
        composeTestRule.onNodeWithText("SyncService").assertIsDisplayed()
    }

    @Test
    fun givenMultiAppOperation_whenOpened_thenShowsProfileLabelsInTheDetailScreenshot() {
        val workChange = entry(otherApp, "UploadService", ProfileLabel.Work, UserId(10)).copy(change = ComponentState.ON)
        val groups = listOf(
            HistoryAppGroup(app, "Example Notes", listOf(appChange.copy(profile = ProfileLabel.Personal))),
            HistoryAppGroup(otherApp, "Example Photos", listOf(workChange)),
        )
        captureDetail("HistoryDetailMultiApp", detail.copy(row = searchRow.copy(operation = searchRow.operation.copy(undoneByOperationId = null)), groups = groups))
        composeTestRule.onNodeWithText("Example Notes").assertIsDisplayed()
        composeTestRule.onNodeWithText("Example Photos").assertIsDisplayed()
        composeTestRule.onNodeWithText("Work").assertIsDisplayed()
    }

    @Test
    fun givenUndoneOperation_whenOpened_thenShowsStatusInTheDetailScreenshot() {
        val row = appRow.copy(operation = appRow.operation.copy(undoneByOperationId = undoRow.operation.id))
        captureDetail("HistoryDetailUndone", detail.copy(row = row, undoneAt = undoRow.operation.timestamp))
        composeTestRule.onNodeWithText("Undone · Today 9:00 AM").assertIsDisplayed()
        composeTestRule.onNodeWithText("Undo").assertDoesNotExist()
    }

    @Test
    fun givenLaterUndoRequested_whenRendered_thenMatchesConfirmationScreenshot() {
        captureDetail("HistoryDetailUndoConfirmation", detail.copy(laterCount = 2, confirmLater = true), allWindows = true)
        composeTestRule.onNodeWithText("Undo 3 operations?").assertIsDisplayed()
        composeTestRule.onNodeWithText("This operation and the 2 operations after it will be undone.").assertIsDisplayed()
    }

    @Test
    fun givenSomeComponentsCouldNotBeRestored_whenUndoFinishes_thenMessageCombinesTheCounts() {
        val result = HistoryMessage.Undone(UndoResult(restored = 1, alreadyAtTarget = 2, skipped = 1, failed = 1))
        composeTestRule.setContent { Text(result.toUiMessage().title) }

        composeTestRule.onNodeWithText("Restored 3 components. 2 could not be restored.").assertIsDisplayed()
    }

    @Test
    fun givenNoComponentsWereRestored_whenUndoFinishes_thenMessageShowsOnlyUnrestoredCount() {
        val result = HistoryMessage.Undone(UndoResult(restored = 0, alreadyAtTarget = 0, skipped = 0, failed = 1))
        composeTestRule.setContent { Text(result.toUiMessage().title) }

        composeTestRule.onNodeWithText("1 component could not be restored").assertIsDisplayed()
    }

    private fun listLabelsAreDisplayed() {
        listOf("Today", "Yesterday", "Dec 10, 2025", "Search · Restored 4 components", "3 off · 1 on · Undone").forEach {
            composeTestRule.onNodeWithText(it).assertIsDisplayed()
        }
    }

    private fun captureDetail(name: String, state: HistoryOperationUiState, allWindows: Boolean = false) = capture(name, allWindows = allWindows) { HistoryOperationScreen(state, today = today) }

    private fun capture(
        name: String,
        darkMode: Boolean = false,
        allWindows: Boolean = false,
        content: @Composable () -> Unit,
    ) {
        composeTestRule.captureForDevice(
            deviceName = if (darkMode) "phone_dark" else "phone",
            deviceSpec = "spec:shape=Normal,width=412,height=840,unit=dp,dpi=480",
            screenshotName = name,
            darkMode = darkMode,
            captureAllWindows = allWindows,
        ) { BlockerTheme { Surface(content = content) } }
    }

    private fun entry(app: ApkIcon, name: String, profile: ProfileLabel? = null, user: UserId = UserId.SYSTEM) = HistoryEntry(ComponentKey(ComponentId(app.packageName, "${app.packageName}.$name"), user), profile, ComponentState.OFF)
}
