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

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.constraintlayout.compose.ConstraintLayout
import androidx.constraintlayout.compose.Dimension
import coil.compose.AsyncImage
import coil.request.ImageRequest.Builder
import com.merxury.blocker.core.designsystem.component.BlockerBodyLargeText
import com.merxury.blocker.core.designsystem.component.BlockerBodyMediumText
import com.merxury.blocker.core.designsystem.component.BlockerLabelSmallText
import com.merxury.blocker.core.designsystem.icon.BlockerIcons
import com.merxury.blocker.core.designsystem.theme.BlockerTheme
import com.merxury.blocker.core.ui.R.plurals
import com.merxury.blocker.core.ui.R.string

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RuleCard(
    item: GeneralRuleWithApp,
    onCardClick: (Int) -> Unit = { },
) {
    OutlinedCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp),
        onClick = { onCardClick(item.id) },
    ) {
        ConstraintLayout(modifier = Modifier.fillMaxWidth()) {
            val (icon, name, matchedCount, company, ruleIcon, keywords) = createRefs()
            val iconEndGuideline = createGuidelineFromStart(72.dp)
            AsyncImage(
                modifier = Modifier
                    .constrainAs(icon) {
                        top.linkTo(parent.top)
                        start.linkTo(parent.start)
                    }
                    .padding(16.dp)
                    .size(40.dp),
                model = Builder(LocalContext.current)
                    .data(item.iconUrl)
                    .error(BlockerIcons.Android)
                    .placeholder(BlockerIcons.Android)
                    .crossfade(true)
                    .build(),
                contentDescription = stringResource(id = string.rule_icon_description),
            )
            BlockerBodyLargeText(
                text = item.name,
                modifier = Modifier
                    .padding(top = 16.dp, end = 16.dp)
                    .constrainAs(name) {
                        linkTo(
                            start = iconEndGuideline,
                            end = matchedCount.start,
                            bias = 0F,
                        )
                        top.linkTo(icon.top)
                        width = Dimension.fillToConstraints
                    },
            )
            val indicatorColor = MaterialTheme.colorScheme.tertiary
            BlockerLabelSmallText(
                modifier = Modifier
                    .drawBehind {
                        drawRoundRect(
                            color = indicatorColor,
                            cornerRadius = CornerRadius(x = 4.dp.toPx(), y = 4.dp.toPx()),
                        )
                    }
                    .padding(horizontal = 2.dp, vertical = 1.dp)
                    .constrainAs(matchedCount) {
                        linkTo(
                            top = name.top,
                            bottom = name.bottom,
                            topMargin = 16.dp,
                        )
                        linkTo(
                            start = name.end,
                            end = parent.end,
                            endMargin = 16.dp,
                        )
                    },
                text = pluralStringResource(
                    id = plurals.matched_apps,
                    item.matchedAppCount,
                    item.matchedAppCount,
                ),
                color = MaterialTheme.colorScheme.onTertiary,
            )
            item.company?.let {
                BlockerBodyMediumText(
                    text = it,
                    modifier = Modifier
                        .constrainAs(company) {
                            linkTo(
                                start = iconEndGuideline,
                                end = parent.end,
                                bias = 0F,
                            )
                            top.linkTo(name.bottom)
                            width = Dimension.fillToConstraints
                        }
                        .padding(top = 4.dp, end = 16.dp),
                )
            }
            val titleBarrier = createBottomBarrier(icon, name, company)
            Icon(
                modifier = Modifier
                    .padding(bottom = 16.dp)
                    .size(16.dp)
                    .constrainAs(ruleIcon) {
                        top.linkTo(titleBarrier)
                        start.linkTo(parent.start)
                        end.linkTo(iconEndGuideline)
                    },
                imageVector = BlockerIcons.SubdirectoryArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.outline,
            )
            BlockerBodyMediumText(
                text = item.searchKeyword.joinToString("\n"),
                modifier = Modifier
                    .padding(end = 16.dp, bottom = 16.dp)
                    .constrainAs(keywords) {
                        linkTo(
                            start = iconEndGuideline,
                            end = parent.end,
                            bias = 0F,
                        )
                        top.linkTo(titleBarrier)
                        width = Dimension.fillToConstraints
                    },
            )
        }
    }
}

@Composable
@Preview
fun RuleBasicInfoPreview() {
    val item = GeneralRuleWithApp(
        id = 2,
        name = "Android WorkerManager",
        iconUrl = null,
        company = "Google",
        description = "WorkManager is the recommended solution for persistent work. " +
            "Work is persistent when it remains scheduled through app restarts and " +
            "system reboots. Because most background processing is best accomplished " +
            "through persistent work, WorkManager is the primary recommended API for " +
            "background processing.",
        sideEffect = "Background works won't be able to execute",
        safeToBlock = false,
        contributors = listOf("Google"),
        searchKeyword = listOf("androidx.work.", "androidx.work.impl"),
    )
    BlockerTheme {
        RuleCard(item = item)
    }
}
