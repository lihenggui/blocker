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
import com.merxury.blocker.core.model.data.AppItem
import com.merxury.blocker.core.model.data.AppServiceStatus
import com.merxury.blocker.core.ui.previewparameter.PreviewParameterData.appList
import kotlinx.datetime.Instant

class AppListPreviewParameterProvider : PreviewParameterProvider<List<AppItem>> {
    override val values: Sequence<List<AppItem>> = sequenceOf(appList)
}

object PreviewParameterData {

    private val appServiceStatus = AppServiceStatus(
        packageName = "com.merxury.blocker",
        running = 1,
        blocked = 2,
        total = 3,
    )

    val appList: List<AppItem> = listOf(
        AppItem(
            label = "Blocker",
            packageName = "com.merxury.blocker",
            versionName = "1.0.0",
            versionCode = 1,
            isEnabled = false,
            isRunning = true,
            appServiceStatus = appServiceStatus,
            firstInstallTime = Instant.fromEpochMilliseconds(0),
            lastUpdateTime = Instant.fromEpochMilliseconds(0),
        ),
        AppItem(
            label = "Blocker Test",
            packageName = "com.test.blocker",
            versionName = "11.0.0(1.1)",
            versionCode = 11,
            isEnabled = false,
            isRunning = false,
            firstInstallTime = Instant.fromEpochMilliseconds(0),
            lastUpdateTime = Instant.fromEpochMilliseconds(0),
        ),
        AppItem(
            label = "Blocker Test test long long long long name",
            packageName = "com.test",
            versionName = "0.1.1",
            versionCode = 11,
            isEnabled = true,
            isRunning = true,
            appServiceStatus = appServiceStatus,
            firstInstallTime = Instant.fromEpochMilliseconds(0),
            lastUpdateTime = Instant.fromEpochMilliseconds(0),
        ),
    )
}
