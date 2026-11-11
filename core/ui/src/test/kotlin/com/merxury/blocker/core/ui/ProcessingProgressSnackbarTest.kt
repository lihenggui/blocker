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
import dagger.hilt.android.testing.HiltTestApplication
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.LooperMode
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [26, 35], application = HiltTestApplication::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
class ProcessingProgressSnackbarTest {
    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

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
        assertTrue(composeTestRule.mainClock.currentTime - beforeUpdate < 1_000)

        composeTestRule.runOnIdle { progress.value = progress.value.copy(current = 81, isEnabling = true) }

        val enablingMessage = composeTestRule.activity.getString(R.string.core_ui_enabling_component_hint, 81, 100)
        composeTestRule.onNodeWithText(enablingMessage).assertIsDisplayed()
        composeTestRule.onNodeWithText(updatedMessage).assertDoesNotExist()
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
        composeTestRule.mainClock.autoAdvance = false
        composeTestRule.mainClock.advanceTimeBy(5_000)

        composeTestRule.onNodeWithText(pendingMessage).assertIsDisplayed()
        composeTestRule.onNodeWithText(composeTestRule.activity.getString(R.string.core_ui_operation_completed)).assertDoesNotExist()
        assertEquals(0, dismissCount)

        composeTestRule.runOnIdle { progress.value = progress.value?.copy(isComplete = true) }
        composeTestRule.mainClock.autoAdvance = true

        composeTestRule.onNodeWithText(composeTestRule.activity.getString(R.string.core_ui_operation_completed)).assertIsDisplayed()
        composeTestRule.onNodeWithText(pendingMessage).assertDoesNotExist()
    }
}
