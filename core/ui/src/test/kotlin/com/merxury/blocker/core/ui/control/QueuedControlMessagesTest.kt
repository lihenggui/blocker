// Copyright 2026 Blocker
// SPDX-License-Identifier: Apache-2.0

package com.merxury.blocker.core.ui.control

import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import com.google.common.truth.Truth.assertThat
import com.merxury.blocker.core.model.component.ComponentId
import com.merxury.blocker.core.model.control.ControlGoal
import com.merxury.blocker.core.ui.R
import dagger.hilt.android.testing.HiltTestApplication
import kotlinx.coroutines.test.StandardTestDispatcher
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.LooperMode

class QueuedControlMessagesTest {
    private val firstComponent = ComponentId("com.example", "com.example.First")
    private val secondComponent = ComponentId("com.example", "com.example.Second")

    @Test
    fun givenAComponentMessageIsShowing_whenEnqueuingAnotherForIt_thenPreservesTheHead() {
        val shown = componentMessage(1, firstComponent)
        val new = componentMessage(2, firstComponent)

        assertThat(listOf(shown).enqueue(new)).containsExactly(shown, new).inOrder()
    }

    @Test
    fun givenWaitingMessagesForDistinctComponents_whenEnqueuingAnUpdate_thenOnlyReplacesTheMatchingMessage() {
        val shown = batchMessage(1)
        val waiting = componentMessage(2, firstComponent)
        val other = componentMessage(3, secondComponent)
        val new = componentMessage(4, firstComponent)

        assertThat(listOf(shown, waiting, other).enqueue(new)).containsExactly(shown, other, new).inOrder()
    }

    @Test
    fun givenBatchSummariesAreWaiting_whenEnqueuingABatchSummary_thenKeepsAllSummaries() {
        val shown = componentMessage(1, firstComponent)
        val waiting = batchMessage(2)
        val new = batchMessage(3)

        assertThat(listOf(shown, waiting).enqueue(new)).containsExactly(shown, waiting, new).inOrder()
    }

    private fun componentMessage(id: Long, component: ComponentId) = QueuedControlMessage(
        id,
        ControlMessage.ComponentNotChanged(component.simpleName, ControlGoal.BLOCK),
        component,
    )

    private fun batchMessage(id: Long) = QueuedControlMessage(id, ControlMessage.BatchDone(notChanged = 1))
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = HiltTestApplication::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
class ControlMessageSnackbarTest {
    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>(effectContext = StandardTestDispatcher())

    @Test
    fun givenAQueuedMessage_whenDismissingTheSnackbar_thenReportsTheDisplayedMessageId() {
        val hostState = SnackbarHostState()
        val shownIds = mutableListOf<Long>()
        val message = QueuedControlMessage(42, ControlMessage.NoComponents)
        composeTestRule.setContent {
            MaterialTheme {
                SnackbarHost(hostState)
                ControlMessageSnackbar(listOf(message), hostState, onShow = { shownIds += it }, onGrantAccess = {})
            }
        }

        composeTestRule.onNodeWithText(text(R.string.core_ui_no_components)).assertIsDisplayed()
        composeTestRule.onNodeWithText(text(R.string.core_ui_grant_access)).assertDoesNotExist()
        composeTestRule.runOnIdle { assertThat(shownIds).isEmpty() }

        composeTestRule.onNode(SemanticsMatcher.keyIsDefined(SemanticsActions.Dismiss))
            .performSemanticsAction(SemanticsActions.Dismiss)

        composeTestRule.runOnIdle { assertThat(shownIds).containsExactly(message.id) }
    }

    @Test
    fun givenAMessageNeedsAccess_whenClickingGrantAccess_thenRequestsAccessAndReportsItShown() {
        val hostState = SnackbarHostState()
        val callbacks = mutableListOf<String>()
        val message = "Access is required"
        composeTestRule.setContent {
            MaterialTheme {
                SnackbarHost(hostState)
                AccessMessageSnackbar(
                    key = 42,
                    text = message,
                    needsAccess = true,
                    snackbarHostState = hostState,
                    onShow = { callbacks += "shown" },
                    onGrantAccess = { callbacks += "grant" },
                )
            }
        }

        composeTestRule.onNodeWithText(message).assertIsDisplayed()
        composeTestRule.onNodeWithText(text(R.string.core_ui_grant_access)).assertIsDisplayed().performClick()

        composeTestRule.runOnIdle { assertThat(callbacks).containsExactly("grant", "shown").inOrder() }
    }

    private fun text(id: Int) = composeTestRule.activity.getString(id)
}
