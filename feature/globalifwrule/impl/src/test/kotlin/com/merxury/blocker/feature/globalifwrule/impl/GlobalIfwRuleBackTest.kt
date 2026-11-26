// Copyright 2026 Blocker
// SPDX-License-Identifier: Apache-2.0

package com.merxury.blocker.feature.globalifwrule.impl

import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.lifecycle.SavedStateHandle
import com.merxury.blocker.core.designsystem.theme.BlockerTheme
import com.merxury.blocker.core.testing.controller.FakeIfwFileSystem
import com.merxury.blocker.core.testing.repository.device.FakeComponentStatusRepository
import com.merxury.blocker.core.testing.repository.device.FakeSelectedUserRepository
import com.merxury.core.ifw.IntentFirewallStore
import com.merxury.core.ifw.getOrThrow
import com.merxury.core.ifw.model.IfwComponentType
import com.merxury.core.ifw.model.IfwIntentFilter
import com.merxury.core.ifw.model.IfwRule
import com.merxury.core.ifw.model.IfwRules
import com.merxury.core.ifw.xml.IfwXmlDeserializer
import com.merxury.core.ifw.xml.IfwXmlSerializer
import dagger.hilt.android.testing.HiltTestApplication
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class)
class GlobalIfwRuleBackTest {
    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private val store = IntentFirewallStore(
        FakeIfwFileSystem(),
        IfwXmlSerializer(),
        IfwXmlDeserializer(),
        "com.merxury.blocker.test",
    )
    private lateinit var viewModel: GlobalIfwRuleViewModel
    private var exitedRoute = false

    @Test
    fun givenDirtySimpleDraft_whenSystemBackPressed_thenShowsDialogAndKeepsEditor() {
        showRoute()
        composeTestRule.onNodeWithContentDescription(text(R.string.feature_globalifwrule_impl_add_rule)).performClick()
        composeTestRule.runOnIdle { viewModel.updateSimpleLog(false) }

        pressSystemBack()

        composeTestRule.onNodeWithText(text(R.string.feature_globalifwrule_impl_unsaved_title)).assertIsDisplayed()
        assertEquals(GlobalIfwRuleScreenState.SIMPLE_EDIT, viewModel.editorState.value.screen)
        assertEquals(false, viewModel.editorState.value.simpleDraft?.log)
        assertFalse(exitedRoute)
    }

    @Test
    fun givenDirtyAdvancedDraft_whenSystemBackPressed_thenShowsDialogAndKeepsEditor() {
        showRoute()
        composeTestRule.onNodeWithContentDescription(text(R.string.feature_globalifwrule_impl_add_advanced_rule)).performClick()
        composeTestRule.runOnIdle { viewModel.updateAdvancedLog(false) }

        pressSystemBack()

        composeTestRule.onNodeWithText(text(R.string.feature_globalifwrule_impl_unsaved_title)).assertIsDisplayed()
        assertEquals(GlobalIfwRuleScreenState.ADVANCED_EDIT, viewModel.editorState.value.screen)
        assertEquals(false, viewModel.editorState.value.advancedDraft?.log)
        assertFalse(exitedRoute)
    }

    @Test
    fun givenCleanSimpleDraft_whenSystemBackPressed_thenReturnsToList() {
        showRoute()
        composeTestRule.onNodeWithContentDescription(text(R.string.feature_globalifwrule_impl_add_rule)).performClick()

        pressSystemBack()

        assertReturnedToList()
    }

    @Test
    fun givenCleanAdvancedDraft_whenSystemBackPressed_thenReturnsToList() {
        showRoute()
        composeTestRule.onNodeWithContentDescription(text(R.string.feature_globalifwrule_impl_add_advanced_rule)).performClick()

        pressSystemBack()

        assertReturnedToList()
    }

    @Test
    fun givenAdvancedDetail_whenSystemBackPressed_thenReturnsToList() {
        val packageName = "com.example"
        runTest {
            val rule = IfwRule(
                componentType = IfwComponentType.SERVICE,
                intentFilters = listOf(IfwIntentFilter(actions = listOf("android.intent.action.VIEW"))),
            )
            store.update(packageName) { IfwRules(listOf(rule)) }.getOrThrow()
        }
        showRoute()
        composeTestRule.runOnIdle { viewModel.openRule(packageName, 0) }
        composeTestRule.onNodeWithText(text(R.string.feature_globalifwrule_impl_advanced_rule)).assertIsDisplayed()

        pressSystemBack()

        assertReturnedToList()
    }

    @Test
    fun givenList_whenSystemBackPressed_thenExitsRoute() {
        showRoute()

        pressSystemBack()

        assertTrue(exitedRoute)
    }

    private fun showRoute() {
        val activity = composeTestRule.activity
        composeTestRule.runOnIdle {
            viewModel = GlobalIfwRuleViewModel(
                ifwStore = store,
                componentRepository = FakeComponentStatusRepository(),
                selectedUsers = FakeSelectedUserRepository(),
                packageManager = activity.packageManager,
                savedStateHandle = SavedStateHandle(),
            )
        }
        val exitRoute = { exitedRoute = true }
        composeTestRule.setContent {
            BackHandler(onBack = exitRoute)
            BlockerTheme {
                GlobalIfwRuleRoute(onBack = exitRoute, viewModel = viewModel)
            }
        }
    }

    private fun pressSystemBack() {
        val dispatcher = composeTestRule.activity.onBackPressedDispatcher
        composeTestRule.runOnIdle { dispatcher.onBackPressed() }
    }

    private fun text(resource: Int) = composeTestRule.activity.getString(resource)

    private fun assertReturnedToList() {
        composeTestRule.onNodeWithText(text(R.string.feature_globalifwrule_impl_title)).assertIsDisplayed()
        composeTestRule.onNodeWithText(text(R.string.feature_globalifwrule_impl_unsaved_title)).assertDoesNotExist()
        assertEquals(GlobalIfwRuleScreenState.LIST, viewModel.editorState.value.screen)
        assertFalse(exitedRoute)
    }
}
