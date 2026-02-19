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

package com.merxury.blocker.feature.appdetail.impl.sdk

import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.tooling.preview.PreviewParameter
import com.merxury.blocker.core.designsystem.component.PreviewThemes
import com.merxury.blocker.core.designsystem.theme.BlockerTheme
import com.merxury.blocker.core.domain.model.MatchedHeaderData
import com.merxury.blocker.core.domain.model.MatchedItem
import com.merxury.blocker.core.model.data.ComponentInfo
import com.merxury.blocker.core.result.Result
import com.merxury.blocker.core.ui.collapseList.CollapsibleList
import com.merxury.blocker.core.ui.component.NoComponentScreen
import com.merxury.blocker.core.ui.data.UiMessage
import com.merxury.blocker.core.ui.previewparameter.ComponentListPreviewParameterProvider
import com.merxury.blocker.core.ui.previewparameter.RuleListPreviewParameterProvider
import com.merxury.blocker.core.ui.screen.ErrorScreen
import com.merxury.blocker.core.ui.screen.LoadingScreen
import com.merxury.blocker.core.ui.R as uiR

@Composable
fun SdkContent(
    modifier: Modifier = Modifier,
    data: Result<List<MatchedItem>> = Result.Loading,
    navigateToRuleDetail: (String) -> Unit = {},
    onStopServiceClick: (String, String) -> Unit = { _, _ -> },
    onLaunchActivityClick: (String, String) -> Unit = { _, _ -> },
    onCopyNameClick: (String) -> Unit = { _ -> },
    onCopyFullNameClick: (String) -> Unit = { _ -> },
    onBlockAllInItemClick: (List<ComponentInfo>) -> Unit = { _ -> },
    onEnableAllInItemClick: (List<ComponentInfo>) -> Unit = { _ -> },
    onSwitch: (ComponentInfo, Boolean) -> Unit = { _, _ -> },
) {
    when (data) {
        is Result.Success -> {
            val sdks = data.data
            if (sdks.isEmpty()) {
                NoComponentScreen()
                return
            }
            CollapsibleList(
                modifier = modifier.testTag("app:sdkList"),
                list = sdks,
                navigateToDetail = navigateToRuleDetail,
                navigationMenuItemDesc = uiR.string.core_ui_open_rule_detail,
                onStopServiceClick = onStopServiceClick,
                onLaunchActivityClick = onLaunchActivityClick,
                onCopyNameClick = onCopyNameClick,
                onCopyFullNameClick = onCopyFullNameClick,
                onBlockAllInItemClick = onBlockAllInItemClick,
                onEnableAllInItemClick = onEnableAllInItemClick,
                onSwitch = onSwitch,
            )
        }

        is Result.Error -> ErrorScreen(error = UiMessage(title = data.exception.message.orEmpty()))

        is Result.Loading -> LoadingScreen()
    }
}

@Composable
@PreviewThemes
private fun SdkContentPreview(
    @PreviewParameter(
        ComponentListPreviewParameterProvider::class,
    ) components: List<ComponentInfo>,
) {
    val rule = RuleListPreviewParameterProvider().values.first()[0]
    val data: Result<List<MatchedItem>> = Result.Success(
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
    BlockerTheme {
        Surface {
            SdkContent(data = data)
        }
    }
}

@Composable
@PreviewThemes
private fun SdkContentLoadingPreview() {
    BlockerTheme {
        Surface {
            SdkContent()
        }
    }
}
