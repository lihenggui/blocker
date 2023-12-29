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

package com.merxury.blocker.feature.appdetail.sdk

import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.tooling.preview.PreviewParameter
import com.merxury.blocker.core.designsystem.component.ThemePreviews
import com.merxury.blocker.core.designsystem.theme.BlockerTheme
import com.merxury.blocker.core.model.data.ComponentItem
import com.merxury.blocker.core.model.data.GeneralRule
import com.merxury.blocker.core.result.Result
import com.merxury.blocker.core.ui.collapseList.CollapsibleList
import com.merxury.blocker.core.ui.component.NoComponentScreen
import com.merxury.blocker.core.ui.data.UiMessage
import com.merxury.blocker.core.ui.previewparameter.ComponentListPreviewParameterProvider
import com.merxury.blocker.core.ui.previewparameter.RuleListPreviewParameterProvider
import com.merxury.blocker.core.ui.rule.MatchedHeaderData
import com.merxury.blocker.core.ui.rule.MatchedItem
import com.merxury.blocker.core.ui.screen.ErrorScreen
import com.merxury.blocker.core.ui.screen.LoadingScreen
import com.merxury.blocker.core.ui.R as uiR

@Composable
fun SdkContent(
    modifier: Modifier = Modifier,
    data: Result<Map<GeneralRule, SnapshotStateList<ComponentItem>>> = Result.Loading,
    navigationToRuleDetail: (Int) -> Unit = {},
) {
    when (data) {
        is Result.Success -> {
            val sdks = data.data
            if (sdks.isEmpty()) {
                NoComponentScreen()
                return
            }
            // Transfer Map<GeneralRule, SnapshotStateList<ComponentItem>> to SnapshotStateList<MatchedItem>
            val matchedList: MutableList<MatchedItem> = mutableListOf()
            sdks.forEach { (rule, components) ->
                val matchedItem = MatchedItem(
                    header = MatchedHeaderData(
                        title = rule.name,
                        uniqueId = rule.id.toString(),
                        icon = rule.iconUrl,
                    ),
                    componentList = components,
                )
                matchedList.add(matchedItem)
            }
            CollapsibleList(
                modifier = modifier.testTag("app:sdkList"),
                list = matchedList.toMutableStateList(),
                navigateToRuleDetail = navigationToRuleDetail,
                navigationMenuItemDesc = uiR.string.core_ui_open_rule_detail,
                onAppDetailScreen = true,
            )
        }

        is Result.Error -> ErrorScreen(error = UiMessage(title = data.exception.message.orEmpty()))

        is Result.Loading -> LoadingScreen()
    }
}

@Composable
@ThemePreviews
fun SdkContentPreview(
    @PreviewParameter(
        ComponentListPreviewParameterProvider::class,
    ) components: List<ComponentItem>,
) {
    val rule = RuleListPreviewParameterProvider().values.first()[0]
    val data: Result<Map<GeneralRule, SnapshotStateList<ComponentItem>>> = Result.Success(
        data = mapOf(
            rule to components.toMutableStateList(),
        ),
    )
    BlockerTheme {
        Surface {
            SdkContent(data = data)
        }
    }
}

@Composable
@ThemePreviews
fun SdkContentLoadingPreview() {
    BlockerTheme {
        Surface {
            SdkContent()
        }
    }
}
