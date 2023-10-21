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

package com.merxury.blocker.feature.ruledetail

import androidx.activity.ComponentActivity
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.merxury.blocker.core.designsystem.theme.BlockerTheme
import com.merxury.blocker.core.testing.util.DefaultTestDevices
import com.merxury.blocker.core.testing.util.captureForDevice
import com.merxury.blocker.core.testing.util.captureMultiDevice
import com.merxury.blocker.core.ui.data.UiMessage
import com.merxury.blocker.core.ui.previewparameter.AppListPreviewParameterProvider
import com.merxury.blocker.core.ui.previewparameter.ComponentListPreviewParameterProvider
import com.merxury.blocker.core.ui.previewparameter.RuleDetailTabStatePreviewParameterProvider
import com.merxury.blocker.core.ui.previewparameter.RuleListPreviewParameterProvider
import com.merxury.blocker.core.ui.rule.RuleMatchedApp
import com.merxury.blocker.core.ui.rule.RuleMatchedAppListUiState
import com.merxury.blocker.core.ui.state.toolbar.AppBarAction.MORE
import com.merxury.blocker.core.ui.state.toolbar.AppBarUiState
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
class RuleDetailScreenScreenshotTests {
    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private val ruleList = RuleListPreviewParameterProvider().values.first()
    private val components = ComponentListPreviewParameterProvider().values.first()
    private val appList = AppListPreviewParameterProvider().values.first()
    private val tabState = RuleDetailTabStatePreviewParameterProvider().values.first()

    @Before
    fun setTimeZone() {
        // Make time zone deterministic in tests
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
    }

    @Test
    fun ruleDetailScreen() {
        composeTestRule.captureMultiDevice("RuleDetailScreen") {
            RuleDetailScreen()
        }
    }

    @Test
    fun ruleDetailScreen_dark() {
        composeTestRule.captureForDevice(
            deviceName = "phone_dark",
            deviceSpec = DefaultTestDevices.PHONE.spec,
            screenshotName = "RuleDetailScreen",
            darkMode = true,
        ) {
            RuleDetailScreen()
        }
    }

    @Test
    fun ruleDetailScreenSelectedApplicable() {
        composeTestRule.captureMultiDevice("RuleDetailScreenSelectedApplicable") {
            RuleDetailScreenSelectedApplicable()
        }
    }

    @Test
    fun ruleDetailScreenSelectedApplicable_dark() {
        composeTestRule.captureForDevice(
            deviceName = "phone_dark",
            deviceSpec = DefaultTestDevices.PHONE.spec,
            screenshotName = "RuleDetailScreenSelectedApplicable",
            darkMode = true,
        ) {
            RuleDetailScreenSelectedApplicable()
        }
    }

    @Test
    fun ruleDetailScreenWithApplicableLoading() {
        composeTestRule.captureMultiDevice("RuleDetailScreenWithApplicableLoading") {
            RuleDetailScreenWithApplicableLoading()
        }
    }

    @Test
    fun ruleDetailScreenWithApplicableLoading_dark() {
        composeTestRule.captureForDevice(
            deviceName = "phone_dark",
            deviceSpec = DefaultTestDevices.PHONE.spec,
            screenshotName = "RuleDetailScreenWithApplicableLoading",
            darkMode = true,
        ) {
            RuleDetailScreenWithApplicableLoading()
        }
    }

    @Test
    fun ruleDetailScreenLoading() {
        composeTestRule.captureMultiDevice("RuleDetailScreenLoading") {
            RuleDetailScreenLoading()
        }
    }

    @Test
    fun ruleDetailScreenLoading_dark() {
        composeTestRule.captureForDevice(
            deviceName = "phone_dark",
            deviceSpec = DefaultTestDevices.PHONE.spec,
            screenshotName = "RuleDetailScreenLoading",
            darkMode = true,
        ) {
            RuleDetailScreenLoading()
        }
    }

    @Test
    fun ruleDetailScreenError() {
        composeTestRule.captureMultiDevice("RuleDetailScreenError") {
            RuleDetailScreenError()
        }
    }

    @Test
    fun ruleDetailScreenError_dark() {
        composeTestRule.captureForDevice(
            deviceName = "phone_dark",
            deviceSpec = DefaultTestDevices.PHONE.spec,
            screenshotName = "RuleDetailScreenError",
            darkMode = true,
        ) {
            RuleDetailScreenError()
        }
    }

    @Composable
    private fun RuleDetailScreen() {
        BlockerTheme {
            Surface {
                RuleDetailScreen(
                    ruleInfoUiState = RuleInfoUiState.Success(
                        ruleInfo = ruleList.first(),
                        ruleIcon = null,
                        matchedAppsUiState = RuleMatchedAppListUiState.Success(
                            list = listOf(
                                RuleMatchedApp(
                                    app = appList.first(),
                                    componentList = components,
                                ),
                            ),
                        ),
                    ),
                    tabState = tabState[0],
                    appBarUiState = AppBarUiState(
                        actions = listOf(
                            MORE,
                        ),
                    ),
                )
            }
        }
    }

    @Composable
    private fun RuleDetailScreenSelectedApplicable() {
        BlockerTheme(darkTheme = true) {
            Surface {
                RuleDetailScreen(
                    ruleInfoUiState = RuleInfoUiState.Success(
                        ruleInfo = ruleList.first(),
                        ruleIcon = null,
                        matchedAppsUiState = RuleMatchedAppListUiState.Success(
                            list = listOf(
                                RuleMatchedApp(
                                    app = appList.first(),
                                    componentList = components,
                                ),
                            ),
                        ),
                    ),
                    tabState = tabState[1],
                )
            }
        }
    }

    @Composable
    private fun RuleDetailScreenWithApplicableLoading() {
        BlockerTheme {
            Surface {
                RuleDetailScreen(
                    ruleInfoUiState = RuleInfoUiState.Success(
                        ruleInfo = ruleList.first(),
                        ruleIcon = null,
                        matchedAppsUiState = RuleMatchedAppListUiState.Loading,
                    ),
                    tabState = tabState[1],
                )
            }
        }
    }

    @Composable
    private fun RuleDetailScreenLoading() {
        BlockerTheme {
            Surface {
                RuleDetailScreen(
                    ruleInfoUiState = RuleInfoUiState.Loading,
                    tabState = tabState[0],
                )
            }
        }
    }

    @Composable
    private fun RuleDetailScreenError() {
        BlockerTheme {
            Surface {
                RuleDetailScreen(
                    ruleInfoUiState = RuleInfoUiState.Error(
                        error = UiMessage("Error"),
                    ),
                    tabState = tabState[0],
                )
            }
        }
    }
}
