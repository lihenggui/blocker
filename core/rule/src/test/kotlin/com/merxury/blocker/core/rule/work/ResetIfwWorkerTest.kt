// Copyright 2026 Blocker
// SPDX-License-Identifier: Apache-2.0

package com.merxury.blocker.core.rule.work

import android.app.ForegroundServiceStartNotAllowedException
import android.app.Notification
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.ForegroundInfo
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.TestListenableWorkerBuilder
import androidx.work.testing.WorkManagerTestInitHelper
import androidx.work.workDataOf
import com.google.common.truth.Truth.assertThat
import com.google.common.util.concurrent.Futures
import com.merxury.blocker.core.data.wake.LocalFirewallResetRepository
import com.merxury.blocker.core.rule.R
import com.merxury.blocker.core.rule.entity.RuleWorkResult
import com.merxury.blocker.core.testing.controller.FakeIfwFileSystem
import com.merxury.core.ifw.IntentFirewallStore
import com.merxury.core.ifw.ManagedWakeReset
import com.merxury.core.ifw.xml.IfwXmlDeserializer
import com.merxury.core.ifw.xml.IfwXmlSerializer
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.IOException

private const val PACKAGE_NAME = "com.example"
private const val OTHER_PACKAGE_NAME = "com.other"

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ResetIfwWorkerTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val files = FakeIfwFileSystem().apply {
        putFile(PACKAGE_NAME, "<rules/>")
        putFile(OTHER_PACKAGE_NAME, "<rules/>")
    }
    private val wakeResets = mutableListOf<String?>()
    private val foregroundUpdates = mutableListOf<ForegroundInfo>()

    @Test
    fun givenPackageRules_whenResetWorkerRuns_thenItClearsTargetAndPublishesResetProgress() = runTest {
        val worker = worker(packageName = PACKAGE_NAME)

        val result = worker.doWork()

        assertThat(result).isEqualTo(
            ListenableWorker.Result.success(workDataOf(ResetIfwWorker.PARAM_CLEAR_COUNT to 1, RuleWorkResult.PARAM_CHANGED_COUNT to 1)),
        )
        assertThat(files.fileContent(PACKAGE_NAME)).isNull()
        assertThat(files.fileContent(OTHER_PACKAGE_NAME)).isNotNull()
        assertThat(wakeResets).containsExactly(PACKAGE_NAME)
        assertThat(worker.getNotificationTitle()).isEqualTo(R.string.core_rule_reset_ifw_please_wait)
        val extras = foregroundUpdates.single().notification.extras
        assertThat(extras.getCharSequence(Notification.EXTRA_TITLE)?.toString())
            .isEqualTo(context.getString(R.string.core_rule_reset_ifw_please_wait))
        assertThat(extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()).isEqualTo("$PACKAGE_NAME.xml")
        assertThat(extras.getInt(Notification.EXTRA_PROGRESS)).isEqualTo(1)
        assertThat(extras.getInt(Notification.EXTRA_PROGRESS_MAX)).isEqualTo(1)
    }

    @Test
    fun givenMissingRoot_whenResetWorkerRuns_thenItReportsMissingPermission() = runTest {
        files.noAccess = true

        val result = worker().doWork()

        assertThat(result).isEqualTo(
            ListenableWorker.Result.failure(workDataOf(RuleWorkResult.PARAM_WORK_RESULT to RuleWorkResult.MISSING_ROOT_PERMISSION)),
        )
        assertThat(files.fileContent(PACKAGE_NAME)).isNotNull()
        assertThat(foregroundUpdates).isEmpty()
    }

    @Test
    fun givenDeleteFails_whenResetWorkerRuns_thenItReportsUnexpectedError() = runTest {
        files.writeFailure = IOException("Cannot delete the rule file")

        val result = worker(packageName = PACKAGE_NAME).doWork()

        assertThat(result).isEqualTo(
            ListenableWorker.Result.failure(workDataOf(RuleWorkResult.PARAM_WORK_RESULT to RuleWorkResult.UNEXPECTED_EXCEPTION)),
        )
        assertThat(files.fileContent(PACKAGE_NAME)).isNotNull()
    }

    @Test
    fun givenForegroundStartIsDisallowed_whenResetWorkerRuns_thenItStillClearsTheRules() = runTest {
        val worker = worker(packageName = PACKAGE_NAME, foregroundFailure = ForegroundServiceStartNotAllowedException("Background start"))

        val result = worker.doWork()

        assertThat(result).isInstanceOf(ListenableWorker.Result.Success::class.java)
        assertThat(files.fileContent(PACKAGE_NAME)).isNull()
        assertThat(foregroundUpdates).hasSize(1)
    }

    private fun TestScope.worker(packageName: String? = null, foregroundFailure: IllegalStateException? = null): ResetIfwWorker {
        WorkManagerTestInitHelper.initializeTestWorkManager(context)
        val wake = ManagedWakeReset {
            wakeResets += it
            true
        }
        val store = IntentFirewallStore(files, IfwXmlSerializer(), IfwXmlDeserializer(), context.packageName)
        val repository = LocalFirewallResetRepository(wake, store)
        val dispatcher = StandardTestDispatcher(testScheduler)
        return TestListenableWorkerBuilder<ResetIfwWorker>(context, inputData = ResetIfwWorker.clearIfwWork(packageName).workSpec.input)
            .setWorkerFactory(
                object : WorkerFactory() {
                    override fun createWorker(appContext: Context, workerClassName: String, workerParameters: WorkerParameters) = ResetIfwWorker(appContext, repository, workerParameters, dispatcher)
                },
            )
            .setForegroundUpdater { _, _, info ->
                foregroundUpdates += info
                if (foregroundFailure == null) Futures.immediateVoidFuture() else Futures.immediateFailedFuture(foregroundFailure)
            }
            .build()
    }
}
