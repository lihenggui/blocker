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

package com.merxury.blocker.feature.appdetail

import androidx.activity.ComponentActivity
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.merxury.blocker.core.designsystem.theme.BlockerTheme
import com.merxury.blocker.core.model.ComponentType.ACTIVITY
import com.merxury.blocker.core.testing.util.DefaultTestDevices
import com.merxury.blocker.core.testing.util.captureForDevice
import com.merxury.blocker.core.testing.util.captureMultiDevice
import com.merxury.blocker.core.ui.data.UiMessage
import com.merxury.blocker.core.ui.previewparameter.AppDetailTabStatePreviewParameterProvider
import com.merxury.blocker.core.ui.previewparameter.AppListPreviewParameterProvider
import com.merxury.blocker.core.ui.previewparameter.ComponentListPreviewParameterProvider
import com.merxury.blocker.core.ui.state.toolbar.AppBarAction.MORE
import com.merxury.blocker.core.ui.state.toolbar.AppBarAction.SEARCH
import com.merxury.blocker.core.ui.state.toolbar.AppBarUiState
import com.merxury.blocker.feature.appdetail.AppInfoUiState.Success
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
class AppDetailScreenScreenshotTests {
    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private val appList = AppListPreviewParameterProvider().values.first()
    private val tabState = AppDetailTabStatePreviewParameterProvider().values.first()
    private val components = ComponentListPreviewParameterProvider().values.first()
    private val activityComponents = components.filter { it.type == ACTIVITY }.toMutableStateList()

    @Before
    fun setTimeZone() {
        // Make time zone deterministic in tests
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
    }

    @Test
    fun appDetailScreen() {
        composeTestRule.captureMultiDevice("AppDetailScreen") {
            AppDetailScreen()
        }
    }

    @Test
    fun appDetailScreen_dark() {
        composeTestRule.captureForDevice(
            deviceName = "phone_dark",
            deviceSpec = DefaultTestDevices.PHONE.spec,
            screenshotName = "AppDetailScreen",
            darkMode = true,
        ) {
            AppDetailScreen()
        }
    }

    @Test
    fun appDetailScreenWithLoading() {
        composeTestRule.captureMultiDevice("AppDetailScreenLoading") {
            AppDetailScreenLoading()
        }
    }

    @Test
    fun appDetailScreenWithLoading_dark() {
        composeTestRule.captureForDevice(
            deviceName = "phone_dark",
            deviceSpec = DefaultTestDevices.PHONE.spec,
            screenshotName = "AppDetailScreenLoading",
            darkMode = true,
        ) {
            AppDetailScreenLoading()
        }
    }

    @Test
    fun appDetailScreenWithError() {
        composeTestRule.captureMultiDevice("AppDetailScreenError") {
            AppDetailScreenError()
        }
    }

    @Test
    fun appDetailScreenWithError_dark() {
        composeTestRule.captureForDevice(
            deviceName = "phone_dark",
            deviceSpec = DefaultTestDevices.PHONE.spec,
            screenshotName = "AppDetailScreenError",
            darkMode = true,
        ) {
            AppDetailScreenError()
        }
    }

    @Test
    fun appDetailScreenSelectedComponentTab() {
        composeTestRule.captureMultiDevice("AppDetailScreenSelectedComponentTab") {
            AppDetailSreenSelectedCompnentTab()
        }
    }

    @Test
    fun appDetailScreenSelectedComponentTab_dark() {
        composeTestRule.captureForDevice(
            deviceName = "phone_dark",
            deviceSpec = DefaultTestDevices.PHONE.spec,
            screenshotName = "AppDetailScreenSelectedComponentTab",
            darkMode = true,
        ) {
            AppDetailSreenSelectedCompnentTab()
        }
    }

    @Test
    fun appDetailScreenSearchMode() {
        composeTestRule.captureMultiDevice("AppDetailScreenSearchMode") {
            AppDetailSreenSearchMode()
        }
    }

