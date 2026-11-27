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

package com.merxury.blocker.feature.ruledetail.impl

import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import com.merxury.blocker.core.designsystem.component.PreviewThemes
import com.merxury.blocker.core.designsystem.theme.BlockerTheme
import com.merxury.blocker.core.model.data.GeneralRule
import com.merxury.blocker.core.ui.applist.previewAppRows
import com.merxury.blocker.core.ui.data.UiMessage
import com.merxury.blocker.core.ui.previewparameter.RuleListPreviewParameterProvider
import com.merxury.blocker.core.ui.previewparameter.previewComponentRows
import com.merxury.blocker.feature.ruledetail.impl.AppBarAction.MORE
import com.merxury.blocker.feature.ruledetail.impl.AppBarAction.SORT

/** One matched app, the first preview app with the preview components, shown with the Android icon. */
internal fun previewMatchedApps(): MatchedAppsUiState.Loaded {
    val app = previewAppRows().first()
    return MatchedAppsUiState.Loaded(listOf(MatchedApp(app = app.key, label = app.label, icon = null, rows = previewComponentRows())))
}

@Composable
@PreviewThemes
private fun RuleDetailScreenPreview(
    @PreviewParameter(RuleListPreviewParameterProvider::class)
    ruleList: List<GeneralRule>,
) {
    val tabState = RuleDetailTabStatePreviewParameterProvider().values.first()
    BlockerTheme {
        Surface {
            RuleDetailScreen(
                showBackButton = false,
                ruleInfoUiState = RuleInfoUiState.Success(ruleInfo = ruleList.first(), matchedAppsUiState = previewMatchedApps()),
                tabState = tabState[0],
                appBarUiState = AppBarUiState(actions = listOf(SORT, MORE)),
            )
        }
    }
}

@Composable
@PreviewThemes
private fun RuleDetailScreenSelectedDescriptionPreview(
    @PreviewParameter(RuleListPreviewParameterProvider::class)
    ruleList: List<GeneralRule>,
) {
    val tabState = RuleDetailTabStatePreviewParameterProvider().values.first()
    BlockerTheme {
        Surface {
            RuleDetailScreen(
                ruleInfoUiState = RuleInfoUiState.Success(ruleInfo = ruleList.first(), matchedAppsUiState = previewMatchedApps()),
                tabState = tabState[1],
            )
        }
    }
}

@Composable
@Preview
private fun RuleDetailScreenWithApplicableLoadingPreview(
    @PreviewParameter(RuleListPreviewParameterProvider::class)
    ruleList: List<GeneralRule>,
) {
    val tabState = RuleDetailTabStatePreviewParameterProvider().values.first()
    BlockerTheme {
        Surface {
            RuleDetailScreen(
                ruleInfoUiState = RuleInfoUiState.Success(ruleInfo = ruleList.first(), matchedAppsUiState = MatchedAppsUiState.Loading),
                tabState = tabState[0],
            )
        }
    }
}

@Composable
@Preview
private fun RuleDetailScreenLoadingPreview() {
    val tabState = RuleDetailTabStatePreviewParameterProvider().values.first()
    BlockerTheme {
        Surface {
            RuleDetailScreen(
                ruleInfoUiState = RuleInfoUiState.Loading,
                tabState = tabState[0],
            )
        }
    }
}

@Composable
@Preview
private fun RuleDetailScreenErrorPreview() {
    val tabState = RuleDetailTabStatePreviewParameterProvider().values.first()
    BlockerTheme {
        Surface {
            RuleDetailScreen(
                ruleInfoUiState = RuleInfoUiState.Error(error = UiMessage("Error")),
                tabState = tabState[0],
            )
        }
    }
}
