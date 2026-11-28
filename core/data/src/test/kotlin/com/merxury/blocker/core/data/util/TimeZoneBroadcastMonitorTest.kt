// Copyright 2026 Blocker
// SPDX-License-Identifier: Apache-2.0

package com.merxury.blocker.core.data.util

import android.app.Application
import android.content.Intent
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.TimeZone
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30])
@OptIn(ExperimentalCoroutinesApi::class)
class TimeZoneBroadcastMonitorTest {
    private val context = ApplicationProvider.getApplicationContext<Application>()

    @Test
    fun whenCollectingTimeZone_thenImmediatelyEmitsTheCurrentDefault() = runTest {
        assertThat(monitor().currentTimeZone.first()).isEqualTo(TimeZone.currentSystemDefault())
        assertThat(testScheduler.currentTime).isEqualTo(0)
    }

    @Test
    fun givenACollector_whenTimeZoneChangeIsBroadcast_thenEmitsTheZoneFromTheIntent() = runTest {
        val default = TimeZone.currentSystemDefault()
        val updated = otherZone(default)
        monitor().currentTimeZone.test {
            assertThat(awaitItem()).isEqualTo(default)

            context.sendBroadcast(Intent(Intent.ACTION_TIMEZONE_CHANGED).putExtra(Intent.EXTRA_TIMEZONE, updated.id))
            shadowOf(Looper.getMainLooper()).idle()

            assertThat(awaitItem()).isEqualTo(updated)
        }
    }

    @Test
    fun givenACollector_whenReceiverGetsAnUnrelatedAction_thenDoesNotEmit() = runTest {
        monitor().currentTimeZone.test {
            val default = awaitItem()
            val receiver = registeredTimeZoneReceivers().single().broadcastReceiver

            receiver.onReceive(context, Intent(Intent.ACTION_LOCALE_CHANGED).putExtra(Intent.EXTRA_TIMEZONE, otherZone(default).id))
            runCurrent()

            expectNoEvents()
        }
    }

    @Test
    fun givenTheLastSubscriberCancels_whenSharingStops_thenUnregistersTheReceiver() = runTest {
        monitor().currentTimeZone.test {
            awaitItem()
            assertThat(registeredTimeZoneReceivers()).hasSize(1)

            cancelAndIgnoreRemainingEvents()
        }
        runCurrent()
        assertThat(registeredTimeZoneReceivers()).hasSize(1)

        advanceTimeBy(5_000)
        runCurrent()

        assertThat(registeredTimeZoneReceivers()).isEmpty()
    }

    private fun TestScope.monitor() = TimeZoneBroadcastMonitor(context, backgroundScope, StandardTestDispatcher(testScheduler))

    private fun registeredTimeZoneReceivers() = shadowOf(context).registeredReceivers.filter {
        it.intentFilter.hasAction(Intent.ACTION_TIMEZONE_CHANGED)
    }

    private fun otherZone(default: TimeZone): TimeZone = listOf(TimeZone.of("Asia/Tokyo"), TimeZone.of("Europe/Paris")).first { it != default }
}
