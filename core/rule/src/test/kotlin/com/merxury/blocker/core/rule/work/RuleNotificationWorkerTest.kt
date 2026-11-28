// Copyright 2026 Blocker
// SPDX-License-Identifier: Apache-2.0

package com.merxury.blocker.core.rule.work

import android.app.Notification
import android.content.Context
import android.content.pm.ServiceInfo
import androidx.test.core.app.ApplicationProvider
import androidx.work.ForegroundInfo
import androidx.work.ListenableWorker.Result
import androidx.work.WorkManager
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.TestListenableWorkerBuilder
import androidx.work.testing.WorkManagerTestInitHelper
import com.google.common.truth.Truth.assertThat
import com.google.common.util.concurrent.Futures
import com.merxury.blocker.core.rule.R
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class RuleNotificationWorkerTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val foregroundUpdates = mutableListOf<ForegroundInfo>()

    @Before
    fun setUp() {
        WorkManagerTestInitHelper.initializeTestWorkManager(context)
    }

    @Test
    fun whenGettingInitialForegroundInfo_thenShowsAnOngoingCancellableNotificationWithoutSummary() = runTest {
        val worker = worker()

        val info = worker.getForegroundInfo()

        val notification = info.notification
        assertThat(notification.extras.getCharSequence(Notification.EXTRA_TITLE)?.toString())
            .isEqualTo(context.getString(worker.getNotificationTitle()))
        assertThat(notification.extras.getCharSequence(Notification.EXTRA_TEXT)).isNull()
        assertThat(notification.extras.getInt(Notification.EXTRA_PROGRESS)).isEqualTo(0)
        assertThat(notification.extras.getInt(Notification.EXTRA_PROGRESS_MAX)).isEqualTo(0)
        assertThat(notification.flags and Notification.FLAG_ONGOING_EVENT).isNotEqualTo(0)
        val action = notification.actions.single()
        assertThat(action.title.toString()).isEqualTo(context.getString(R.string.core_rule_cancel))
        assertThat(action.actionIntent).isEqualTo(WorkManager.getInstance(context).createCancelPendingIntent(worker.id))
        assertThat(info.foregroundServiceType).isEqualTo(ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
    }

    @Test
    @Config(sdk = [29])
    fun givenAProgressUpdate_whenBuildingTheNotification_thenShowsSummaryAndProgressForDataSync() {
        val info = worker().updateNotification("com.example.app", current = 3, total = 8)

        val extras = info.notification.extras
        assertThat(extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()).isEqualTo("com.example.app")
        assertThat(extras.getInt(Notification.EXTRA_PROGRESS)).isEqualTo(3)
        assertThat(extras.getInt(Notification.EXTRA_PROGRESS_MAX)).isEqualTo(8)
        assertThat(extras.getBoolean(Notification.EXTRA_PROGRESS_INDETERMINATE)).isFalse()
        assertThat(info.foregroundServiceType).isEqualTo(ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
    }

    @Test
    fun givenABlankSummary_whenBuildingTheNotification_thenOmitsTheSummary() {
        val info = worker().updateNotification("  ", current = 1, total = 2)

        assertThat(info.notification.extras.getCharSequence(Notification.EXTRA_TEXT)).isNull()
    }

    @Test
    fun givenForegroundStartThrowsIllegalStateException_whenWorkRuns_thenItStillSucceeds() = runTest {
        val worker = worker(foregroundFailure = IllegalStateException("Foreground start denied"))

        assertThat(worker.doWork()).isEqualTo(Result.success())
        assertThat(foregroundUpdates).hasSize(1)
    }

    @Test
    fun givenForegroundStartIsCancelled_whenWorkRuns_thenCancellationPropagates() = runTest {
        val worker = worker(foregroundFailure = CancellationException("Foreground start cancelled"))

        val failure = runCatching { worker.doWork() }.exceptionOrNull()

        assertThat(failure).isInstanceOf(CancellationException::class.java)
        assertThat(foregroundUpdates).hasSize(1)
    }

    private fun worker(foregroundFailure: IllegalStateException? = null): NotificationWorker = TestListenableWorkerBuilder<NotificationWorker>(context)
        .setWorkerFactory(
            object : WorkerFactory() {
                override fun createWorker(appContext: Context, workerClassName: String, workerParameters: WorkerParameters) = NotificationWorker(appContext, workerParameters)
            },
        )
        .setForegroundUpdater { _, _, info ->
            foregroundUpdates += info
            when (foregroundFailure) {
                null -> Futures.immediateVoidFuture()
                is CancellationException -> Futures.immediateCancelledFuture()
                else -> Futures.immediateFailedFuture(foregroundFailure)
            }
        }
        .build()

    private class NotificationWorker(context: Context, params: WorkerParameters) : RuleNotificationWorker(context, params) {
        override fun getNotificationTitle(): Int = R.string.core_rule_reset_ifw_please_wait

        override suspend fun doWork(): Result {
            setForegroundSafely(getForegroundInfo())
            return Result.success()
        }
    }
}
