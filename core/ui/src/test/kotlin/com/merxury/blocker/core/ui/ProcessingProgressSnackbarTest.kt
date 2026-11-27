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

package com.merxury.blocker.core.ui

import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.google.common.truth.Truth.assertThat
import dagger.hilt.android.testing.HiltTestApplication
import kotlinx.coroutines.test.StandardTestDispatcher
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.LooperMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [26, 35], application = HiltTestApplication::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
class ProcessingProgressSnackbarTest {
    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>(effectContext = StandardTestDispatcher())

    @Test
    fun givenVisibleProgress_whenCountChanges_thenUpdatesWithoutQueuingOldMessages() {
        val progress = mutableStateOf(ProcessingProgress(1, 100, isEnabling = false, isComplete = false))
        composeTestRule.setContent {
            MaterialTheme {
                ProcessingProgressSnackbar(progress.value, onDismiss = {})
            }
        }
        val initialMessage = composeTestRule.activity.getString(R.string.core_ui_disabling_component_hint, 1, 100)
        composeTestRule.onNodeWithText(initialMessage).assertIsDisplayed()
        val beforeUpdate = composeTestRule.mainClock.currentTime

        composeTestRule.runOnIdle { progress.value = progress.value.copy(current = 80) }

        val updatedMessage = composeTestRule.activity.getString(R.string.core_ui_disabling_component_hint, 80, 100)
        composeTestRule.onNodeWithText(updatedMessage).assertIsDisplayed()
        composeTestRule.onNodeWithText(initialMessage).assertDoesNotExist()
        assertThat(composeTestRule.mainClock.currentTime - beforeUpdate).isLessThan(1_000L)

        composeTestRule.runOnIdle { progress.value = progress.value.copy(current = 81, isEnabling = true) }

        val enablingMessage = composeTestRule.activity.getString(R.string.core_ui_enabling_component_hint, 81, 100)
        composeTestRule.onNodeWithText(enablingMessage).assertIsDisplayed()
        composeTestRule.onNodeWithText(updatedMessage).assertDoesNotExist()
    }

    @Test
    fun givenFailedComponents_whenOperationCompletes_thenShowsHowManyCouldNotBeChanged() {
        val progress = mutableStateOf(ProcessingProgress(9, 10, isEnabling = false, isComplete = false, failed = 2))
        composeTestRule.setContent {
            MaterialTheme {
                ProcessingProgressSnackbar(progress.value, onDismiss = {})
            }
        }

        composeTestRule.runOnIdle { progress.value = progress.value.copy(current = 10, isComplete = true) }

        val resources = composeTestRule.activity.resources
        val failedMessage = resources.getQuantityString(R.plurals.core_ui_operation_completed_with_failures, 2, 2)
        val completedMessage = resources.getString(R.string.core_ui_operation_completed)
        composeTestRule.onNodeWithText(failedMessage).assertIsDisplayed()
        composeTestRule.onNodeWithText(completedMessage).assertDoesNotExist()
    }

    @Test
    fun givenUnfinishedOperation_whenTimePasses_thenKeepsProgressUntilExplicitCompletion() {
        val progress = mutableStateOf<ProcessingProgress?>(ProcessingProgress(0, 0, isEnabling = false, isComplete = false))
        var dismissCount = 0
        composeTestRule.setContent {
            MaterialTheme {
                ProcessingProgressSnackbar(
                    progress.value,
                    onDismiss = {
                        dismissCount++
                        progress.value = null
                    },
                )
            }
        }
        composeTestRule.onNodeWithText(composeTestRule.activity.getString(R.string.core_ui_loading)).assertIsDisplayed()

        composeTestRule.runOnIdle { progress.value = ProcessingProgress(100, 100, isEnabling = false, isComplete = false) }

        val pendingMessage = composeTestRule.activity.getString(R.string.core_ui_disabling_component_hint, 100, 100)
        composeTestRule.onNodeWithText(pendingMessage).assertIsDisplayed()
        composeTestRule.mainClock.advanceTimeBy(5_000, ignoreFrameDuration = true)

        composeTestRule.onNodeWithText(pendingMessage).assertIsDisplayed()
        composeTestRule.onNodeWithText(composeTestRule.activity.getString(R.string.core_ui_operation_completed)).assertDoesNotExist()
        composeTestRule.runOnIdle { assertThat(dismissCount).isEqualTo(0) }

        composeTestRule.runOnIdle { progress.value = progress.value?.copy(isComplete = true) }

        composeTestRule.onNodeWithText(composeTestRule.activity.getString(R.string.core_ui_operation_completed)).assertIsDisplayed()
        composeTestRule.onNodeWithText(pendingMessage).assertDoesNotExist()
    }

    @Test
    fun givenConsecutiveCompletedBatches_whenCallbackChanges_thenDismissesLatestBatchOnItsOwnTimeout() {
        val progress = mutableStateOf(ProcessingProgress(0, 1, isEnabling = true, isComplete = false))
        val dismissals = mutableListOf<String>()
        val onDismiss = mutableStateOf<() -> Unit>({ dismissals.add("initial callback") })
        composeTestRule.setContent {
            MaterialTheme {
                ProcessingProgressSnackbar(progress.value, onDismiss = onDismiss.value)
            }
        }
        val resources = composeTestRule.activity.resources
        val initialMessage = resources.getString(R.string.core_ui_enabling_component_hint, 0, 1)
        composeTestRule.onNodeWithText(initialMessage).assertIsDisplayed()

        composeTestRule.runOnIdle {
            progress.value = ProcessingProgress(1, 1, isEnabling = true, isComplete = true)
        }
        composeTestRule.onNodeWithText(resources.getString(R.string.core_ui_operation_completed)).assertIsDisplayed()
        composeTestRule.mainClock.advanceTimeBy(1_000, ignoreFrameDuration = true)
        composeTestRule.runOnIdle { assertThat(dismissals).isEmpty() }

        composeTestRule.runOnIdle {
            progress.value = ProcessingProgress(2, 2, isEnabling = false, isComplete = true, failed = 1)
        }
        val failedMessage = resources.getQuantityString(R.plurals.core_ui_operation_completed_with_failures, 1, 1)
        composeTestRule.onNodeWithText(failedMessage).assertIsDisplayed()
        composeTestRule.mainClock.advanceTimeBy(1_500, ignoreFrameDuration = true)
        composeTestRule.runOnIdle { assertThat(dismissals).isEmpty() }

        composeTestRule.runOnIdle { onDismiss.value = { dismissals.add("latest callback") } }
        composeTestRule.waitForIdle()
        composeTestRule.mainClock.advanceTimeBy(2_000, ignoreFrameDuration = true)
        composeTestRule.runOnIdle { assertThat(dismissals).isEmpty() }

        composeTestRule.mainClock.advanceTimeBy(600, ignoreFrameDuration = true)
        composeTestRule.runOnIdle { assertThat(dismissals).containsExactly("latest callback") }
        composeTestRule.onNodeWithText(failedMessage).assertDoesNotExist()
    }
}
