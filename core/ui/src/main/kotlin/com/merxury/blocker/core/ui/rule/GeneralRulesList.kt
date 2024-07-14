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

package com.merxury.blocker.core.ui.rule

import androidx.compose.foundation.gestures.Orientation.Vertical
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.dp
import com.merxury.blocker.core.designsystem.component.PreviewThemes
import com.merxury.blocker.core.designsystem.component.scrollbar.DraggableScrollbar
import com.merxury.blocker.core.designsystem.component.scrollbar.rememberDraggableScroller
import com.merxury.blocker.core.designsystem.component.scrollbar.scrollbarState
import com.merxury.blocker.core.designsystem.theme.BlockerTheme
import com.merxury.blocker.core.model.data.GeneralRule
import com.merxury.blocker.core.ui.R
import com.merxury.blocker.core.ui.previewparameter.RuleListPreviewParameterProvider

@Composable
fun GeneralRulesList(
    matchedRules: List<GeneralRule>,
    unmatchedRules: List<GeneralRule>,
    modifier: Modifier = Modifier,
    onClick: (String) -> Unit = {},
) {
    val listState = rememberLazyListState()
    val scrollbarState = listState.scrollbarState(
        itemsAvailable = matchedRules.size + unmatchedRules.size,
    )
    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.testTag("rule:list"),
            state = listState,
        ) {
            if (matchedRules.isNotEmpty()) {
                item {
                    RuleItemHeader(title = stringResource(id = R.string.core_ui_found))
                }
                items(matchedRules, key = { it.id }) {
                    RuleItem(
                        item = it,
                        onClick = onClick,
                    )
                }
            }
            if (matchedRules.isNotEmpty() && unmatchedRules.isNotEmpty()) {
                item {
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 8.dp))
                }
            }
            if (unmatchedRules.isNotEmpty()) {
                item {
                    RuleItemHeader(title = stringResource(id = R.string.core_ui_not_found))
                }
                items(unmatchedRules, key = { it.id }) {
                    RuleItem(
                        item = it,
                        onClick = onClick,
                    )
                }
            }
            item {
                Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.safeDrawing))
            }
        }
        listState.DraggableScrollbar(
            modifier = Modifier
                .fillMaxHeight()
                .padding(horizontal = 2.dp)
                .align(Alignment.CenterEnd)
                .testTag("rule:scrollbar"),
            state = scrollbarState,
            orientation = Vertical,
            onThumbMove = listState.rememberDraggableScroller(
                itemsAvailable = matchedRules.size + unmatchedRules.size,
            ),
        )
    }
}

@Composable
@PreviewThemes
private fun GeneralRuleScreenPreview(
    @PreviewParameter(RuleListPreviewParameterProvider::class)
    ruleList: List<GeneralRule>,
) {
    BlockerTheme {
        Surface {
            GeneralRulesList(matchedRules = ruleList, unmatchedRules = ruleList)
        }
    }
}
