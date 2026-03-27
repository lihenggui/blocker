/*
 * Copyright 2025 Blocker
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *       https://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 */

package com.merxury.blocker.provider

import com.merxury.blocker.core.analytics.AnalyticsEvent
import com.merxury.blocker.core.analytics.AnalyticsEvent.Param
import com.merxury.blocker.core.testing.util.TestAnalyticsHelper
import org.junit.Test
import kotlin.test.assertTrue

class AnalyticsExtensionTest {

    private val analyticsHelper = TestAnalyticsHelper()

    @Test
    fun givenNewStateTrue_whenLogControlComponentViaProvider_thenEventLoggedWithTrueState() {
        analyticsHelper.logControlComponentViaProvider(newState = true)
        assertTrue(
            analyticsHelper.hasLogged(
                AnalyticsEvent(
                    type = "control_component_via_provider_activated",
                    extras = listOf(Param(key = "new_state", value = "true")),
                ),
            ),
        )
    }

    @Test
    fun givenNewStateFalse_whenLogControlComponentViaProvider_thenEventLoggedWithFalseState() {
        analyticsHelper.logControlComponentViaProvider(newState = false)
        assertTrue(
            analyticsHelper.hasLogged(
                AnalyticsEvent(
                    type = "control_component_via_provider_activated",
                    extras = listOf(Param(key = "new_state", value = "false")),
                ),
            ),
        )
    }
}
