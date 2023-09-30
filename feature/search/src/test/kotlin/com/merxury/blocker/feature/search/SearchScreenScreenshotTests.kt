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

package com.merxury.blocker.feature.search

import androidx.activity.ComponentActivity
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.text.input.TextFieldValue
import com.merxury.blocker.core.designsystem.theme.BlockerTheme
import com.merxury.blocker.core.model.ComponentType.ACTIVITY
import com.merxury.blocker.core.model.ComponentType.RECEIVER
import com.merxury.blocker.core.model.data.FilteredComponent
import com.merxury.blocker.core.testing.util.DefaultTestDevices
import com.merxury.blocker.core.testing.util.captureForDevice
import com.merxury.blocker.core.testing.util.captureMultiDevice
import com.merxury.blocker.core.ui.data.UiMessage
import com.merxury.blocker.core.ui.previewparameter.AppListPreviewParameterProvider
import com.merxury.blocker.core.ui.previewparameter.ComponentListPreviewParameterProvider
import com.merxury.blocker.core.ui.previewparameter.RuleListPreviewParameterProvider
import com.merxury.blocker.core.ui.previewparameter.SearchTabStatePreviewParameterProvider
import com.merxury.blocker.feature.search.LocalSearchUiState.Error
import com.merxury.blocker.feature.search.LocalSearchUiState.Idle
import com.merxury.blocker.feature.search.LocalSearchUiState.Initializing
import com.merxury.blocker.feature.search.LocalSearchUiState.Success
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
class SearchScreenScreenshotTests {
    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private val tabState = SearchTabStatePreviewParameterProvider().values.first()
    private val appList = AppListPreviewParameterProvider().values.first()
    private val components = ComponentListPreviewParameterProvider().values.first()
    private val ruleList = RuleListPreviewParameterProvider().values.first()
    private val keyword = "blocker"

    @Before
    fun setTimeZone() {
        // Make time zone deterministic in tests
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
    }

    @Test
    fun searchScreenSelectedApp() {
        composeTestRule.captureMultiDevice("SearchScreenSelectedApp") {
            SearchScreenSelectedApp()
        }
    }

    @Test
    fun searchScreenSelectedApp_dark() {
        composeTestRule.captureForDevice(
            deviceName = "phone_dark",
            deviceSpec = DefaultTestDevices.PHONE.spec,
            screenshotName = "SearchScreenSelectedApp",
            darkMode = true,
        ) {
            SearchScreenSelectedApp()
        }
    }

    @Test
    fun searchScreenSelectedComponent() {
        composeTestRule.captureMultiDevice("SearchScreenSelectedComponent") {
            SearchScreenSelectedComponent()
        }
    }

    @Test
    fun searchScreenSelectedComponent_dark() {
        composeTestRule.captureForDevice(
            deviceName = "phone_dark",
            deviceSpec = DefaultTestDevices.PHONE.spec,
            screenshotName = "SearchScreenSelectedComponent",
            darkMode = true,
        ) {
            SearchScreenSelectedComponent()
        }
    }

    @Test
    fun searchScreenSelectedRule() {
        composeTestRule.captureMultiDevice("SearchScreenSelectedRule") {
            SearchScreenSelectedRule()
        }
    }

    @Test
    fun searchScreenSelectedRule_dark() {
        composeTestRule.captureForDevice(
            deviceName = "phone_dark",
            deviceSpec = DefaultTestDevices.PHONE.spec,
            screenshotName = "SearchScreenSelectedRule",
            darkMode = true,
        ) {
            SearchScreenSelectedRule()
        }
    }

    @Test
    fun searchScreenSelectedMode() {
        composeTestRule.captureMultiDevice("SearchScreenSelectedMode") {
            SearchScreenSelectedMode()
        }
    }

    @Test
    fun searchScreenSelectedMode_dark() {
        composeTestRule.captureForDevice(
            deviceName = "phone_dark",
            deviceSpec = DefaultTestDevices.PHONE.spec,
            screenshotName = "SearchScreenSelectedMode",
            darkMode = true,
        ) {
            SearchScreenSelectedMode()
        }
    }

    @Test
    fun searchScreenEmpty() {
        composeTestRule.captureMultiDevice("SearchScreenEmpty") {
            SearchScreenEmpty()
        }
    }

    @Test
    fun searchScreenEmpty_dark() {
        composeTestRule.captureForDevice(
            deviceName = "phone_dark",
            deviceSpec = DefaultTestDevices.PHONE.spec,
            screenshotName = "SearchScreenEmpty",
            darkMode = true,
        ) {
            SearchScreenEmpty()
        }
    }

    @Test
    fun searchScreenLoading() {
        composeTestRule.captureMultiDevice("SearchScreenLoading") {
            SearchScreenLoading()
        }
    }

    @Test
    fun searchScreenLoading_dark() {
        composeTestRule.captureForDevice(
            deviceName = "phone_dark",
            deviceSpec = DefaultTestDevices.PHONE.spec,
            screenshotName = "SearchScreenLoading",
            darkMode = true,
        ) {
            SearchScreenLoading()
        }
    }

    @Test
    fun searchScreenNoResult() {
        composeTestRule.captureMultiDevice("SearchScreenNoResult") {
            SearchScreenNoResult()
        }
    }

