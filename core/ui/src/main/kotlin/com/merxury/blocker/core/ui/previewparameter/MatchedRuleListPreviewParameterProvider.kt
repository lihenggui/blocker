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

package com.merxury.blocker.core.ui.previewparameter

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import com.merxury.blocker.core.model.ComponentType.ACTIVITY
import com.merxury.blocker.core.model.ComponentType.RECEIVER
import com.merxury.blocker.core.model.data.ComponentInfo
import com.merxury.blocker.core.model.data.GeneralRule
import com.merxury.blocker.core.ui.previewparameter.MatchedRuleListPreviewParameterData.matchRuleList

class MatchedRuleListPreviewParameterProvider :
    PreviewParameterProvider<Map<GeneralRule, SnapshotStateList<ComponentInfo>>> {
    override val values: Sequence<Map<GeneralRule, SnapshotStateList<ComponentInfo>>> =
        sequenceOf(matchRuleList)
}

object MatchedRuleListPreviewParameterData {

    val matchRuleList =
        mapOf(
            GeneralRule(
                id = 1,
                name = "AWS SDK for Kotlin (Developer Preview)",
                iconUrl = null,
                company = "Amazon",
                description = "The AWS SDK for Kotlin simplifies the use of AWS services by " +
                    "providing a set of libraries that are consistent and familiar for " +
                    "Kotlin developers. All AWS SDKs support API lifecycle considerations " +
                    "such as credential management, retries, data marshaling, and serialization.",
                sideEffect = "Unknown",
                safeToBlock = true,
                contributors = listOf("Online contributor"),
                searchKeyword = listOf("androidx.google.example1"),
            ) to mutableStateListOf(
                ComponentInfo(
                    name = "AlarmManagerSchedulerBroadcast",
                    simpleName = "AlarmManagerSchedulerBroadcast",
                    packageName = "com.merxury.blocker",
                    pmBlocked = false,
                    type = RECEIVER,
                ),
                ComponentInfo(
                    name = "ComponentActivity",
                    simpleName = "ComponentActivity",
                    packageName = "com.merxury.blocker",
                    pmBlocked = false,
                    type = ACTIVITY,
                ),
                ComponentInfo(
                    name = "PreviewActivity",
                    simpleName = "PreviewActivity",
                    packageName = "com.merxury.blocker",
                    pmBlocked = false,
                    type = ACTIVITY,
                ),
            ),

            )
}
