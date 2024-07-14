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

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest.Builder
import com.merxury.blocker.core.designsystem.component.BlockerBodyLargeText
import com.merxury.blocker.core.designsystem.component.BlockerBodyMediumText
import com.merxury.blocker.core.designsystem.component.PreviewThemes
import com.merxury.blocker.core.designsystem.icon.BlockerIcons
import com.merxury.blocker.core.designsystem.theme.BlockerTheme
import com.merxury.blocker.core.model.data.GeneralRule
import com.merxury.blocker.core.ui.R.string
import com.merxury.blocker.core.ui.previewparameter.RuleListPreviewParameterProvider

@Composable
fun RuleItem(
    item: GeneralRule,
    modifier: Modifier = Modifier,
    onClick: (String) -> Unit = { },
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .clickable { onClick(item.id.toString()) }
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        RuleIcon(iconUrl = item.iconUrl)
        Spacer(modifier = Modifier.width(16.dp))
        RuleInfo(
            name = item.name,
            company = item.company,
            modifier = Modifier.weight(1F),
        )
        if (item.matchedAppCount > 0) {
            Spacer(modifier = Modifier.width(16.dp))
            MatchedAppIcon(matchedAppCount = item.matchedAppCount)
        }
    }
}

@Composable
private fun RuleIcon(iconUrl: String?) {
    AsyncImage(
        modifier = Modifier
            .size(48.dp),
        model = Builder(LocalContext.current)
            .data(iconUrl)
            .error(BlockerIcons.Android)
            .placeholder(BlockerIcons.Android)
            .crossfade(true)
            .build(),
        contentDescription = stringResource(id = string.core_ui_rule_icon_description),
    )
}

@Composable
private fun RuleInfo(
    name: String,
    company: String?,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        BlockerBodyLargeText(
            text = name,
        )
        company?.let {
            BlockerBodyMediumText(
                text = it,
            )
        }
    }
}

@Composable
private fun MatchedAppIcon(matchedAppCount: Int) {
    Text(
        text = matchedAppCount.toString(),
        textAlign = TextAlign.Center,
        fontSize = 12.sp,
        modifier = Modifier
            .size(24.dp)
            .background(
                color = MaterialTheme.colorScheme.secondaryContainer,
                shape = CircleShape,
            ),
    )
}

@Composable
@PreviewThemes
private fun RuleBasicInfoPreview(
    @PreviewParameter(RuleListPreviewParameterProvider::class)
    ruleList: List<GeneralRule>,
) {
    BlockerTheme {
        Surface {
            RuleItem(item = ruleList[0])
        }
    }
}

@Composable
@PreviewThemes
private fun RulePreview(
    @PreviewParameter(RuleListPreviewParameterProvider::class)
    ruleList: List<GeneralRule>,
) {
    BlockerTheme {
        Surface {
            RuleItem(item = ruleList[2])
        }
    }
}
