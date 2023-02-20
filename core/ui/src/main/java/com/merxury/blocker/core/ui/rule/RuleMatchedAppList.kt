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

@file:JvmName("RuleMatchedAppKt")

package com.merxury.blocker.core.ui.rule

import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.merxury.blocker.core.designsystem.theme.BlockerTheme
import com.merxury.blocker.core.model.ComponentType.ACTIVITY
import com.merxury.blocker.core.ui.applist.model.AppItem
import com.merxury.blocker.core.ui.component.ComponentItem

@Composable
fun RuleMatchedAppList(
    ruleMatchedAppListUiState: RuleMatchedAppListUiState,
    onStopServiceClick: (String, String) -> Unit,
    onLaunchActivityClick: (String, String) -> Unit,
    onCopyNameClick: (String) -> Unit,
    onCopyFullNameClick: (String) -> Unit,
    onSwitch: (String, String, Boolean) -> Unit,
) {
    when (ruleMatchedAppListUiState) {
        RuleMatchedAppListUiState.Loading -> {}
        is RuleMatchedAppListUiState.Success -> {
            LazyColumn {
                items(ruleMatchedAppListUiState.list, key = { it }) { ruleMatchedApp ->
                    MatchedComponentItem(
                        ruleMatchedApp = ruleMatchedApp,
                        onStopServiceClick = onStopServiceClick,
                        onLaunchActivityClick = onLaunchActivityClick,
                        onCopyNameClick = onCopyNameClick,
                        onCopyFullNameClick = onCopyFullNameClick,
                        onSwitch = onSwitch,
                    )
                }
            }
        }
    }
}

sealed interface RuleMatchedAppListUiState {
    object Loading : RuleMatchedAppListUiState
    data class Success(
        val list: List<RuleMatchedApp>,
    ) : RuleMatchedAppListUiState
}

@Composable
@Preview
fun RuleMatchedAppListPreview() {
    val componentInfo = ComponentItem(
        name = "component",
        simpleName = "com",
        packageName = "blocker",
        type = ACTIVITY,
        pmBlocked = false,
    )
    val ruleMatchedApp = RuleMatchedApp(
        app = AppItem(
            packageName = "com.merxury.blocker",
            label = "Blocker",
            isSystem = false,
        ),
        componentList = listOf(componentInfo),
    )
    val uiState = RuleMatchedAppListUiState.Success(
        list = listOf(ruleMatchedApp),
    )
    BlockerTheme {
        Surface {
            RuleMatchedAppList(
                ruleMatchedAppListUiState = uiState,
                onStopServiceClick = { _, _ -> },
                onLaunchActivityClick = { _, _ -> },
                onCopyNameClick = {},
                onCopyFullNameClick = {},
                onSwitch = { _, _, _ -> },
            )
        }
    }
}
