import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import com.merxury.blocker.core.ui.AppDetailTabs.Activity
import com.merxury.blocker.core.ui.AppDetailTabs.Info
import com.merxury.blocker.core.ui.AppDetailTabs.Provider
import com.merxury.blocker.core.ui.AppDetailTabs.Receiver
import com.merxury.blocker.core.ui.AppDetailTabs.Service
import com.merxury.blocker.core.ui.TabState
import com.merxury.blocker.core.ui.bottomsheet.ComponentSortInfoUiState
import com.merxury.blocker.core.ui.data.UiMessage
import com.merxury.blocker.core.ui.state.toolbar.AppBarUiState
import com.merxury.blocker.feature.appdetail.AppDetailScreen
import com.merxury.blocker.feature.appdetail.AppInfoUiState
import com.merxury.blocker.feature.appdetail.ComponentListUiState
import org.junit.Rule
import org.junit.Test
import com.merxury.blocker.core.ui.R as uiR

/*
 * Copyright 2022 Blocker Open Source Project
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


class AppDetailScreenTest {
    @get:Rule(order = 0)
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private val tabState =
        TabState(
            items = listOf(
                Info,
                Receiver,
                Service,
                Activity,
                Provider,
            ),
            selectedItem = Info,
        )
    private val errorMessage = UiMessage("Can't find apps in this device.")

    @Test
    fun circularProgressIndicator_whenScreenIsLoading_exists() {
        composeTestRule.setContent {
            BoxWithConstraints {
                AppDetailScreen(
                    appInfoUiState = AppInfoUiState.Loading,
                    topAppBarUiState = AppBarUiState(),
                    componentListUiState = ComponentListUiState(),
                    tabState = tabState,
                    bottomSheetState = ComponentSortInfoUiState.Loading,
                    onBackClick = {},
                    onLaunchAppClick = {},
                    switchTab = {},
                )
            }
        }

        composeTestRule
            .onNodeWithContentDescription(
                composeTestRule.activity.resources.getString(uiR.string.core_ui_loading),
            )
            .assertExists()
    }

    @Test
    fun errorIndicator_whenFailToLoadInfo_exists() {
        composeTestRule.setContent {
            BoxWithConstraints {
                AppDetailScreen(
                    appInfoUiState = AppInfoUiState.Error(errorMessage),
                    topAppBarUiState = AppBarUiState(),
                    componentListUiState = ComponentListUiState(),
                    tabState = tabState,
                    bottomSheetState = ComponentSortInfoUiState.Loading,
                    onBackClick = {},
                    onLaunchAppClick = {},
                    switchTab = {},
                )
            }
        }

        composeTestRule
            .onNodeWithContentDescription(
                composeTestRule.activity.resources.getString(uiR.string.core_ui_error),
            )
            .assertExists()
        composeTestRule.onNodeWithText(errorMessage.title).assertExists()
    }
}
