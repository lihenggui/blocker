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

package com.merxury.blocker.feature.appdetail

import com.merxury.blocker.core.analytics.AnalyticsEvent
import com.merxury.blocker.core.analytics.AnalyticsEvent.Param
import com.merxury.blocker.core.analytics.AnalyticsHelper

internal fun AnalyticsHelper.logBatchOperationPerformed(newState: Boolean) =
    logEvent(
        AnalyticsEvent(
            type = "app_detail_batch_operation_performed",
            extras = listOf(
                Param(key = "new_state", value = newState.toString()),
            ),
        ),
    )

internal fun AnalyticsHelper.logExportBlockerRuleClicked() =
    logEvent(
        AnalyticsEvent(
            type = "app_detail_export_blocker_rule_clicked",
        ),
    )

internal fun AnalyticsHelper.logImportBlockerRuleClicked() =
    logEvent(
        AnalyticsEvent(
            type = "app_detail_import_blocker_rule_clicked",
        ),
    )

internal fun AnalyticsHelper.logExportIfwRuleClicked() =
    logEvent(
        AnalyticsEvent(
            type = "app_detail_export_ifw_rule_clicked",
        ),
    )

internal fun AnalyticsHelper.logImportIfwRuleClicked() =
    logEvent(
        AnalyticsEvent(
            type = "app_detail_import_ifw_rule_clicked",
        ),
    )

internal fun AnalyticsHelper.logResetIfwRuleClicked() =
    logEvent(
        AnalyticsEvent(
            type = "app_detail_reset_ifw_rule_clicked",
        ),
    )

internal fun AnalyticsHelper.logSearchButtonClicked() =
    logEvent(
        AnalyticsEvent(
            type = "app_detail_search_button_clicked",
        ),
    )

internal fun AnalyticsHelper.logStartActivityClicked() =
    logEvent(
        AnalyticsEvent(
            type = "app_detail_start_activity_clicked",
        ),
    )

internal fun AnalyticsHelper.logStopServiceClicked() =
    logEvent(
        AnalyticsEvent(
            type = "app_detail_stop_service_clicked",
        ),
    )

internal fun AnalyticsHelper.logSwitchComponentClicked(newState: Boolean) =
    logEvent(
        AnalyticsEvent(
            type = "app_detail_switch_component_clicked",
            extras = listOf(
                Param(key = "new_state", value = newState.toString()),
            ),
        ),
    )
