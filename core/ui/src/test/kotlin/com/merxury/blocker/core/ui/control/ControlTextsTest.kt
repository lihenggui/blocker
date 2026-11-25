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

package com.merxury.blocker.core.ui.control

import androidx.activity.ComponentActivity
import androidx.compose.material3.Text
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

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = HiltTestApplication::class, qualifiers = "en")
class ControlTextsTest {
    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun givenPartialBatchCounts_whenDisplayed_thenThePartialCountSelectsThePlural() {
        val message = mutableStateOf(ControlMessage.BatchDone(notChanged = 0, partlyChanged = 1))
        composeTestRule.setContent { Text(message.value.text()) }

        composeTestRule.onNodeWithText("Operation completed. 1 component was only partly changed.").assertIsDisplayed()
        composeTestRule.runOnIdle { message.value = message.value.copy(partlyChanged = 2) }
        composeTestRule.onNodeWithText("Operation completed. 2 components were only partly changed.").assertIsDisplayed()
        composeTestRule.runOnIdle { message.value = message.value.copy(partlyChanged = 1, notChanged = 2) }
        composeTestRule.onNodeWithText("Operation completed. 1 component was only partly changed, and 2 could not be changed.").assertIsDisplayed()
        composeTestRule.runOnIdle { message.value = message.value.copy(partlyChanged = 2, notChanged = 1) }
        composeTestRule.onNodeWithText("Operation completed. 2 components were only partly changed, and 1 could not be changed.").assertIsDisplayed()
    }
}
