/*
 * Copyright 2023 The Android Open Source Project
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

package com.merxury.blocker.core.data.respository

import com.merxury.blocker.core.analytics.AnalyticsEvent
import com.merxury.blocker.core.analytics.AnalyticsEvent.Param
import com.merxury.blocker.core.analytics.AnalyticsHelper

fun AnalyticsHelper.logDarkThemeConfigChanged(darkThemeConfigName: String) =
    logEvent(
        AnalyticsEvent(
            type = "dark_theme_config_changed",
            extras = listOf(
                Param(key = "dark_theme_config", value = darkThemeConfigName),
            ),
        ),
    )

fun AnalyticsHelper.logDynamicColorPreferenceChanged(useDynamicColor: Boolean) =
    logEvent(
        AnalyticsEvent(
            type = "dynamic_color_preference_changed",
            extras = listOf(
                Param(key = "dynamic_color_preference", value = useDynamicColor.toString()),
            ),
        ),
    )

fun AnalyticsHelper.logUseBottomSheetStyleInDetailPreferenceChanged(useBottomSheetStyleInDetail: Boolean) =
    logEvent(
        AnalyticsEvent(
            type = "use_bottom_sheet_style_in_detail_preference_changed",
            extras = listOf(
                Param(
                    key = "use_bottom_sheet_style_in_detail_preference",
                    value = useBottomSheetStyleInDetail.toString(),
                ),
            ),
        ),
    )

fun AnalyticsHelper.logControllerTypeChanged(controllerName: String) =
    logEvent(
        AnalyticsEvent(
            type = "controller_type_changed",
            extras = listOf(
                Param(key = "controller_type", value = controllerName),
            ),
        ),
    )

fun AnalyticsHelper.logBackupSystemAppPreferenceChanged(backupSystemApp: Boolean) =
    logEvent(
        AnalyticsEvent(
            type = "backup_system_app_preference_changed",
            extras = listOf(
                Param(key = "backup_system_app_preference", value = backupSystemApp.toString()),
            ),
        ),
    )

fun AnalyticsHelper.logRestoreSystemAppPreferenceChanged(restoreSystemApp: Boolean) =
    logEvent(
        AnalyticsEvent(
            type = "restore_system_app_preference_changed",
            extras = listOf(
                Param(key = "restore_system_app_preference", value = restoreSystemApp.toString()),
            ),
        ),
    )

fun AnalyticsHelper.logRuleServerProviderChanged(ruleServerProviderName: String) =
    logEvent(
        AnalyticsEvent(
            type = "rule_server_provider_changed",
            extras = listOf(
                Param(key = "rule_server_provider", value = ruleServerProviderName),
            ),
        ),
    )

fun AnalyticsHelper.logAppSortingChanged(appSortingName: String) =
    logEvent(
        AnalyticsEvent(
            type = "app_sorting_changed",
            extras = listOf(
                Param(key = "app_sorting", value = appSortingName),
            ),
        ),
    )

fun AnalyticsHelper.logShowServiceInfoPreferenceChanged(showServiceInfo: Boolean) =
    logEvent(
        AnalyticsEvent(
            type = "show_service_info_preference_changed",
            extras = listOf(
                Param(key = "show_service_info_preference", value = showServiceInfo.toString()),
            ),
        ),
    )

fun AnalyticsHelper.logShowSystemAppPreferenceChanged(showSystemApp: Boolean) =
    logEvent(
        AnalyticsEvent(
            type = "show_system_app_preference_changed",
            extras = listOf(
                Param(key = "show_system_app_preference", value = showSystemApp.toString()),
            ),
        ),
    )

fun AnalyticsHelper.logComponentShowPriorityPreferenceChanged(componentShowPriority: String) =
    logEvent(
        AnalyticsEvent(
            type = "component_show_priority_preference_changed",
            extras = listOf(
                Param(key = "component_show_priority_preference", value = componentShowPriority),
            ),
        ),
    )

fun AnalyticsHelper.logShowRunningAppsOnTopPreferenceChanged(showRunningAppsOnTop: Boolean) =
    logEvent(
        AnalyticsEvent(
            type = "show_running_apps_on_top_preference_changed",
            extras = listOf(
                Param(
                    key = "show_running_apps_on_top_preference",
                    value = showRunningAppsOnTop.toString(),
                ),
            ),
        ),
    )
