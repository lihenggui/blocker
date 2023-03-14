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

package com.merxury.blocker.feature.applist

import com.merxury.blocker.core.analytics.AnalyticsEvent
import com.merxury.blocker.core.analytics.AnalyticsHelper

fun AnalyticsHelper.logUninstallAppClicked() = logEvent(
    AnalyticsEvent(
        type = "app_list_uninstall_app_clicked",
    ),
)

fun AnalyticsHelper.logDisableAppClicked() = logEvent(
    AnalyticsEvent(
        type = "app_list_disable_app_clicked",
    ),
)

fun AnalyticsHelper.logEnableAppClicked() = logEvent(
    AnalyticsEvent(
        type = "app_list_enable_app_clicked",
    ),
)

fun AnalyticsHelper.logClearCacheClicked() = logEvent(
    AnalyticsEvent(
        type = "app_list_clear_cache_clicked",
    ),
)

fun AnalyticsHelper.logClearDataClicked() = logEvent(
    AnalyticsEvent(
        type = "app_list_clear_data_clicked",
    ),
)

fun AnalyticsHelper.logForceStopClicked() = logEvent(
    AnalyticsEvent(
        type = "app_list_force_stop_clicked",
    ),
)
