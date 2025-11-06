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

import com.merxury.blocker.core.analytics.AnalyticsEvent
import com.merxury.blocker.core.analytics.AnalyticsEvent.Param
import com.merxury.blocker.core.analytics.AnalyticsHelper

internal fun AnalyticsHelper.logSearchQueryUpdated() = logEvent(
    AnalyticsEvent(
        type = "debloater_search_query_updated",
    ),
)

internal fun AnalyticsHelper.logComponentControlled(
    enabled: Boolean,
    isShareable: Boolean,
    isExplicitLaunch: Boolean,
    isLauncherEntry: Boolean,
    isDeeplinkEntry: Boolean,
) = logEvent(
    AnalyticsEvent(
        type = "debloater_component_controlled",
        extras = listOf(
            Param(key = "enabled", value = enabled.toString()),
            Param(key = "is_shareable", value = isShareable.toString()),
            Param(key = "is_explicit_launch", value = isExplicitLaunch.toString()),
            Param(key = "is_launcher_entry", value = isLauncherEntry.toString()),
            Param(key = "is_deeplink_entry", value = isDeeplinkEntry.toString()),
        ),
    ),
)

internal fun AnalyticsHelper.logBatchComponentsControlled(
    enabled: Boolean,
    componentCount: Int,
    shareableCount: Int,
    launcherEntryCount: Int,
    deeplinkEntryCount: Int,
) = logEvent(
    AnalyticsEvent(
        type = "debloater_batch_components_controlled",
        extras = listOf(
            Param(key = "enabled", value = enabled.toString()),
            Param(key = "component_count", value = componentCount.toString()),
            Param(key = "shareable_count", value = shareableCount.toString()),
            Param(key = "launcher_entry_count", value = launcherEntryCount.toString()),
            Param(key = "deeplink_entry_count", value = deeplinkEntryCount.toString()),
        ),
    ),
)

internal fun AnalyticsHelper.logTestShareTriggered() = logEvent(
    AnalyticsEvent(
        type = "debloater_test_share_triggered",
    ),
)
