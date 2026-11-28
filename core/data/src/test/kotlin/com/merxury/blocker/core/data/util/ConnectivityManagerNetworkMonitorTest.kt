// Copyright 2026 Blocker
// SPDX-License-Identifier: Apache-2.0

package com.merxury.blocker.core.data.util

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET
import androidx.core.content.getSystemService
import androidx.test.core.app.ApplicationProvider
import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowNetwork
import org.robolectric.shadows.ShadowNetworkCapabilities

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@OptIn(ExperimentalCoroutinesApi::class)
class ConnectivityManagerNetworkMonitorTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val connectivity = checkNotNull(context.getSystemService<ConnectivityManager>())
    private val shadowConnectivity = shadowOf(connectivity)

    @Test
    fun givenNoActiveNetwork_whenCollecting_thenInitiallyEmitsOffline() = runTest {
        shadowConnectivity.setActiveNetworkInfo(null)

        assertThat(monitor().isOnline.first()).isFalse()
    }

    @Test
    fun givenAnActiveNetworkWithInternet_whenCollecting_thenInitiallyEmitsOnline() = runTest {
        val activeNetwork = checkNotNull(connectivity.activeNetwork)
        val capabilities = ShadowNetworkCapabilities.newInstance()
        shadowOf(capabilities).addCapability(NET_CAPABILITY_INTERNET)
        shadowConnectivity.setNetworkCapabilities(activeNetwork, capabilities)

        assertThat(monitor().isOnline.first()).isTrue()
    }

    @Test
    fun givenTwoAvailableNetworks_whenEachIsLost_thenStaysOnlineUntilTheLastIsLost() = runTest {
        shadowConnectivity.setActiveNetworkInfo(null)
        val first = ShadowNetwork.newInstance(101)
        val second = ShadowNetwork.newInstance(102)

        monitor().isOnline.test {
            assertThat(awaitItem()).isFalse()
            val callback = shadowConnectivity.networkCallbacks.single()

            callback.onAvailable(first)
            assertThat(awaitItem()).isTrue()

            callback.onAvailable(second)
            assertThat(awaitItem()).isTrue()

            callback.onLost(first)
            assertThat(awaitItem()).isTrue()

            callback.onLost(second)
            assertThat(awaitItem()).isFalse()
        }
    }

    @Test
    fun givenAnActiveCollector_whenCancellingIt_thenUnregistersItsNetworkCallback() = runTest {
        monitor().isOnline.test {
            awaitItem()
            assertThat(shadowConnectivity.networkCallbacks).hasSize(1)

            cancelAndIgnoreRemainingEvents()
        }
        runCurrent()

        assertThat(shadowConnectivity.networkCallbacks).isEmpty()
    }

    private fun TestScope.monitor() = ConnectivityManagerNetworkMonitor(context, StandardTestDispatcher(testScheduler))
}
