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

package com.merxury.blocker.core.ui.previewparameter

import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import com.merxury.blocker.core.model.data.GeneralRule

class RuleListPreviewParameterProvider : PreviewParameterProvider<List<GeneralRule>> {
    override val values: Sequence<List<GeneralRule>> = sequenceOf(
        listOf(
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
            ),
            GeneralRule(
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
                searchKeyword = listOf(
                    "androidx.google.example1",
                    "androidx.google.example2",
                    "androidx.google.example3",
                    "androidx.google.example4",
                ),
            ),
            GeneralRule(
                id = 3,
                name = "Android WorkerManager Test",
            ),
        ),
    )
}
