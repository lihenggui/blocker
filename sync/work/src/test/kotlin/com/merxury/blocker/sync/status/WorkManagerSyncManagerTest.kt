// Copyright 2026 Blocker
// SPDX-License-Identifier: Apache-2.0

package com.merxury.blocker.sync.status

import android.content.Context
import android.os.Looper
import androidx.concurrent.futures.CallbackToFutureAdapter
import androidx.test.core.app.ApplicationProvider
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
import com.merxury.blocker.sync.initializers.SYNC_WORK_NAME
import com.merxury.blocker.sync.workers.SyncWorker
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.produceIn
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], manifest = Config.NONE)
class WorkManagerSyncManagerTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val workManager get() = WorkManager.getInstance(context)
    private val workFactory = HeldSyncWorkerFactory()
    private lateinit var syncManager: WorkManagerSyncManager

    @Before
    fun setUp() {
        val configuration = Configuration.Builder()
            .setExecutor(SynchronousExecutor())
            .setWorkerFactory(workFactory)
            .build()
        WorkManagerTestInitHelper.initializeTestWorkManager(context, configuration)
        syncManager = WorkManagerSyncManager(context)
    }

    @After
    fun tearDown() {
        workManager.cancelAllWork().result.get()
        shadowOf(Looper.getMainLooper()).idle()
    }

    @Test
    fun givenSyncIsQueued_whenRequestingSyncAgain_thenKeepsTheOriginalNonForcedWork() {
        syncManager.requestSync()
        val first = uniqueSync()
        assertThat(first.state).isEqualTo(WorkInfo.State.ENQUEUED)

        syncManager.requestSync()

        assertThat(uniqueSync().id).isEqualTo(first.id)
        start(first.id)
        assertThat(workFactory.parameters.single().inputData).isEqualTo(SyncWorker.startUpSyncWork(forceCheck = false).workSpec.input)
    }

    @Test
    fun givenSyncIsQueued_whenRequestingSyncImmediately_thenReplacesItWithForcedWork() {
        syncManager.requestSync()
        val first = uniqueSync()

        syncManager.requestSyncImmediately()

        val replacement = uniqueSync()
        assertThat(replacement.id).isNotEqualTo(first.id)
        assertThat(replacement.state).isEqualTo(WorkInfo.State.ENQUEUED)
        start(replacement.id)
        assertThat(workFactory.parameters.single().inputData).isEqualTo(SyncWorker.startUpSyncWork(forceCheck = true).workSpec.input)
    }

    @Test
    fun givenQueuedSync_whenItStartsAndFinishes_thenOnlyRunningWorkIsReportedAsSyncing() = runTest {
        syncManager.requestSync()
        val queued = uniqueSync()
        assertThat(queued.state).isEqualTo(WorkInfo.State.ENQUEUED)
        val syncing = syncManager.isSyncing.distinctUntilChanged().produceIn(backgroundScope)
        assertThat(syncing.receive()).isFalse()

        start(queued.id)
        assertThat(uniqueSync().state).isEqualTo(WorkInfo.State.RUNNING)
        assertThat(syncing.receive()).isTrue()

        workFactory.complete()
        assertThat(uniqueSync().state).isEqualTo(WorkInfo.State.SUCCEEDED)
        assertThat(syncing.receive()).isFalse()
    }

    private fun uniqueSync(): WorkInfo = workManager.getWorkInfosForUniqueWork(SYNC_WORK_NAME).get().single()

    private fun start(id: UUID) {
        checkNotNull(WorkManagerTestInitHelper.getTestDriver(context)).setAllConstraintsMet(id)
        shadowOf(Looper.getMainLooper()).idle()
    }
}

private class HeldSyncWorkerFactory : WorkerFactory() {
    val parameters = mutableListOf<WorkerParameters>()
    private lateinit var result: CallbackToFutureAdapter.Completer<ListenableWorker.Result>

    override fun createWorker(appContext: Context, workerClassName: String, workerParameters: WorkerParameters): ListenableWorker = object : ListenableWorker(appContext, workerParameters) {
        override fun startWork(): ListenableFuture<Result> {
            parameters += workerParameters
            return CallbackToFutureAdapter.getFuture { completer ->
                result = completer
                "held sync"
            }
        }
    }

    fun complete() {
        result.set(ListenableWorker.Result.success())
    }
}
