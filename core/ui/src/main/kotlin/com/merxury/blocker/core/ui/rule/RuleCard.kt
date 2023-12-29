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

package com.merxury.blocker.core.ui.rule

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest.Builder
import com.merxury.blocker.core.designsystem.component.BlockerBodyLargeText
import com.merxury.blocker.core.designsystem.component.BlockerBodyMediumText
import com.merxury.blocker.core.designsystem.component.BlockerLabelSmallText
import com.merxury.blocker.core.designsystem.component.BlockerOutlinedCard
import com.merxury.blocker.core.designsystem.component.ThemePreviews
import com.merxury.blocker.core.designsystem.icon.BlockerIcons
import com.merxury.blocker.core.designsystem.theme.BlockerTheme
import com.merxury.blocker.core.model.data.GeneralRule
import com.merxury.blocker.core.ui.DevicePreviews
import com.merxury.blocker.core.ui.R.plurals
import com.merxury.blocker.core.ui.R.string
import com.merxury.blocker.core.ui.previewparameter.RuleListPreviewParameterProvider

@Composable
fun RuleCard(
    item: GeneralRule,
    onCardClick: (String) -> Unit = { },
) {
    BlockerOutlinedCard(
        modifier = Modifier,
        onClick = { onCardClick(item.id.toString()) },
    ) {
        CardHeader(
            iconUrl = item.iconUrl,
            name = item.name,
            matchedAppCount = item.matchedAppCount,
            company = item.company,
        )
        CardContent(searchKeyword = item.searchKeyword)
    }
}

@Composable
private fun CardHeader(iconUrl: String?, name: String, matchedAppCount: Int, company: String?) {
    Row {
        AsyncImage(
            modifier = Modifier
                .padding(16.dp)
                .size(40.dp),
            model = Builder(LocalContext.current)
                .data(iconUrl)
                .error(BlockerIcons.Android)
                .placeholder(BlockerIcons.Android)
                .crossfade(true)
                .build(),
            contentDescription = stringResource(id = string.core_ui_rule_icon_description),
        )
        Column(modifier = Modifier.weight(1f)) {
            BlockerBodyLargeText(
                text = name,
                modifier = Modifier.padding(top = 16.dp, end = 16.dp),
            )
            company?.let {
                BlockerBodyMediumText(
                    text = it,
                    modifier = Modifier.padding(top = 4.dp, end = 16.dp),
                )
            }
        }
        val indicatorColor = MaterialTheme.colorScheme.tertiary
        Row(
            modifier = Modifier
                .fillMaxWidth(0.25f),
            horizontalArrangement = Arrangement.End,
        ) {
            BlockerLabelSmallText(
                modifier = Modifier
                    .padding(top = 16.dp, end = 16.dp)
                    .drawBehind {
                        drawRoundRect(
                            color = indicatorColor,
                            cornerRadius = CornerRadius(x = 4.dp.toPx(), y = 4.dp.toPx()),
                        )
                    }
                    .padding(horizontal = 2.dp, vertical = 1.dp),
                text = pluralStringResource(
                    id = plurals.core_ui_matched_apps,
                    matchedAppCount,
                    matchedAppCount,
                ),
                color = MaterialTheme.colorScheme.onTertiary,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun CardContent(searchKeyword: List<String>) {
    Row {
        Icon(
            modifier = Modifier
                .padding(top = 9.dp, bottom = 8.dp, start = 16.dp, end = 40.dp)
                .size(16.dp),
            imageVector = BlockerIcons.SubdirectoryArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.outline,
        )
        BlockerBodyMediumText(
            text = searchKeyword.joinToString("\n"),
            modifier = Modifier.padding(end = 16.dp, bottom = 16.dp, top = 8.dp),
        )
    }
}

@Composable
@ThemePreviews
fun RuleBasicInfoPreview(
    @PreviewParameter(RuleListPreviewParameterProvider::class)
    ruleList: List<GeneralRule>,
) {
    BlockerTheme {
        RuleCard(item = ruleList[1])
    }
}

@Composable
@DevicePreviews
fun RuleSimpleInfoPreview(
    @PreviewParameter(RuleListPreviewParameterProvider::class)
    ruleList: List<GeneralRule>,
) {
    BlockerTheme {
        RuleCard(item = ruleList[2])
    }
}
