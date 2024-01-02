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

package com.merxury.blocker.feature.ruledetail.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.merxury.blocker.core.designsystem.component.BlockerBodyLargeText
import com.merxury.blocker.core.designsystem.theme.BlockerTheme
import com.merxury.blocker.core.model.ComponentType.ACTIVITY
import com.merxury.blocker.core.model.data.ComponentItem
import com.merxury.blocker.core.ui.R.string
import com.merxury.blocker.core.ui.collapseList.CollapsibleList
import com.merxury.blocker.core.ui.rule.MatchedHeaderData
import com.merxury.blocker.core.ui.rule.MatchedItem
import com.merxury.blocker.core.ui.screen.LoadingScreen
import com.merxury.blocker.feature.ruledetail.RuleMatchedAppListUiState
import com.merxury.blocker.feature.ruledetail.RuleMatchedAppListUiState.Loading
import com.merxury.blocker.feature.ruledetail.RuleMatchedAppListUiState.Success

@Composable
fun RuleMatchedAppList(
    modifier: Modifier = Modifier,
    ruleMatchedAppListUiState: RuleMatchedAppListUiState,
    onStopServiceClick: (String, String) -> Unit = { _, _ -> },
    onLaunchActivityClick: (String, String) -> Unit = { _, _ -> },
    onCopyNameClick: (String) -> Unit = { _ -> },
    onCopyFullNameClick: (String) -> Unit = { _ -> },
    navigateToAppDetail: (String) -> Unit = { _ -> },
    onBlockAllClick: (List<ComponentItem>) -> Unit = { _ -> },
    onEnableAllClick: (List<ComponentItem>) -> Unit = { _ -> },
    onSwitch: (String, String, Boolean) -> Unit = { _, _, _ -> },
) {
    when (ruleMatchedAppListUiState) {
        Loading -> LoadingScreen()
        is Success -> {
            if (ruleMatchedAppListUiState.list.isEmpty()) {
                NoApplicableAppScreen()
                return
            }
            CollapsibleList(
                modifier = modifier.testTag("rule:matchedAppList"),
                list = ruleMatchedAppListUiState.list,
                navigateToDetail = navigateToAppDetail,
                navigationMenuItemDesc = string.core_ui_open_app_detail,
                onStopServiceClick = onStopServiceClick,
                onLaunchActivityClick = onLaunchActivityClick,
                onCopyNameClick = onCopyNameClick,
                onCopyFullNameClick = onCopyFullNameClick,
                onBlockAllClick = onBlockAllClick,
                onEnableAllClick = onEnableAllClick,
                onSwitch = onSwitch,
            )
        }
    }
}

@Composable
fun NoApplicableAppScreen() {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        BlockerBodyLargeText(
            text = stringResource(id = string.core_ui_no_applicable_app),
            color = MaterialTheme.colorScheme.outline,
        )
    }
}

@Composable
@Preview
fun RuleMatchedAppListPreview() {
    val componentInfo = ComponentItem(
        name = ".ui.component.ComponentListActivity",
        simpleName = "ComponentListItem",
        packageName = "com.merxury.blocker.test1",
        type = ACTIVITY,
        pmBlocked = false,
    )
    val matchedItem = MatchedItem(
        header = MatchedHeaderData(
            title = "Blocker",
            uniqueId = "com.merxury.blocker.test1",
        ),
        componentList = remember {
            mutableStateListOf(componentInfo)
        },
    )
    val matchedItem2 = MatchedItem(
        header = MatchedHeaderData(
            title = "Test long long long long long name",
            uniqueId = "com.merxury.blocker.test2",
        ),
        componentList = remember {
            mutableStateListOf()
        },
    )
    val uiState = Success(
        list = remember {
            mutableStateListOf(matchedItem, matchedItem2)
        },
    )
    BlockerTheme {
        Surface {
            RuleMatchedAppList(
                ruleMatchedAppListUiState = uiState,
            )
        }
    }
}
