// Copyright 2026 Blocker
// SPDX-License-Identifier: Apache-2.0

package com.merxury.blocker.sync.workers

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Looper
import androidx.concurrent.futures.CallbackToFutureAdapter
import androidx.work.Configuration
import androidx.work.ListenableWorker
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import com.google.common.truth.Truth.assertThat
import com.google.common.util.concurrent.ListenableFuture
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

/** A collection that runs until the test ends it with a result. */
private class HeldRun {
    lateinit var completer: CallbackToFutureAdapter.Completer<ListenableWorker.Result>
    var cancelled = false

    fun finish(result: ListenableWorker.Result) {
        completer.set(result)
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], manifest = Config.NONE)
class WakeWorkTest {
    private val context get() = RuntimeEnvironment.getApplication() as Application
    private val channel = FakePrivilegedChannel(ChannelState.Unavailable(UnavailableReason.NOT_CHECKED))
    private val runs = mutableListOf<HeldRun>()

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun TestScope.initializeWork(): WorkManager {
        val factory = object : WorkerFactory() {
            override fun createWorker(appContext: Context, workerClassName: String, workerParameters: WorkerParameters): ListenableWorker = object : ListenableWorker(appContext, workerParameters) {
                override fun startWork(): ListenableFuture<Result> {
                    val run = HeldRun()
                    runs += run
                    return CallbackToFutureAdapter.getFuture { completer ->
                        run.completer = completer
                        completer.addCancellationListener({ run.cancelled = true }, Runnable::run)
                        "held collection"
                    }
                }
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

    private fun WorkManager.pending(name: String): WorkInfo = getWorkInfosForUniqueWork(name).get().single { !it.state.isFinished }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun givenDisabledLegacySwitch_whenUpgradingAndChannelBecomesReadyWhileIdle_thenWorkIsScheduled() = runTest {
        context.getSharedPreferences("wake_protection", Context.MODE_PRIVATE).edit().putBoolean("enabled", false).commit()
        val work = initializeWork()
        val startup = work.pending("wake-history-update").id
        val periodic = work.pending("wake-history-periodic").id
        runs.single().finish(ListenableWorker.Result.success())

        channel.setState(ChannelState.Ready(PrivilegedIdentity.ROOT))
        runCurrent()

        assertThat(work.pending("wake-history-update").id).isNotEqualTo(startup)
        assertThat(work.pending("wake-history-periodic").id).isEqualTo(periodic)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun givenReadinessFlipsWhileTheWorkRuns_whenTheRunConnectsTheChannel_thenTheRunIsKept() = runTest {
        val work = initializeWork()
        val running = work.pending("wake-history-update")
        assertThat(running.state).isEqualTo(WorkInfo.State.RUNNING)

        // The run's own quiet connect, then Shizuku returning as another uid.
        channel.setState(ChannelState.Ready(PrivilegedIdentity.SHELL))
        runCurrent()
        channel.setState(ChannelState.Ready(PrivilegedIdentity.ROOT))
        runCurrent()

        assertThat(work.pending("wake-history-update").id).isEqualTo(running.id)
        assertThat(runs.single().cancelled).isFalse()
        assertThat(channel.connectCount).isEqualTo(0)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun givenRunThatLosesTheChannel_whenItIsReadyAgainDuringTheBackoff_thenTheBackoffKeepsBuilding() = runTest {
        val work = initializeWork()
        channel.setState(ChannelState.Ready(PrivilegedIdentity.ROOT))
        runCurrent()
        val first = work.pending("wake-history-update")

        // A read marks the channel lost and the run asks to be retried.
        channel.setState(ChannelState.Unavailable(UnavailableReason.FAILED))
        runCurrent()
        runs.single().finish(ListenableWorker.Result.retry())
        val backingOff = work.pending("wake-history-update")
        assertThat(backingOff.state).isEqualTo(WorkInfo.State.ENQUEUED)
        assertThat(backingOff.runAttemptCount).isEqualTo(1)

        // The quiet connect makes it ready again.
        channel.setState(ChannelState.Ready(PrivilegedIdentity.ROOT))
        runCurrent()

        val after = work.pending("wake-history-update")
        assertThat(after.id).isEqualTo(first.id)
        assertThat(after.runAttemptCount).isEqualTo(1)
        assertThat(runs).hasSize(1)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun givenChannelStaysUnavailable_whenItsReasonChanges_thenNothingIsRescheduledAndNothingConnects() = runTest {
        val work = initializeWork()
        val startup = work.pending("wake-history-update").id
        runs.single().finish(ListenableWorker.Result.success())

        for (reason in UnavailableReason.entries) {
            channel.setState(ChannelState.Unavailable(reason))
            runCurrent()
        }

        assertThat(work.getWorkInfosForUniqueWork("wake-history-update").get().map { it.id }).containsExactly(startup)
        assertThat(channel.connectCount).isEqualTo(0)
    }

    @Test fun givenRunningCollector_whenExplicitlyRequestingWakeRun_thenSeparateWorkIsScheduled() = runTest {
        val work = initializeWork()
        val regular = work.pending("wake-history-update").id
        val requester = WakeWork(context, WakeAuthorizationRepository(channel), backgroundScope)

        requester.requestRun()
        val first = work.pending("wake-history-requested").id
        requester.requestRun()

        assertThat(work.pending("wake-history-requested").id).isNotEqualTo(first)
        assertThat(work.pending("wake-history-update").id).isEqualTo(regular)
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
