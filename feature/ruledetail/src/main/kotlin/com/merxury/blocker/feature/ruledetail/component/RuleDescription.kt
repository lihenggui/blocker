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

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.merxury.blocker.core.designsystem.component.BlockerBodyLargeText
import com.merxury.blocker.core.designsystem.theme.BlockerTheme
import com.merxury.blocker.core.model.data.GeneralRule
import com.merxury.blocker.feature.ruledetail.R
import com.merxury.blocker.feature.ruledetail.R.string
import dev.jeziellago.compose.markdowntext.MarkdownText

@Composable
fun RuleDescription(
    modifier: Modifier = Modifier,
    rule: GeneralRule,
) {
    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = modifier
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
                .testTag("ruleDetail:description"),
        ) {
            MarkdownText(
                modifier = Modifier.padding(vertical = 6.dp),
                markdown = if (rule.description.isNullOrBlank()) {
                    stringResource(id = string.feature_ruledetail_no_description)
                } else {
                    rule.description.orEmpty()
                },
                style = MaterialTheme.typography.bodyLarge.copy(
                    color = MaterialTheme.colorScheme.onSurface,
                ),
                linkColor = MaterialTheme.colorScheme.tertiary,
                isTextSelectable = true,
            )
            InfoItemHeading(
                heading = stringResource(id = string.feature_ruledetail_safe_to_block),
                description = listOf(
                    when (rule.safeToBlock) {
                        null -> stringResource(id = string.feature_ruledetail_unknown)
                        true -> stringResource(id = string.feature_ruledetail_yes)
                        false -> stringResource(id = string.feature_ruledetail_no)
                    },
                ),
            )
            InfoItemHeading(
                heading = stringResource(id = string.feature_ruledetail_side_effect),
                description = listOf(
                    rule.sideEffect ?: stringResource(id = string.feature_ruledetail_unknown),
                ),
            )
            InfoItemHeading(
                heading = stringResource(id = string.feature_ruledetail_keywords),
                description = rule.searchKeyword,
            )
            InfoItemHeading(
                heading = stringResource(id = string.feature_ruledetail_contributors),
                description = rule.contributors,
            )
        }
    }
}

@Composable
fun InfoItemHeading(
    heading: String? = null,
    description: List<String?>,
) {
    Column(modifier = Modifier.padding(vertical = 6.dp)) {
        if (heading != null) {
            Text(
                text = heading,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(vertical = 16.dp),
            )
        }
        description.forEach { string ->
            BlockerBodyLargeText(
                text = if (string.isNullOrEmpty()) {
                    stringResource(id = R.string.feature_ruledetail_no_description)
                } else {
                    string
                },
            )
        }
    }
}

@Composable
@Preview
fun RuleDescriptionPreview() {
    val item = GeneralRule(
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
        Surface {
            RuleDescription(rule = item)
        }
    }
}