    @Test
    fun appDetailScreenSearchMode_dark() {
        composeTestRule.captureForDevice(
            deviceName = "phone_dark",
            deviceSpec = DefaultTestDevices.PHONE.spec,
            screenshotName = "AppDetailScreenSearchMode",
            darkMode = true,
        ) {
            AppDetailSreenSearchMode()
        }
    }

    @Test
    fun appDetailScreenSelectedMode() {
        composeTestRule.captureMultiDevice("AppDetailScreenSelectedMode") {
            AppDetailSreenSelectedMode()
        }
    }

    @Test
    fun appDetailScreenSelectedMode_dark() {
        composeTestRule.captureForDevice(
            deviceName = "phone_dark",
            deviceSpec = DefaultTestDevices.PHONE.spec,
            screenshotName = "AppDetailScreenSelectedMode",
            darkMode = true,
        ) {
            AppDetailSreenSelectedMode()
        }
    }

    @Composable
    private fun AppDetailScreen() {
        BlockerTheme {
            Surface {
                AppDetailScreen(
                    appInfoUiState = Success(
                        appInfo = appList[0],
                        iconBasedTheming = null,
                    ),
                    tabState = tabState[0],
                    topAppBarUiState = AppBarUiState(),
                )
            }
        }
    }

    @Composable
    private fun AppDetailScreenLoading() {
        BlockerTheme {
            Surface {
                AppDetailScreen(
                    appInfoUiState = AppInfoUiState.Loading,
                    tabState = tabState[0],
                    topAppBarUiState = AppBarUiState(),
                )
            }
        }
    }

    @Composable
    private fun AppDetailScreenError() {
        BlockerTheme {
            Surface {
                AppDetailScreen(
                    appInfoUiState = AppInfoUiState.Error(error = UiMessage("Error")),
                    tabState = tabState[0],
                    topAppBarUiState = AppBarUiState(),
                )
            }
        }
    }

    @Composable
    private fun AppDetailSreenSelectedCompnentTab() {
        BlockerTheme {
            Surface {
                AppDetailScreen(
                    appInfoUiState = Success(
                        appInfo = appList[0],
                        iconBasedTheming = null,
                        componentListUiState = ComponentListUiState(
                            activity = activityComponents,
                        ),
                    ),
                    tabState = tabState[1],
                    topAppBarUiState = AppBarUiState(
                        actions = listOf(
                            SEARCH,
                            MORE,
                        ),
                    ),
                )
            }
        }
    }

    @Composable
    private fun AppDetailSreenSearchMode() {
        BlockerTheme {
            Surface {
                AppDetailScreen(
                    appInfoUiState = Success(
                        appInfo = appList[0],
                        iconBasedTheming = null,
                        componentListUiState = ComponentListUiState(
                            activity = activityComponents,
                        ),
                    ),
                    tabState = tabState[1],
                    topAppBarUiState = AppBarUiState(
                        actions = listOf(
                            SEARCH,
                            MORE,
                        ),
                        isSearchMode = true,
                    ),
                )
            }
        }
    }

    @Composable
    private fun AppDetailSreenSelectedMode() {
        BlockerTheme {
            Surface {
                BlockerTheme {
                    Surface {
                        AppDetailScreen(
                            appInfoUiState = Success(
                                appInfo = appList[0],
                                iconBasedTheming = null,
                                componentListUiState = ComponentListUiState(
                                    activity = activityComponents,
                                ),
                            ),
                            topAppBarUiState = AppBarUiState(
                                actions = listOf(
                                    SEARCH,
                                    MORE,
                                ),
                                isSearchMode = true,
                                isSelectedMode = true,
                                selectedComponentList = listOf(
                                    activityComponents[0].toComponentInfo(),
                                ),
                            ),
                            tabState = tabState[1],
                        )
                    }
                }
            }
        }
    }
}
