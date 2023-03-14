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

package com.merxury.blocker.feature.ruledetail

import com.merxury.blocker.core.analytics.AnalyticsEvent
import com.merxury.blocker.core.analytics.AnalyticsEvent.Param
import com.merxury.blocker.core.analytics.AnalyticsHelper

internal fun AnalyticsHelper.logControlAllInPageClicked(newState: Boolean) =
    logEvent(
        AnalyticsEvent(
            type = "rule_detail_control_all_in_page_clicked",
            extras = listOf(
                Param(key = "new_state", value = newState.toString()),
            ),
        ),
    )
internal fun AnalyticsHelper.logControlAllComponentsClicked(newState: Boolean) =
    logEvent(
        AnalyticsEvent(
            type = "rule_detail_control_all_components_clicked",
            extras = listOf(
                Param(key = "new_state", value = newState.toString()),
            ),
        ),
    )

internal fun AnalyticsHelper.logSwitchComponentStateClicked(newState: Boolean) =
    logEvent(
        AnalyticsEvent(
            type = "rule_detail_switch_component_state_clicked",
            extras = listOf(
                Param(key = "new_state", value = newState.toString()),
            ),
        ),
    )

internal fun AnalyticsHelper.logStopServiceClicked() =
    logEvent(
        AnalyticsEvent(
            type = "rule_detail_stop_service_clicked",
        ),
    )

internal fun AnalyticsHelper.logLaunchActivityClicked() =
    logEvent(
        AnalyticsEvent(
            type = "rule_detail_launch_activity_clicked",
        ),
    )
