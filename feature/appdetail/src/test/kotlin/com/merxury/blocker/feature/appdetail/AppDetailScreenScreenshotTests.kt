/*
 * Copyright 2024 Blocker
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
import com.merxury.blocker.core.domain.model.ComponentSearchResult
import com.merxury.blocker.core.domain.model.MatchedHeaderData
import com.merxury.blocker.core.domain.model.MatchedItem
import com.merxury.blocker.core.model.ComponentType.ACTIVITY
import com.merxury.blocker.core.result.Result
import com.merxury.blocker.core.testing.util.DefaultTestDevices
import com.merxury.blocker.core.testing.util.captureForDevice
import com.merxury.blocker.core.testing.util.captureMultiDevice
import com.merxury.blocker.core.ui.data.UiMessage
import com.merxury.blocker.core.ui.previewparameter.AppDetailTabStatePreviewParameterProvider
import com.merxury.blocker.core.ui.previewparameter.AppListPreviewParameterProvider
import com.merxury.blocker.core.ui.previewparameter.ComponentListPreviewParameterProvider
import com.merxury.blocker.core.ui.previewparameter.PreviewParameterData
import com.merxury.blocker.core.ui.previewparameter.RuleListPreviewParameterProvider
import com.merxury.blocker.core.ui.state.toolbar.AppBarAction.MORE
import com.merxury.blocker.core.ui.state.toolbar.AppBarAction.SEARCH
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
@Config(application = HiltTestApplication::class)
@LooperMode(LooperMode.Mode.PAUSED)
class AppDetailScreenScreenshotTests {
    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private val appList = AppListPreviewParameterProvider().values.first()
    private val tabState = AppDetailTabStatePreviewParameterProvider().values.first()
    private val components = ComponentListPreviewParameterProvider().values.first()
    private val activityComponents = components.filter { it.type == ACTIVITY }.toMutableStateList()
    private val rule = RuleListPreviewParameterProvider().values.first().first()
    private val matchedRuleUiState: Result<List<MatchedItem>> = Result.Success(
        data = listOf(
            MatchedItem(
                header = MatchedHeaderData(
                    title = rule.name,
                    uniqueId = rule.id.toString(),
                ),
                componentList = components,
            ),
        ),
    )

    @Before
    fun setTimeZone() {
        // Make time zone deterministic in tests
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
    }

    @Test
    fun appDetailScreenAppInfoTab() {
        composeTestRule.captureMultiDevice("AppDetailScreenAppInfoTab") {
            AppDetailScreenAppInfoTab()
        }
    }

    @Test
    fun appDetailScreenAppInfoTab_dark() {
        composeTestRule.captureForDevice(
            deviceName = "phone_dark",
            deviceSpec = DefaultTestDevices.PHONE.spec,
            screenshotName = "AppDetailScreenAppInfoTab",
            darkMode = true,
        ) {
            AppDetailScreenAppInfoTab()
        }
    }

    @Test
    fun appDetailScreenComponentTab() {
        composeTestRule.captureMultiDevice("AppDetailScreenComponentTab") {
            AppDetailScreenComponentTab()
        }
    }

    @Test
    fun appDetailScreenComponentTab_dark() {
        composeTestRule.captureForDevice(
            deviceName = "phone_dark",
            deviceSpec = DefaultTestDevices.PHONE.spec,
            screenshotName = "AppDetailScreenComponentTab",
            darkMode = true,
        ) {
            AppDetailScreenComponentTab()
        }
    }

    @Test
    fun appDetailScreenComponentEmpty() {
        composeTestRule.captureMultiDevice("AppDetailScreenComponentEmpty") {
            AppDetailScreenComponentEmpty()
        }
    }

    @Test
    fun appDetailScreenComponentEmpty_dark() {
        composeTestRule.captureForDevice(
            deviceName = "phone_dark",
            deviceSpec = DefaultTestDevices.PHONE.spec,
            screenshotName = "AppDetailScreenComponentEmpty",
            darkMode = true,
        ) {
            AppDetailScreenComponentEmpty()
        }
    }

    @Test
    fun appDetailScreenComponentLoading() {
        composeTestRule.captureMultiDevice("AppDetailScreenComponentLoading") {
            AppDetailScreenComponentLoading()
        }
    }

    @Test
    fun appDetailScreenComponentLoading_dark() {
        composeTestRule.captureForDevice(
            deviceName = "phone_dark",
            deviceSpec = DefaultTestDevices.PHONE.spec,
            screenshotName = "AppDetailScreenComponentLoading",
            darkMode = true,
        ) {
            AppDetailScreenComponentLoading()
        }
    }

    @Test
    fun appDetailScreenComponentError() {
        composeTestRule.captureMultiDevice("AppDetailScreenComponentError") {
            AppDetailScreenComponentError()
        }
    }

    @Test
    fun appDetailScreenComponentError_dark() {
        composeTestRule.captureForDevice(
            deviceName = "phone_dark",
            deviceSpec = DefaultTestDevices.PHONE.spec,
            screenshotName = "AppDetailScreenComponentError",
            darkMode = true,
        ) {
            AppDetailScreenComponentError()
        }
    }

    @Test
    fun appDetailScreenComponentRefreshing() {
        composeTestRule.captureMultiDevice("AppDetailScreenComponentRefreshing") {
            AppDetailScreenComponentRefreshing()
        }
    }

    @Test
    fun appDetailScreenComponentRefreshing_dark() {
        composeTestRule.captureForDevice(
            deviceName = "phone_dark",
            deviceSpec = DefaultTestDevices.PHONE.spec,
            screenshotName = "AppDetailScreenComponentRefreshing",
            darkMode = true,
        ) {
            AppDetailScreenComponentRefreshing()
        }
    }

    @Test
    fun appDetailScreenSdkTab() {
        composeTestRule.captureMultiDevice("AppDetailScreenSdkTab") {
            AppDetailScreenSdkTab()
        }
    }

    @Test
    fun appDetailScreenSdkTab_dark() {
        composeTestRule.captureForDevice(
            deviceName = "phone_dark",
            deviceSpec = DefaultTestDevices.PHONE.spec,
            screenshotName = "AppDetailScreenSdkTab",
            darkMode = true,
        ) {
            AppDetailScreenSdkTab()
        }
    }

    @Test
    fun appDetailScreenSdkLoading() {
        composeTestRule.captureMultiDevice("AppDetailScreenSdkLoading") {
            AppDetailScreenSdkLoading()
        }
    }

    @Test
    fun appDetailScreenSdkLoading_dark() {
        composeTestRule.captureForDevice(
            deviceName = "phone_dark",
            deviceSpec = DefaultTestDevices.PHONE.spec,
            screenshotName = "AppDetailScreenSdkLoading",
            darkMode = true,
        ) {
            AppDetailScreenSdkLoading()
        }
    }

    @Test
    fun appDetailScreenSdkError() {
        composeTestRule.captureMultiDevice("AppDetailScreenSdkError") {
            AppDetailScreenSdkError()
        }
    }

    @Test
    fun appDetailScreenSdkError_dark() {
        composeTestRule.captureForDevice(
            deviceName = "phone_dark",
            deviceSpec = DefaultTestDevices.PHONE.spec,
            screenshotName = "AppDetailScreenSdkError",
            darkMode = true,
        ) {
            AppDetailScreenSdkError()
        }
    }

    @Test
    fun appDetailScreenSearchMode() {
        composeTestRule.captureMultiDevice("AppDetailScreenSearchMode") {
            AppDetailScreenSearchMode()
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
            AppDetailScreenSearchMode()
        }
    }

    @Test
    fun appDetailScreenSelectedMode() {
        composeTestRule.captureMultiDevice("AppDetailScreenSelectedMode") {
            AppDetailScreenSelectedMode()
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
            AppDetailScreenSelectedMode()
        }
    }

    @Composable
    private fun AppDetailScreenAppInfoTab() {
        BlockerTheme {
            Surface {
                AppDetailScreen(
                    appInfoUiState = AppInfoUiState(
                        appInfo = appList[0],
                    ),
                    componentListUiState = Result.Success(
                        ComponentSearchResult(appList[0]),
                    ),
                    tabState = tabState[0],
                    topAppBarUiState = AppBarUiState(),
                )
            }
        }
    }

    @Composable
    private fun AppDetailScreenComponentTab() {
        BlockerTheme {
            Surface {
                AppDetailScreen(
                    appInfoUiState = AppInfoUiState(appInfo = appList[0], iconBasedTheming = null),
                    componentListUiState = Result.Success(
                        ComponentSearchResult(
                            app = appList[0],
                            activity = components.filter { it.type == ACTIVITY },
                        ),
                    ),
                    topAppBarUiState = AppBarUiState(
                        actions = listOf(
                            SEARCH,
                            MORE,
                        ),
                    ),
                    tabState = tabState[1],
                )
            }
        }
    }

    @Composable
    private fun AppDetailScreenComponentEmpty() {
        BlockerTheme {
            Surface {
                AppDetailScreen(
                    appInfoUiState = AppInfoUiState(
                        appInfo = appList[0],
                        showOpenInLibChecker = true,
                    ),
                    componentListUiState = Result.Success(
                        ComponentSearchResult(appList[0]),
                    ),
                    tabState = tabState[2],
                    topAppBarUiState = AppBarUiState(),
                )
            }
        }
    }

    @Composable
    private fun AppDetailScreenComponentLoading() {
        BlockerTheme {
            Surface {
                AppDetailScreen(
                    appInfoUiState = AppInfoUiState(
                        appInfo = PreviewParameterData.appList[0],
                        error = UiMessage("Error"),
                    ),
                    componentListUiState = Result.Loading,
                    tabState = tabState[1],
                    topAppBarUiState = AppBarUiState(),
                )
            }
        }
    }

    @Composable
    private fun AppDetailScreenComponentError() {
        BlockerTheme {
            Surface {
                AppDetailScreen(
                    appInfoUiState = AppInfoUiState(
                        appInfo = appList[0],
                        showOpenInLibChecker = true,
                    ),
                    componentListUiState = Result.Error(
                        Exception("Error"),
                    ),
                    tabState = tabState[1],
                    topAppBarUiState = AppBarUiState(),
                )
            }
        }
    }

    @Composable
    private fun AppDetailScreenComponentRefreshing() {
        BlockerTheme {
            Surface {
                AppDetailScreen(
                    appInfoUiState = AppInfoUiState(
                        appInfo = PreviewParameterData.appList[0],
                        isRefreshing = true,
                    ),
                    componentListUiState = Result.Success(
                        ComponentSearchResult(
                            app = PreviewParameterData.appList[0],
                            activity = components.filter { it.type == ACTIVITY },
                        ),
                    ),
                    tabState = tabState[1],
                    topAppBarUiState = AppBarUiState(),
                )
            }
        }
    }

    @Composable
    private fun AppDetailScreenSdkTab() {
        BlockerTheme {
            Surface {
                AppDetailScreen(
                    appInfoUiState = AppInfoUiState(
                        appInfo = appList[0],
                        showOpenInLibChecker = true,
                        matchedRuleUiState = matchedRuleUiState,
                    ),
                    componentListUiState = Result.Success(
                        ComponentSearchResult(appList[0]),
                    ),
                    tabState = tabState[3],
                    topAppBarUiState = AppBarUiState(),
                )
            }
        }
    }

    @Composable
    private fun AppDetailScreenSdkLoading() {
        BlockerTheme {
            Surface {
                AppDetailScreen(
                    appInfoUiState = AppInfoUiState(
                        appInfo = appList[0],
                        showOpenInLibChecker = true,
                        matchedRuleUiState = Result.Loading,
                    ),
                    componentListUiState = Result.Success(
                        ComponentSearchResult(appList[0]),
                    ),
                    tabState = tabState[3],
                    topAppBarUiState = AppBarUiState(),
                )
            }
        }
    }

    @Composable
    private fun AppDetailScreenSdkError() {
        BlockerTheme {
            Surface {
                AppDetailScreen(
                    appInfoUiState = AppInfoUiState(
                        appInfo = appList[0],
                        showOpenInLibChecker = true,
                        matchedRuleUiState = Result.Error(
                            Exception("Error"),
                        ),
                    ),
                    componentListUiState = Result.Success(
                        ComponentSearchResult(appList[0]),
                    ),
                    tabState = tabState[3],
                    topAppBarUiState = AppBarUiState(),
                )
            }
        }
    }

    @Composable
    private fun AppDetailScreenSearchMode() {
        BlockerTheme {
            Surface {
                AppDetailScreen(
                    appInfoUiState = AppInfoUiState(appInfo = appList[0], iconBasedTheming = null),
                    componentListUiState = Result.Success(
                        ComponentSearchResult(
                            app = appList[0],
                            activity = components.filter { it.type == ACTIVITY },
                        ),
                    ),
                    topAppBarUiState = AppBarUiState(
                        actions = listOf(
                            SEARCH,
                            MORE,
                        ),
                        isSearchMode = true,
                    ),
                    tabState = tabState[1],
                )
            }
        }
    }

    @Composable
    private fun AppDetailScreenSelectedMode() {
        BlockerTheme {
            Surface {
                AppDetailScreen(
                    appInfoUiState = AppInfoUiState(appInfo = appList[0], iconBasedTheming = null),
                    componentListUiState = Result.Success(
                        ComponentSearchResult(
                            app = appList[0],
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
                            activityComponents[0],
                        ),
                    ),
                    tabState = tabState[1],
                )
            }
        }
    }
}
