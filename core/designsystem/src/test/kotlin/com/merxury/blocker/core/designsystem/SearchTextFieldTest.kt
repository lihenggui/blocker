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

package com.merxury.blocker.core.designsystem

import android.content.Context
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.text.input.ImeAction
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.merxury.blocker.core.designsystem.component.BlockerSearchTextField
import com.merxury.blocker.core.designsystem.theme.BlockerTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SearchTextFieldTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    private val submissions = mutableListOf<String>()
    private val queryChanges = mutableListOf<String>()

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun givenFocusedSearchField_whenEnterIsPressed_thenSubmitsQueryOnce() {
        showSearchField(imeAction = ImeAction.Done)

        composeTestRule.onNodeWithTag("BlockerSearchTextField")
            .assertIsFocused()
            .performKeyInput { pressKey(Key.Enter) }

        composeTestRule.runOnIdle { assertThat(submissions).containsExactly("example") }
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun givenFocusedClearButton_whenEnterIsPressed_thenClearsQuery() {
        showSearchField(imeAction = ImeAction.Done)
        val context = ApplicationProvider.getApplicationContext<Context>()
        val clearDescription = context.getString(R.string.core_designsystem_clear_search_text_content_desc)

        composeTestRule.onNodeWithContentDescription(clearDescription)
            .requestFocus()
            .assertIsFocused()
            .performKeyInput { pressKey(Key.Enter) }

        composeTestRule.runOnIdle {
            assertThat(queryChanges).containsExactly("")
            assertThat(submissions).isEmpty()
        }
    }

    @Test
    fun givenSearchImeAction_whenInvoked_thenSubmitsQuery() {
        showSearchField(imeAction = ImeAction.Search)

        composeTestRule.onNodeWithTag("BlockerSearchTextField").performImeAction()

        composeTestRule.runOnIdle { assertThat(submissions).containsExactly("example") }
    }

    @Test
    fun givenDoneImeAction_whenInvoked_thenSubmitsQuery() {
        showSearchField(imeAction = ImeAction.Done)

        composeTestRule.onNodeWithTag("BlockerSearchTextField").performImeAction()

        composeTestRule.runOnIdle { assertThat(submissions).containsExactly("example") }
    }

    private fun showSearchField(imeAction: ImeAction) {
        composeTestRule.setContent {
            BlockerTheme {
                BlockerSearchTextField(
                    searchQuery = "example",
                    onSearchQueryChange = { queryChanges.add(it) },
                    onSearchTrigger = { submissions.add(it) },
                    keyboardOptions = KeyboardOptions(imeAction = imeAction),
                )
            }
        }
    }
}
