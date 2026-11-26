// Copyright 2026 Blocker
// SPDX-License-Identifier: Apache-2.0

package com.merxury.blocker.feature.ifwrule.impl

import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.merxury.blocker.core.designsystem.theme.BlockerTheme
import com.merxury.blocker.core.testing.controller.FakeIfwFileSystem
import com.merxury.core.ifw.IntentFirewallStore
import com.merxury.core.ifw.xml.IfwXmlDeserializer
import com.merxury.core.ifw.xml.IfwXmlSerializer
import dagger.hilt.android.testing.HiltTestApplication
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class)
class IfwRuleEditorBackTest {
    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private val editorVisible = mutableStateOf(true)
    private lateinit var viewModel: IfwRuleEditorViewModel

    @Test
    fun givenDirtyDraft_whenSystemBackPressed_thenShowsDialogAndKeepsEditor() {
        showEditor()
        composeTestRule.runOnIdle { viewModel.updateLog(false) }

        pressSystemBack()

        composeTestRule.onNodeWithText(text(R.string.feature_ifwrule_impl_unsaved_title)).assertIsDisplayed()
        composeTestRule.onNodeWithText(text(R.string.feature_ifwrule_impl_title)).assertIsDisplayed()
    }

    @Test
    fun givenCleanDraft_whenSystemBackPressed_thenLeavesEditor() {
        showEditor()

        pressSystemBack()

        composeTestRule.onNodeWithText(text(R.string.feature_ifwrule_impl_title)).assertDoesNotExist()
        composeTestRule.onNodeWithText(text(R.string.feature_ifwrule_impl_unsaved_title)).assertDoesNotExist()
    }

    private fun showEditor() {
        composeTestRule.runOnIdle {
            viewModel = IfwRuleEditorViewModel(
                ifwStore = IntentFirewallStore(
                    FakeIfwFileSystem(),
                    IfwXmlSerializer(),
                    IfwXmlDeserializer(),
                    "com.merxury.blocker.test",
                ),
                packageName = "com.example",
                componentName = "com.example.BootReceiver",
                componentTypeTag = "broadcast",
            )
        }
        val leaveEditor = { editorVisible.value = false }
        composeTestRule.setContent {
            BackHandler(onBack = leaveEditor)
            BlockerTheme {
                if (editorVisible.value) {
                    IfwRuleEditorScreen(onBackClick = leaveEditor, viewModel = viewModel)
                }
            }
        }
        composeTestRule.onNodeWithText(text(R.string.feature_ifwrule_impl_title)).assertIsDisplayed()
    }

    private fun pressSystemBack() {
        val dispatcher = composeTestRule.activity.onBackPressedDispatcher
        composeTestRule.runOnIdle { dispatcher.onBackPressed() }
    }

    private fun text(resource: Int) = composeTestRule.activity.getString(resource)
}
