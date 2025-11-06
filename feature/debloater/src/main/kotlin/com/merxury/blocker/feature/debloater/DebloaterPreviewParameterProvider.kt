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

package com.merxury.blocker.feature.debloater

import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import com.merxury.blocker.core.database.debloater.DebloatableComponentEntity
import com.merxury.blocker.core.domain.model.MatchedHeaderData
import com.merxury.blocker.core.model.ComponentType
import com.merxury.blocker.core.model.data.IntentFilterDataInfo
import com.merxury.blocker.core.model.data.IntentFilterInfo

class DebloaterPreviewParameterProvider : PreviewParameterProvider<List<MatchedTarget>> {
    override val values: Sequence<List<MatchedTarget>> = sequenceOf(DebloaterPreviewParameterData.debloaterList)
}

object DebloaterPreviewParameterData {

    private val launcherIntentFilter = IntentFilterInfo(
        actions = listOf("android.intent.action.MAIN"),
        categories = listOf("android.intent.category.LAUNCHER"),
        data = emptyList(),
    )

    private val shareIntentFilter = IntentFilterInfo(
        actions = listOf("android.intent.action.SEND"),
        categories = listOf("android.intent.category.DEFAULT"),
        data = listOf(
            IntentFilterDataInfo(mimeType = "text/plain"),
        ),
    )

    private val deeplinkIntentFilter = IntentFilterInfo(
        actions = listOf("android.intent.action.VIEW"),
        categories = listOf("android.intent.category.DEFAULT", "android.intent.category.BROWSABLE"),
        data = listOf(
            IntentFilterDataInfo(scheme = "https", host = "example.com"),
        ),
    )

    val debloaterComponentUiItem = DebloatableComponentUiItem(
        entity = DebloatableComponentEntity(
            packageName = "com.example.app",
            componentName = "com.example.app.MainActivity",
            simpleName = "MainActivity",
            displayName = "Main Activity",
            ifwBlocked = false,
            pmBlocked = false,
            type = ComponentType.ACTIVITY,
            exported = true,
            label = "Main",
            intentFilters = listOf(launcherIntentFilter, shareIntentFilter, deeplinkIntentFilter),
        ),
        isShareableComponent = true,
        isExplicitLaunch = true,
        isLauncherEntry = true,
        isDeeplinkEntry = true,
    )

    val launcherOnlyComponent = DebloatableComponentUiItem(
        entity = DebloatableComponentEntity(
            packageName = "com.example.app",
            componentName = "com.example.app.LauncherActivity",
            simpleName = "LauncherActivity",
            displayName = "Launcher",
            ifwBlocked = false,
            pmBlocked = false,
            type = ComponentType.ACTIVITY,
            exported = true,
            label = "Launcher",
            intentFilters = listOf(launcherIntentFilter),
        ),
        isShareableComponent = false,
        isExplicitLaunch = true,
        isLauncherEntry = true,
        isDeeplinkEntry = false,
    )

    val blockedComponent = DebloatableComponentUiItem(
        entity = DebloatableComponentEntity(
            packageName = "com.example.app",
            componentName = "com.example.app.BlockedActivity",
            simpleName = "BlockedActivity",
            displayName = "",
            ifwBlocked = true,
            pmBlocked = false,
            type = ComponentType.ACTIVITY,
            exported = false,
            label = null,
            intentFilters = emptyList(),
        ),
        isShareableComponent = false,
        isExplicitLaunch = false,
        isLauncherEntry = false,
        isDeeplinkEntry = false,
    )

    val shareComponent = DebloatableComponentUiItem(
        entity = DebloatableComponentEntity(
            packageName = "com.example.app",
            componentName = "com.example.app.ShareActivity",
            simpleName = "ShareActivity",
            displayName = "Share Handler",
            ifwBlocked = false,
            pmBlocked = false,
            type = ComponentType.ACTIVITY,
            exported = true,
            label = "Share",
            intentFilters = listOf(shareIntentFilter),
        ),
        isShareableComponent = true,
        isExplicitLaunch = true,
        isLauncherEntry = false,
        isDeeplinkEntry = false,
    )

    val deeplinkComponent = DebloatableComponentUiItem(
        entity = DebloatableComponentEntity(
            packageName = "com.example.app",
            componentName = "com.example.app.DeeplinkActivity",
            simpleName = "DeeplinkActivity",
            displayName = "Deeplink Handler",
            ifwBlocked = false,
            pmBlocked = false,
            type = ComponentType.ACTIVITY,
            exported = true,
            label = "Deeplink",
            intentFilters = listOf(deeplinkIntentFilter),
        ),
        isShareableComponent = false,
        isExplicitLaunch = true,
        isLauncherEntry = false,
        isDeeplinkEntry = true,
    )

    val matchedTarget = MatchedTarget(
        header = MatchedHeaderData(
            title = "Example App",
            uniqueId = "com.example.app",
            icon = null,
        ),
        targets = listOf(
            debloaterComponentUiItem,
            launcherOnlyComponent,
            shareComponent,
            deeplinkComponent,
            blockedComponent,
        ),
    )

    val secondMatchedTarget = MatchedTarget(
        header = MatchedHeaderData(
            title = "Another App",
            uniqueId = "com.example.another",
            icon = null,
        ),
        targets = listOf(
            DebloatableComponentUiItem(
                entity = DebloatableComponentEntity(
                    packageName = "com.example.another",
                    componentName = "com.example.another.MainActivity",
                    simpleName = "MainActivity",
                    displayName = "Main Activity",
                    ifwBlocked = false,
                    pmBlocked = false,
                    type = ComponentType.ACTIVITY,
                    exported = true,
                    label = "Main",
                    intentFilters = listOf(launcherIntentFilter),
                ),
                isShareableComponent = false,
                isExplicitLaunch = true,
                isLauncherEntry = true,
                isDeeplinkEntry = false,
            ),
            DebloatableComponentUiItem(
                entity = DebloatableComponentEntity(
                    packageName = "com.example.another",
                    componentName = "com.example.another.BackgroundService",
                    simpleName = "BackgroundService",
                    displayName = "",
                    ifwBlocked = false,
                    pmBlocked = true,
                    type = ComponentType.SERVICE,
                    exported = false,
                    label = null,
                    intentFilters = emptyList(),
                ),
                isShareableComponent = false,
                isExplicitLaunch = false,
                isLauncherEntry = false,
                isDeeplinkEntry = false,
            ),
        ),
    )

    val debloaterList: List<MatchedTarget> = listOf(
        matchedTarget,
        secondMatchedTarget,
    )
}
