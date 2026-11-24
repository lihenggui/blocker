/*
 * Copyright 2026 Blocker
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

package com.merxury.blocker.feature.appdetail.impl

import com.merxury.blocker.core.model.ComponentType
import com.merxury.blocker.core.model.component.BlockReason
import com.merxury.blocker.core.model.component.ComponentId
import com.merxury.blocker.core.ui.coil.ApkIcon
import kotlin.time.Instant

/* Data for previews and screenshot tests: one row in each state a component can show. */

private const val PREVIEW_PACKAGE = "com.merxury.blocker"

internal val previewHeaderApp = HeaderApp(
    packageName = PREVIEW_PACKAGE,
    label = "Blocker",
    versionName = "2.0.4",
    versionCode = 2004,
    icon = ApkIcon(PREVIEW_PACKAGE, apkPath = null, versionCode = 2004, uid = null),
    uid = 10_123,
    targetSdk = 36,
    minSdk = 24,
    lastUpdateTime = Instant.fromEpochMilliseconds(1_700_000_000_000),
    dataDir = "/data/user/0/$PREVIEW_PACKAGE",
)

private fun previewRow(
    name: String,
    type: ComponentType,
    switch: SwitchState,
    blockedBy: Set<BlockReason> = emptySet(),
    conditional: Boolean = false,
    unknownCause: UnknownCause? = null,
    isRunning: Boolean = false,
    description: String? = null,
) = ComponentRow(
    id = ComponentId(PREVIEW_PACKAGE, "$PREVIEW_PACKAGE.$name"),
    type = type,
    switch = switch,
    blockedBy = blockedBy,
    conditional = conditional,
    unknownCause = unknownCause,
    isRunning = isRunning,
    description = description,
)

/** Activities in each state: runnable, blocked by each mechanism and by both, conditional, unknown. */
internal val previewActivityRows = listOf(
    previewRow("MainActivity", ComponentType.ACTIVITY, SwitchState.ON, description = "The screen the launcher opens"),
    previewRow("AdActivity", ComponentType.ACTIVITY, SwitchState.OFF, blockedBy = setOf(BlockReason.PM_SETTING)),
    previewRow("TrackerActivity", ComponentType.ACTIVITY, SwitchState.OFF, blockedBy = setOf(BlockReason.IFW_RULE)),
    previewRow("SurveyActivity", ComponentType.ACTIVITY, SwitchState.OFF, blockedBy = setOf(BlockReason.PM_SETTING, BlockReason.IFW_RULE)),
    previewRow("DebugActivity", ComponentType.ACTIVITY, SwitchState.OFF, blockedBy = setOf(BlockReason.MANIFEST_DEFAULT)),
    previewRow("ShareActivity", ComponentType.ACTIVITY, SwitchState.ON, conditional = true),
    previewRow("SettingsActivity", ComponentType.ACTIVITY, SwitchState.UNKNOWN, unknownCause = UnknownCause.RULES_UNREADABLE),
)

internal val previewServiceRows = listOf(
    previewRow("SyncService", ComponentType.SERVICE, SwitchState.ON, isRunning = true),
    previewRow("PushService", ComponentType.SERVICE, SwitchState.OFF, blockedBy = setOf(BlockReason.IFW_RULE)),
)

internal val previewComponents = AppComponents(activities = previewActivityRows, services = previewServiceRows)

internal val previewMatchedRules = listOf(
    MatchedRule(id = 1, title = "Firebase", icon = null, rows = previewServiceRows),
)
