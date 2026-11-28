// Copyright 2026 Blocker
// SPDX-License-Identifier: Apache-2.0

package com.merxury.blocker.core.analytics

import android.os.Bundle
import com.google.common.truth.Truth.assertThat
import com.google.firebase.analytics.FirebaseAnalytics
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.inOrder
import org.mockito.kotlin.mock
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.verifyNoMoreInteractions
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class FirebaseAnalyticsHelperTest {
    private val firebase = mock<FirebaseAnalytics>()
    private val helper = FirebaseAnalyticsHelper(firebase)

    @Test
    fun givenTheDefaultStatisticsSetting_whenLoggingAnEvent_thenDoesNotCallFirebase() {
        helper.logEvent(AnalyticsEvent("opened_settings"))

        verifyNoInteractions(firebase)
    }

    @Test
    fun givenStatisticsAreEnabled_whenLoggingAnEvent_thenForwardsTheEventWithTruncatedParameters() {
        val key = "k".repeat(40)
        val value = "v".repeat(100)
        val event = AnalyticsEvent(
            "opened_settings",
            extras = listOf(AnalyticsEvent.Param(key + "_suffix", value + "_suffix"), AnalyticsEvent.Param("source", "settings")),
        )
        val params = argumentCaptor<Bundle>()

        helper.setEnableStatistics(true)
        helper.logEvent(event)

        inOrder(firebase) {
            verify(firebase).setAnalyticsCollectionEnabled(true)
            verify(firebase).logEvent(eq(event.type), params.capture())
        }
        assertThat(params.singleValue.keySet()).containsExactly(key, "source")
        assertThat(params.singleValue.getString(key)).isEqualTo(value)
        assertThat(params.singleValue.getString("source")).isEqualTo("settings")
    }

    @Test
    fun givenStatisticsWereEnabled_whenDisablingThem_thenStopsForwardingEvents() {
        helper.setEnableStatistics(true)
        helper.setEnableStatistics(false)
        helper.logEvent(AnalyticsEvent("opened_settings"))

        inOrder(firebase) {
            verify(firebase).setAnalyticsCollectionEnabled(true)
            verify(firebase).setAnalyticsCollectionEnabled(false)
        }
        verifyNoMoreInteractions(firebase)
    }
}
