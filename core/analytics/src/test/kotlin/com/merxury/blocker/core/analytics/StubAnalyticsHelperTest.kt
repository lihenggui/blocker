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

package com.merxury.blocker.core.analytics

import android.util.Log
import com.google.common.truth.Truth.assertThat
import org.junit.After
import org.junit.Test
import timber.log.Timber

class StubAnalyticsHelperTest {

    @After
    fun tearDown() {
        Timber.uprootAll()
    }

    @Test
    fun givenStubAnalyticsHelper_whenLoggingEvent_thenWritesDebugLog() {
        val entries = mutableListOf<Pair<Int, String>>()
        Timber.plant(
            object : Timber.Tree() {
                override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
                    entries += priority to message
                }
            },
        )
        val helper = StubAnalyticsHelper()
        val event = AnalyticsEvent(
            type = AnalyticsEvent.Types.SCREEN_VIEW,
            extras = listOf(AnalyticsEvent.Param(AnalyticsEvent.ParamKeys.SCREEN_NAME, "AppList")),
        )

        helper.logEvent(event)

        assertThat(entries).hasSize(1)
        assertThat(entries.single().first).isEqualTo(Log.DEBUG)
        assertThat(entries.single().second).contains("AppList")
    }

    @Test
    fun givenStubAnalyticsHelper_whenTogglingStatistics_thenWritesInfoLog() {
        val entries = mutableListOf<Pair<Int, String>>()
        Timber.plant(
            object : Timber.Tree() {
                override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
                    entries += priority to message
                }
            },
        )
        val helper = StubAnalyticsHelper()

        helper.setEnableStatistics(true)

        assertThat(entries).hasSize(1)
        assertThat(entries.single().first).isEqualTo(Log.INFO)
        assertThat(entries.single().second).contains("true")
    }

    @Test
    fun givenNoOpAnalyticsHelper_whenLoggingOrToggling_thenProducesNoLogs() {
        val entries = mutableListOf<String>()
        Timber.plant(
            object : Timber.Tree() {
                override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
                    entries += message
                }
            },
        )
        val helper = NoOpAnalyticsHelper()

        helper.setEnableStatistics(true)
        helper.logEvent(AnalyticsEvent(type = AnalyticsEvent.Types.SCREEN_VIEW))

        assertThat(entries).isEmpty()
    }
}
