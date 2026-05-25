/*
 * Copyright 2025 Blocker
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

package com.merxury.blocker.feature.ifwrule.impl

import androidx.activity.ComponentActivity
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import com.merxury.blocker.core.designsystem.theme.BlockerTheme
import com.merxury.blocker.core.testing.util.DefaultTestDevices
import com.merxury.blocker.core.testing.util.captureForDevice
import com.merxury.blocker.core.testing.util.captureMultiDevice
import com.merxury.blocker.feature.ifwrule.impl.previewparameter.IfwRuleEditorScreenPreviewParameterProvider
import dagger.hilt.android.testing.HiltTestApplication
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.LooperMode
import java.util.TimeZone

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = HiltTestApplication::class)
@LooperMode(LooperMode.Mode.PAUSED)
class IfwRuleEditorScreenScreenshotTests {
    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    @Before
    fun setTimeZone() {
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
    }

    @Test
    fun ifwRuleEditorScreen_blockAll() {
        composeTestRule.captureMultiDevice("IfwRuleEditorScreenBlockAll") {
            IfwRuleEditorScreen(blockAllState())
        }
    }

    @Test
    fun ifwRuleEditorScreen_blockAll_dark() {
        composeTestRule.captureForDevice(
            deviceName = "phone_dark",
            deviceSpec = DefaultTestDevices.PHONE.spec,
            screenshotName = "IfwRuleEditorScreenBlockAll",
            darkMode = true,
        ) {
            IfwRuleEditorScreen(blockAllState())
        }
    }

    @Test
    fun ifwRuleEditorScreen_conditional() {
        composeTestRule.captureMultiDevice("IfwRuleEditorScreenConditional") {
            IfwRuleEditorScreen(conditionalState())
        }
    }

    @Test
    fun ifwRuleEditorScreen_conditional_dark() {
        composeTestRule.captureForDevice(
            deviceName = "phone_dark",
            deviceSpec = DefaultTestDevices.PHONE.spec,
            screenshotName = "IfwRuleEditorScreenConditional",
            darkMode = true,
        ) {
            IfwRuleEditorScreen(conditionalState())
        }
    }

    @Test
    fun ifwRuleEditorScreen_advanced() {
        composeTestRule.captureMultiDevice("IfwRuleEditorScreenAdvanced") {
            IfwRuleEditorScreen(advancedState())
        }
    }

    @Test
    fun ifwRuleEditorScreen_advanced_dark() {
        composeTestRule.captureForDevice(
            deviceName = "phone_dark",
            deviceSpec = DefaultTestDevices.PHONE.spec,
            screenshotName = "IfwRuleEditorScreenAdvanced",
            darkMode = true,
        ) {
            IfwRuleEditorScreen(advancedState())
        }
    }

    @Test
    fun ifwRuleEditorScreen_loading() {
        composeTestRule.captureMultiDevice("IfwRuleEditorScreenLoading") {
            IfwRuleEditorScreen(RuleEditorScreenUiState.Loading)
        }
    }

    @Test
    fun ifwRuleEditorScreen_loading_dark() {
        composeTestRule.captureForDevice(
            deviceName = "phone_dark",
            deviceSpec = DefaultTestDevices.PHONE.spec,
            screenshotName = "IfwRuleEditorScreenLoading",
            darkMode = true,
        ) {
            IfwRuleEditorScreen(RuleEditorScreenUiState.Loading)
        }
    }

    @Test
    fun ifwRuleEditorScreen_error() {
        composeTestRule.captureMultiDevice("IfwRuleEditorScreenError") {
            IfwRuleEditorScreen(RuleEditorScreenUiState.Error("Failed to load IFW rules"))
        }
    }

    @Test
    fun ifwRuleEditorScreen_error_dark() {
        composeTestRule.captureForDevice(
            deviceName = "phone_dark",
            deviceSpec = DefaultTestDevices.PHONE.spec,
            screenshotName = "IfwRuleEditorScreenError",
            darkMode = true,
        ) {
            IfwRuleEditorScreen(RuleEditorScreenUiState.Error("Failed to load IFW rules"))
        }
    }

    @Composable
    private fun IfwRuleEditorScreen(
        uiState: RuleEditorScreenUiState,
    ) {
        val showUnsavedDialog = (uiState as? RuleEditorScreenUiState.Success)?.hasUnsavedChanges == true
        BlockerTheme {
            Surface {
                IfwRuleEditorScreen(
                    uiState = uiState,
                    showUnsavedDialog = showUnsavedDialog,
                    onBackClick = {},
                    onSaveClick = {},
                    onUpdateBlockMode = {},
                    onUpdateLog = {},
                    onChangeBlockEnable = {},
                    onUpdateRootGroup = {},
                    onDelete = {},
                    onDiscardUnsavedChanges = {},
                    onDismissUnsavedDialog = {},
                )
            }
        }
    }

    private fun blockAllState(): RuleEditorScreenUiState =
        IfwRuleEditorScreenPreviewParameterProvider().values.first()

    private fun conditionalState(): RuleEditorScreenUiState =
        IfwRuleEditorScreenPreviewParameterProvider().values.drop(1).first()

    private fun advancedState(): RuleEditorScreenUiState =
        IfwRuleEditorScreenPreviewParameterProvider().values.drop(2).first()

}
