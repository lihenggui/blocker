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

package com.merxury.blocker.feature.debloater.impl

import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import com.merxury.blocker.core.model.ComponentType
import com.merxury.blocker.core.model.component.AppKey
import com.merxury.blocker.core.model.component.ComponentId
import com.merxury.blocker.core.model.component.ComponentKey
import com.merxury.blocker.core.model.component.UserId
import com.merxury.blocker.core.ui.component.ComponentRow

class DebloaterPreviewParameterProvider : PreviewParameterProvider<List<MatchedTarget>> {
    override val values: Sequence<List<MatchedTarget>> = sequenceOf(DebloaterPreviewParameterData.debloaterList)
}

object DebloaterPreviewParameterData {
    private val app = AppKey("com.example.app", UserId.SYSTEM)
    private val anotherApp = AppKey("com.example.another", UserId.SYSTEM)

    val debloaterComponentUiItem = previewItem(
        app,
        "MainActivity",
        "Main Activity",
        kinds = setOf(ComponentClassification.SHAREABLE, ComponentClassification.LAUNCHER, ComponentClassification.DEEPLINK),
    )
    val launcherOnlyComponent = previewItem(
        app,
        "LauncherActivity",
        "Launcher",
        kinds = setOf(ComponentClassification.LAUNCHER),
    )
    private val blockedComponent = previewItem(app, "BlockedActivity", "", enabled = false)
    val shareComponent = previewItem(
        app,
        "ShareActivity",
        "Share Handler",
        kinds = setOf(ComponentClassification.SHAREABLE),
    )
    private val deeplinkComponent = previewItem(
        app,
        "DeeplinkActivity",
        "Deeplink Handler",
        kinds = setOf(ComponentClassification.DEEPLINK),
    )

    val matchedTarget = MatchedTarget(
        app = app,
        title = "Example App",
        icon = null,
        targets = listOf(debloaterComponentUiItem, launcherOnlyComponent, shareComponent, deeplinkComponent, blockedComponent),
    )

    private val secondMatchedTarget = MatchedTarget(
        app = anotherApp,
        title = "Another App",
        icon = null,
        targets = listOf(
            previewItem(anotherApp, "MainActivity", "Main Activity", kinds = setOf(ComponentClassification.LAUNCHER)),
            previewItem(anotherApp, "BackgroundService", "", enabled = false, type = ComponentType.SERVICE),
        ),
    )

    val debloaterList = listOf(matchedTarget, secondMatchedTarget)

    private fun previewItem(
        app: AppKey,
        name: String,
        label: String,
        kinds: Set<ComponentClassification> = emptySet(),
        enabled: Boolean = true,
        type: ComponentType = ComponentType.ACTIVITY,
    ): DebloatableComponentUiItem {
        val id = ComponentId(app.packageName, "${app.packageName}.$name")
        return DebloatableComponentUiItem(ComponentKey(id, app.userId), ComponentRow(id, type, enabled), label, kinds)
    }
}
