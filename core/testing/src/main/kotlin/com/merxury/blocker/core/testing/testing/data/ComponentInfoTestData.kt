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

package com.merxury.blocker.core.testing.testing.data

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.snapshots.SnapshotStateList
import com.merxury.blocker.core.model.ComponentType.ACTIVITY
import com.merxury.blocker.core.model.ComponentType.RECEIVER
import com.merxury.blocker.core.model.data.ComponentInfo
import com.merxury.blocker.core.model.data.ComponentItem

val receiverTestData: SnapshotStateList<ComponentItem> = mutableStateListOf(
    ComponentItem(
        name = "AlarmManagerSchedulerBroadcast",
        simpleName = "AlarmManagerSchedulerBroadcast",
        packageName = "com.merxury.blocker",
        pmBlocked = false,
        type = RECEIVER,
    ),
)

val activityItemsTestData: SnapshotStateList<ComponentItem> = mutableStateListOf(
    ComponentItem(
        name = "ComponentActivity",
        simpleName = "ComponentActivity",
        packageName = "com.merxury.blocker",
        pmBlocked = false,
        type = ACTIVITY,
    ),
    ComponentItem(
        name = "PreviewActivity",
        simpleName = "PreviewActivity",
        packageName = "com.merxury.blocker",
        pmBlocked = false,
        type = ACTIVITY,
    ),
)

val activityInfoTestData: SnapshotStateList<ComponentInfo> = mutableStateListOf(
    ComponentInfo(
        name = "ComponentActivity",
        simpleName = "ComponentActivity",
        packageName = "com.merxury.blocker",
        pmBlocked = false,
        exported = false,
        type = ACTIVITY,
    ),
    ComponentInfo(
        name = "PreviewActivity",
        simpleName = "PreviewActivity",
        packageName = "com.merxury.blocker",
        pmBlocked = false,
        exported = false,
        type = ACTIVITY,
    ),
)
