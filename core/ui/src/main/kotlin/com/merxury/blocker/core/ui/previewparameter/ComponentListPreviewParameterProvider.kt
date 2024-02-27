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

import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import com.merxury.blocker.core.model.ComponentType.ACTIVITY
import com.merxury.blocker.core.model.ComponentType.RECEIVER
import com.merxury.blocker.core.model.data.ComponentInfo
import com.merxury.blocker.core.ui.previewparameter.ComponentListPreviewParameterData.componentList

class ComponentListPreviewParameterProvider : PreviewParameterProvider<List<ComponentInfo>> {
    override val values: Sequence<List<ComponentInfo>> = sequenceOf(componentList)
}

object ComponentListPreviewParameterData {

    val componentList = listOf(
        ComponentInfo(
            simpleName = "ExampleActivity",
            name = "com.merxury.blocker.feature.appdetail.component.ExampleActivity",
            packageName = "com.merxury.blocker",
            description = "An example activity",
            type = ACTIVITY,
            pmBlocked = true,
            ifwBlocked = true,
            isRunning = true,
        ),
        ComponentInfo(
            name = "ComponentActivity",
            simpleName = "ComponentActivity",
            packageName = "com.merxury.blocker",
            pmBlocked = false,
            ifwBlocked = true,
            isRunning = false,
            type = ACTIVITY,
        ),
        ComponentInfo(
            name = "AlarmManagerSchedulerBroadcast",
            simpleName = "AlarmManagerSchedulerBroadcast",
            packageName = "com.merxury.blocker",
            type = RECEIVER,
        ),
    )
}
