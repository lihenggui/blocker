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

package com.merxury.blocker.core.ui.previewparameter

import com.merxury.blocker.core.model.ComponentType.ACTIVITY
import com.merxury.blocker.core.model.ComponentType.RECEIVER
import com.merxury.blocker.core.model.component.ComponentId
import com.merxury.blocker.core.ui.component.ComponentRow

fun previewComponentRows(): List<ComponentRow> = listOf(
    ComponentRow(
        id = ComponentId("com.merxury.blocker", "com.merxury.blocker.feature.appdetail.component.ExampleActivity"),
        type = ACTIVITY,
        enabled = false,
        isRunning = true,
        description = "An example activity",
    ),
    ComponentRow(
        id = ComponentId("com.merxury.blocker", "ComponentActivity"),
        type = ACTIVITY,
        enabled = false,
    ),
    ComponentRow(
        id = ComponentId("com.merxury.blocker", "AlarmManagerSchedulerBroadcast"),
        type = RECEIVER,
        enabled = true,
    ),
)
