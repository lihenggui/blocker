// Copyright 2026 Blocker
// SPDX-License-Identifier: Apache-2.0

package com.merxury.blocker.sync.workers

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Looper
import androidx.work.Configuration
import androidx.work.ListenableWorker
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import com.google.common.truth.Truth.assertThat
import com.merxury.blocker.core.data.wake.WakeAuthorizationRepository
import com.merxury.blocker.core.model.privileged.ChannelState
import com.merxury.blocker.core.model.privileged.PrivilegedIdentity
import com.merxury.blocker.core.model.privileged.UnavailableReason
import com.merxury.blocker.core.testing.controller.FakePrivilegedChannel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], manifest = Config.NONE)
class WakeWorkTest {
    private val context get() = RuntimeEnvironment.getApplication() as Application
    private val channel = FakePrivilegedChannel(ChannelState.Unavailable(UnavailableReason.NOT_CHECKED))

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun TestScope.initializeWork(): WorkManager {
        val factory = object : WorkerFactory() {
            override fun createWorker(appContext: Context, workerClassName: String, workerParameters: WorkerParameters): ListenableWorker = object : Worker(appContext, workerParameters) {
                override fun doWork() = Result.retry()
            }
        }
        WorkManagerTestInitHelper.initializeTestWorkManager(
            context,
            Configuration.Builder().setExecutor(SynchronousExecutor()).setWorkerFactory(factory).build(),
        )
        WakeWork(context, WakeAuthorizationRepository(channel), backgroundScope).initialize()
        runCurrent()
        return WorkManager.getInstance(context)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun givenDisabledLegacySwitch_whenUpgradingAndChannelBecomesReady_thenImmediateWorkIsRescheduled() = runTest {
        context.getSharedPreferences("wake_protection", Context.MODE_PRIVATE).edit().putBoolean("enabled", false).commit()
        val work = initializeWork()
        fun pending(name: String) = work.getWorkInfosForUniqueWork(name).get().filter { !it.state.isFinished }.single().id
        val startup = pending("wake-history-update")
        val periodic = pending("wake-history-periodic")

        channel.setState(ChannelState.Ready(PrivilegedIdentity.ROOT))
        runCurrent()
        assertThat(pending("wake-history-update")).isNotEqualTo(startup)
        assertThat(pending("wake-history-periodic")).isEqualTo(periodic)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun givenChannelReadyAsTheShell_whenGrantedAtFirstLaunch_thenImmediateWorkIsRescheduledToRead() = runTest {
        val work = initializeWork()
        fun pending(name: String) = work.getWorkInfosForUniqueWork(name).get().filter { !it.state.isFinished }.single().id
        val startup = pending("wake-history-update")

        channel.setState(ChannelState.Ready(PrivilegedIdentity.SHELL))
        runCurrent()
        val reading = pending("wake-history-update")
        assertThat(reading).isNotEqualTo(startup)

        // Shizuku restarted as root: the same work may now write too.
        channel.setState(ChannelState.Ready(PrivilegedIdentity.ROOT))
        runCurrent()
        assertThat(pending("wake-history-update")).isNotEqualTo(reading)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun givenChannelStaysUnavailable_whenItsReasonChanges_thenNothingIsRescheduledAndNothingConnects() = runTest {
        val work = initializeWork()
        fun pending(name: String) = work.getWorkInfosForUniqueWork(name).get().filter { !it.state.isFinished }.single().id
        val startup = pending("wake-history-update")

        for (reason in UnavailableReason.entries) {
            channel.setState(ChannelState.Unavailable(reason))
            runCurrent()
        }

        assertThat(pending("wake-history-update")).isEqualTo(startup)
        assertThat(channel.connectCount).isEqualTo(0)
    }

    @Test fun givenRetryingCollector_whenEitherPeerUpdates_thenASeparateRefreshIsScheduled() = runTest {
        val work = initializeWork()

        val updates = mutableSetOf<java.util.UUID>()
        // Android sends PACKAGE_ADDED with EXTRA_REPLACING for an update to either peer.
        for (pkg in listOf("test.caller", "test.target")) {
            context.sendBroadcast(Intent(Intent.ACTION_PACKAGE_ADDED, Uri.parse("package:$pkg")).putExtra(Intent.EXTRA_REPLACING, true))
            shadowOf(Looper.getMainLooper()).idle()
            updates += work.getWorkInfosForUniqueWork("wake-history-package-change").get().single { !it.state.isFinished }.id
        }

        assertThat(updates).hasSize(2)
        assertThat(work.getWorkInfosForUniqueWork("wake-history-update").get()).hasSize(1)
    }
}
