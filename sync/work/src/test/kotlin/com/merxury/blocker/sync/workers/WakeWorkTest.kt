// Copyright 2026 Blocker
// SPDX-License-Identifier: Apache-2.0

package com.merxury.blocker.sync.workers

import android.app.Application
import android.content.Context
import androidx.work.Configuration
import androidx.work.ListenableWorker
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import com.google.common.truth.Truth.assertThat
import com.merxury.blocker.core.utils.LibrootRootAvailabilityChecker
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], manifest = Config.NONE)
class WakeWorkTest {
    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun startsAfterUpgradeWithLegacySwitchOffAndSchedulesAgainOnRootGrant() = runTest {
        val context = RuntimeEnvironment.getApplication() as Application
        context.getSharedPreferences("wake_protection", Context.MODE_PRIVATE).edit().putBoolean("enabled", false).commit()
        val factory = object : WorkerFactory() {
            override fun createWorker(appContext: Context, workerClassName: String, workerParameters: WorkerParameters): ListenableWorker = object : Worker(appContext, workerParameters) {
                override fun doWork() = Result.retry()
            }
        }
        WorkManagerTestInitHelper.initializeTestWorkManager(
            context,
            Configuration.Builder().setExecutor(SynchronousExecutor()).setWorkerFactory(factory).build(),
        )
        val work = WorkManager.getInstance(context)
        val granted = MutableStateFlow(false)
        val root = mock<LibrootRootAvailabilityChecker>()
        whenever(root.rootGranted).thenReturn(granted)
        WakeWork(context, root, mock(), backgroundScope, StandardTestDispatcher(testScheduler)).initialize()
        runCurrent()
        fun pending(name: String) = work.getWorkInfosForUniqueWork(name).get().filter { !it.state.isFinished }.single().id
        val startup = pending("wake-history-update")
        val periodic = pending("wake-history-periodic")

        granted.value = true
        runCurrent()
        assertThat(pending("wake-history-update")).isNotEqualTo(startup)
        assertThat(pending("wake-history-periodic")).isEqualTo(periodic)
    }
}
