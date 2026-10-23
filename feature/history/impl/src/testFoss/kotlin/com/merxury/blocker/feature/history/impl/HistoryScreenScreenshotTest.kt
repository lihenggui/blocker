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

package com.merxury.blocker.feature.history.impl

import androidx.activity.ComponentActivity
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.github.takahirom.roborazzi.captureRoboImage
import com.merxury.blocker.core.designsystem.theme.BlockerTheme
import com.merxury.blocker.core.model.ComponentType.SERVICE
import com.merxury.blocker.core.model.data.Operation
import com.merxury.blocker.core.model.data.OperationEntry
import com.merxury.blocker.core.model.data.OperationSource.APP_DETAIL
import com.merxury.blocker.core.model.data.OperationSource.RESET_IFW
import com.merxury.blocker.core.model.data.OperationSource.SEARCH
import com.merxury.blocker.core.model.data.OperationSource.UNDO
import com.merxury.blocker.core.testing.util.DefaultRoborazziOptions
import com.merxury.blocker.core.testing.util.DefaultTestDevices
import com.merxury.blocker.core.testing.util.captureForDevice
import com.merxury.blocker.core.testing.util.captureMultiDevice
import com.merxury.blocker.core.ui.LocalTimeZone
import dagger.hilt.android.testing.HiltTestApplication
import kotlinx.datetime.TimeZone
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.LooperMode
import java.util.Locale
import kotlin.test.assertEquals
import kotlin.time.Instant

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = HiltTestApplication::class, qualifiers = "en-rUS")
@LooperMode(LooperMode.Mode.PAUSED)
class HistoryScreenScreenshotTest {
    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()
    private val originalLocale = Locale.getDefault()
    private val originalTimeZone = java.util.TimeZone.getDefault()
    private val timestamp = Instant.parse("2026-10-23T12:00:00Z")
    private val operations = listOf(
        Operation(4, timestamp, APP_DETAIL, ref = "com.example.app", entryCount = 1),
        Operation(3, Instant.parse("2026-10-23T09:00:00Z"), RESET_IFW),
        Operation(2, Instant.parse("2026-10-22T18:00:00Z"), SEARCH, undoneByOperationId = 5, entryCount = 1),
    )
    private val entries = listOf(OperationEntry(4, "com.example.app", "com.example.app.SyncService", SERVICE, false, true, true, false))

    @Before
    fun setup() {
        Locale.setDefault(Locale.US)
        java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone("UTC"))
    }

    @After
    fun restoreDefaults() {
        Locale.setDefault(originalLocale)
        java.util.TimeZone.setDefault(originalTimeZone)
    }

    @Composable
    private fun ThemedHistory(content: @Composable () -> Unit) {
        CompositionLocalProvider(LocalTimeZone provides TimeZone.UTC) {
            BlockerTheme { Surface { content() } }
        }
    }

    @Test
    fun historyLoading() {
        composeTestRule.captureMultiDevice("HistoryLoading") {
            ThemedHistory { HistoryScreen(operations = null) }
        }
    }

    @Test
    fun historyEmpty() {
        composeTestRule.captureMultiDevice("HistoryEmpty") {
            ThemedHistory { HistoryScreen(operations = emptyList()) }
        }
    }

    @Test
    fun historyTimeline() {
        composeTestRule.captureMultiDevice("HistoryTimeline") {
            ThemedHistory { HistoryScreen(operations = operations) }
        }
    }

    @Test
    fun historyTimeline_dark() {
        composeTestRule.captureForDevice(
            deviceName = "phone_dark",
            deviceSpec = DefaultTestDevices.PHONE.spec,
            screenshotName = "HistoryTimeline",
            darkMode = true,
        ) { ThemedHistory { HistoryScreen(operations = operations) } }
    }

    @Test
    fun historyExpanded() {
        composeTestRule.captureMultiDevice("HistoryExpanded") {
            ThemedHistory { HistoryScreen(operations = operations, expandedOperationId = 4, entries = entries) }
        }
    }

    @Test
    fun historyUndoConfirmation() {
        RuntimeEnvironment.setQualifiers("w640dp-h360dp-480dpi")
        composeTestRule.setContent {
            ThemedHistory { HistoryScreen(operations = operations, undoRequest = UndoRequest.Since(timestamp, 2)) }
        }
        composeTestRule.onNode(isDialog()).captureRoboImage(
            "src/test/screenshots/HistoryUndoConfirmation_phone.png",
            roborazziOptions = DefaultRoborazziOptions,
        )
    }

    @Test
    fun resetUndoAndUndoneRows_hideUndoActions() {
        composeTestRule.setContent {
            ThemedHistory {
                HistoryScreen(operations = operations + Operation(5, timestamp, UNDO, entryCount = 1))
            }
        }
        composeTestRule.onAllNodesWithText("Undo this operation").assertCountEquals(1)
    }

    @Test
    fun expandedSnapshot_showsIndependentPmAndIfwChanges() {
        composeTestRule.setContent {
            ThemedHistory { HistoryScreen(operations = operations.take(1), expandedOperationId = 4, entries = entries) }
        }
        composeTestRule.onNodeWithText("PM: Allowed → Blocked").assertIsDisplayed()
        composeTestRule.onNodeWithText("IFW: Blocked → Allowed").assertIsDisplayed()
    }

    @Test
    fun dayAction_usesLocalMidnight() {
        var since: Instant? = null
        composeTestRule.setContent {
            CompositionLocalProvider(LocalTimeZone provides TimeZone.of("America/Los_Angeles")) {
                BlockerTheme {
                    Surface {
                        HistoryScreen(operations = operations.take(1), onUndoSince = { since = it })
                    }
                }
            }
        }
        composeTestRule.onNodeWithText("Oct 23, 2026").assertIsDisplayed()
        composeTestRule.onNodeWithText("Undo everything since this day").performClick()
        assertEquals(Instant.parse("2026-10-23T07:00:00Z"), since)
    }
}
