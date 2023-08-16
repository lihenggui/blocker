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
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import com.merxury.blocker.core.model.ComponentType.RECEIVER
import com.merxury.blocker.core.ui.AppDetailTabs.Activity
import com.merxury.blocker.core.ui.AppDetailTabs.Info
import com.merxury.blocker.core.ui.AppDetailTabs.Provider
import com.merxury.blocker.core.ui.AppDetailTabs.Receiver
import com.merxury.blocker.core.ui.AppDetailTabs.Service
import com.merxury.blocker.core.ui.TabState
import com.merxury.blocker.core.ui.applist.model.AppItem
import com.merxury.blocker.core.ui.bottomsheet.ComponentSortInfoUiState
import com.merxury.blocker.core.ui.component.ComponentItem
import com.merxury.blocker.core.ui.data.UiMessage
import com.merxury.blocker.core.ui.state.toolbar.AppBarUiState
import org.junit.Rule
import org.junit.Test
import com.merxury.blocker.core.ui.R as uiR

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
    private val receiverComponentList: SnapshotStateList<ComponentItem> = mutableStateListOf(
        ComponentItem(
            name = "AlarmManagerSchedulerBroadcastReceiver",
            simpleName = "AlarmReceiver",
            packageName = "com.merxury.blocker",
            pmBlocked = false,
            type = RECEIVER,
        ),
    )

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

    @Test
    fun tabSelector_whenComponentTabSelected_showSearchMoreIcon() {
        val tabState =
            TabState(
                items = listOf(
                    Info,
                    Receiver,
                ),
                selectedItem = Receiver,
            )
        composeTestRule.setContent {
            BoxWithConstraints {
                AppDetailScreen(
                    appInfoUiState = AppInfoUiState.Success(
                        appInfo = AppItem(label = "App", packageName = "com.merxury.blocker"),
                        appIcon = null,
                    ),
                    topAppBarUiState = AppBarUiState(),
                    componentListUiState = ComponentListUiState(receiver = receiverComponentList),
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
                composeTestRule.activity.resources.getString(uiR.string.core_ui_search_icon),
            )
            .assertExists()
        composeTestRule
            .onNodeWithContentDescription(
                composeTestRule.activity.resources.getString(uiR.string.core_ui_more_menu),
            )
            .assertExists()
    }
}
