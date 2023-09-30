/*
 * Copyright 2023 Blocker
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

package com.merxury.blocker.feature.generalrules

import androidx.activity.ComponentActivity
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.merxury.blocker.core.designsystem.theme.BlockerTheme
import com.merxury.blocker.core.testing.util.DefaultTestDevices
import com.merxury.blocker.core.testing.util.captureForDevice
import com.merxury.blocker.core.testing.util.captureMultiDevice
import com.merxury.blocker.core.ui.data.UiMessage
import com.merxury.blocker.core.ui.previewparameter.RuleListPreviewParameterProvider
import com.merxury.blocker.feature.generalrules.GeneralRuleUiState.Error
import com.merxury.blocker.feature.generalrules.GeneralRuleUiState.Loading
import com.merxury.blocker.feature.generalrules.GeneralRuleUiState.Success
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
@Config(application = HiltTestApplication::class, sdk = [33])
@LooperMode(LooperMode.Mode.PAUSED)
class GeneralRuleScreenScreenshotTests {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    @Before
    fun setTimeZone() {
        // Make time zone deterministic in tests
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
    }

    @Test
    fun generalRuleListScreen() {
        composeTestRule.captureMultiDevice("GeneralRuleListScreen") {
            GeneralRuleListScreen()
        }
    }

    @Test
    fun generalRuleListScreen_dark() {
        composeTestRule.captureForDevice(
            deviceName = "phone_dark",
            deviceSpec = DefaultTestDevices.PHONE.spec,
            screenshotName = "GeneralRuleListScreen",
            darkMode = true,
        ) {
            GeneralRuleListScreen()
        }
    }

    @Test
    fun generalRuleScreenLoading() {
        composeTestRule.captureMultiDevice("GeneralRuleScreenLoading") {
            GeneralRuleScreenLoading()
        }
    }

    @Test
    fun generalRuleScreenLoading_dark() {
        composeTestRule.captureForDevice(
            deviceName = "phone_dark",
            deviceSpec = DefaultTestDevices.PHONE.spec,
            screenshotName = "GeneralRuleScreenLoading",
            darkMode = true,
        ) {
            GeneralRuleScreenLoading()
        }
    }

    @Test
    fun generalRuleScreenError() {
        composeTestRule.captureMultiDevice("GeneralRuleScreenError") {
            GeneralRuleScreenError()
        }
    }

    @Test
    fun generalRuleScreenError_dark() {
        composeTestRule.captureForDevice(
            deviceName = "phone_dark",
            deviceSpec = DefaultTestDevices.PHONE.spec,
            screenshotName = "GeneralRuleScreenError",
            darkMode = true,
        ) {
            GeneralRuleScreenError()
        }
    }

    @Composable
    private fun GeneralRuleListScreen() {
        val ruleList = RuleListPreviewParameterProvider().values.first()
        BlockerTheme {
            Surface {
                GeneralRulesScreen(uiState = Success(ruleList))
            }
        }
    }

    @Composable
    private fun GeneralRuleScreenLoading() {
        BlockerTheme {
            Surface {
                GeneralRulesScreen(uiState = Loading)
            }
        }
    }

    @Composable
    private fun GeneralRuleScreenError() {
        BlockerTheme {
            Surface {
                GeneralRulesScreen(uiState = Error(UiMessage("Error")))
            }
        }
    }
}