    @Test
    fun searchScreenNoResult_dark() {
        composeTestRule.captureForDevice(
            deviceName = "phone_dark",
            deviceSpec = DefaultTestDevices.PHONE.spec,
            screenshotName = "SearchScreenNoResult",
            darkMode = true,
        ) {
            SearchScreenNoResult()
        }
    }

    @Test
    fun searchScreenInitial() {
        composeTestRule.captureMultiDevice("SearchScreenInitial") {
            SearchScreenInitial()
        }
    }

    @Test
    fun searchScreenInitial_dark() {
        composeTestRule.captureForDevice(
            deviceName = "phone_dark",
            deviceSpec = DefaultTestDevices.PHONE.spec,
            screenshotName = "SearchScreenInitial",
            darkMode = true,
        ) {
            SearchScreenInitial()
        }
    }

    @Test
    fun searchScreenError() {
        composeTestRule.captureMultiDevice("SearchScreenError") {
            SearchScreenError()
        }
    }

    @Test
    fun searchScreenError_dark() {
        composeTestRule.captureForDevice(
            deviceName = "phone_dark",
            deviceSpec = DefaultTestDevices.PHONE.spec,
            screenshotName = "SearchScreenError",
            darkMode = true,
        ) {
            SearchScreenError()
        }
    }

    @Composable
    private fun SearchScreenSelectedApp() {
        BlockerTheme {
            Surface {
                SearchScreen(
                    localSearchUiState = Success(
                        searchKeyword = listOf(keyword),
                        appTabUiState = AppTabUiState(
                            list = appList,
                        ),
                    ),
                    tabState = tabState[0],
                    searchUiState = SearchUiState(
                        keyword = TextFieldValue(keyword),
                    ),
                )
            }
        }
    }

    @Composable
    private fun SearchScreenSelectedComponent() {
        BlockerTheme {
            Surface {
                SearchScreen(
                    localSearchUiState = Success(
                        searchKeyword = listOf(keyword),
                        componentTabUiState = ComponentTabUiState(
                            list = listOf(
                                FilteredComponent(
                                    app = appList[0],
                                    activity = components.filter { it.type == ACTIVITY },
                                    receiver = components.filter { it.type == RECEIVER },
                                ),
                            ),
                        ),
                    ),
                    tabState = tabState[1],
                    searchUiState = SearchUiState(
                        keyword = TextFieldValue(keyword),
                    ),
                )
            }
        }
    }

    @Composable
    private fun SearchScreenSelectedRule() {
        BlockerTheme {
            Surface {
                SearchScreen(
                    localSearchUiState = Success(
                        searchKeyword = listOf(keyword),
                        ruleTabUiState = RuleTabUiState(
                            list = ruleList,
                        ),
                    ),
                    tabState = tabState[2],
                    searchUiState = SearchUiState(
                        keyword = TextFieldValue(keyword),
                    ),
                )
            }
        }
    }

    @Composable
    private fun SearchScreenSelectedMode() {
        BlockerTheme {
            Surface {
                SearchScreen(
                    localSearchUiState = Success(
                        searchKeyword = listOf(keyword),
                        componentTabUiState = ComponentTabUiState(
                            list = listOf(
                                FilteredComponent(
                                    app = appList[0],
                                    activity = components.filter { it.type == ACTIVITY },
                                    receiver = components.filter { it.type == RECEIVER },
                                ),
                            ),
                        ),
                    ),
                    tabState = tabState[1],
                    searchUiState = SearchUiState(
                        keyword = TextFieldValue(keyword),
                        isSelectedMode = true,
                        selectedComponentList = listOf(
                            components[0].toComponentInfo(),
                            components[1].toComponentInfo(),
                        ),
                        selectedAppList = listOf(
                            FilteredComponent(
                                app = appList[0],
                                activity = components.filter { it.type == ACTIVITY },
                                receiver = components.filter { it.type == RECEIVER },
                            ),
                        ),
                    ),
                )
            }
        }
    }

    @Composable
    private fun SearchScreenEmpty() {
        BlockerTheme {
            Surface {
                SearchScreen(
                    localSearchUiState = Idle,
                    tabState = tabState[0],
                    searchUiState = SearchUiState(),
                )
            }
        }
    }

    @Composable
    private fun SearchScreenLoading() {
        BlockerTheme {
            Surface {
                SearchScreen(
                    localSearchUiState = Idle,
                    tabState = tabState[0],
                    searchUiState = SearchUiState(),
                )
            }
        }
    }

    @Composable
    private fun SearchScreenNoResult() {
        BlockerTheme {
            Surface {
                SearchScreen(
                    localSearchUiState = Success(
                        searchKeyword = listOf(keyword),
                        appTabUiState = AppTabUiState(
                            list = emptyList(),
                        ),
                    ),
                    tabState = tabState[3],
                    searchUiState = SearchUiState(
                        keyword = TextFieldValue(keyword),
                    ),
                )
            }
        }
    }

    @Composable
    private fun SearchScreenInitial() {
        BlockerTheme {
            Surface {
                SearchScreen(
                    localSearchUiState = Initializing("Blocker"),
                    tabState = tabState[0],
                    searchUiState = SearchUiState(),
                )
            }
        }
    }

    @Composable
    private fun SearchScreenError() {
        BlockerTheme {
            Surface {
                SearchScreen(
                    localSearchUiState = Error(uiMessage = UiMessage("Error")),
                    tabState = tabState[0],
                    searchUiState = SearchUiState(),
                )
            }
        }
    }
}
