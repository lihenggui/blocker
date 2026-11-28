// Copyright 2026 Blocker
// SPDX-License-Identifier: Apache-2.0

package com.merxury.blocker.sync.workers

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.ListenableWorker.Result
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.TestListenableWorkerBuilder
import com.google.common.truth.Truth.assertThat
import com.merxury.blocker.core.data.wake.WakeImport
import com.merxury.blocker.core.data.wake.WakeProtectionRepository
import com.merxury.blocker.core.data.wake.WakeRepository
import com.merxury.blocker.core.data.wake.WakeRuleLock
import com.merxury.blocker.core.data.wake.WakeWrites
import com.merxury.blocker.core.domain.wake.WakeProtectionManager
import com.merxury.blocker.core.model.privileged.PrivilegedIdentity
import com.merxury.blocker.core.model.wake.WakeContinuation
import com.merxury.blocker.core.model.wake.WakeFindingDay
import com.merxury.blocker.core.model.wake.WakeProtection
import com.merxury.blocker.core.model.wake.WakeRecord
import com.merxury.blocker.core.model.wake.WakeRelation
import com.merxury.blocker.core.model.wake.WakeSourceStatus
import com.merxury.blocker.core.model.wake.WakeVerdictDay
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.IOException

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class WakeHistoryWorkerTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val repository = WakeAccessProbe()

    @Test
    fun givenReconciliationSucceeds_whenWorkRuns_thenItSucceeds() = runTest {
        assertThat(worker().doWork()).isEqualTo(Result.success())
        assertThat(repository.accessChecks).isEqualTo(1)
    }

    @Test
    fun givenReconciliationFails_whenAnAttemptRemains_thenWorkRetries() = runTest {
        repository.readFailure = IOException("Wake history unavailable")

        assertThat(worker(attempt = MAX_RETRY_ATTEMPTS - 2).doWork()).isEqualTo(Result.retry())
        assertThat(repository.accessChecks).isEqualTo(1)
    }

    @Test
    fun givenReconciliationFails_whenTheFinalAttemptRuns_thenWorkFails() = runTest {
        repository.readFailure = IOException("Wake history unavailable")

        assertThat(worker(attempt = MAX_RETRY_ATTEMPTS - 1).doWork()).isEqualTo(Result.failure())
        assertThat(repository.accessChecks).isEqualTo(1)
    }

    @Test
    fun givenTheRetryLimitIsReached_whenWorkRuns_thenItFailsWithoutReconciling() = runTest {
        assertThat(worker(attempt = MAX_RETRY_ATTEMPTS).doWork()).isEqualTo(Result.failure())
        assertThat(repository.accessChecks).isEqualTo(0)
    }

    private fun TestScope.worker(attempt: Int = 0): WakeHistoryWorker {
        val manager = WakeProtectionManager(repository, UnusedWakeProtection, WakeRuleLock(), StandardTestDispatcher(testScheduler))
        return TestListenableWorkerBuilder<WakeHistoryWorker>(context)
            .setRunAttemptCount(attempt)
            .setWorkerFactory(
                object : WorkerFactory() {
                    override fun createWorker(appContext: Context, workerClassName: String, workerParameters: WorkerParameters) = WakeHistoryWorker(appContext, workerParameters, manager)
                },
            )
            .build()
    }
}

// Reconciliation exits at the quiet access check, before reading history or touching the firewall.
private class WakeAccessProbe : WakeRepository {
    var accessChecks = 0
        private set
    var readFailure: IOException? = null

    override val isOwnerProfile = true

    override suspend fun readAccess(): PrivilegedIdentity? {
        accessChecks++
        readFailure?.let { throw it }
        return null
    }

    override suspend fun reportStatus(status: WakeSourceStatus) = Unit
    override val rulesPending: Boolean get() = unexpectedWakeCall()
    override val lastDiagnostic: Long get() = unexpectedWakeCall()
    override fun markRulesPending(value: Boolean): Nothing = unexpectedWakeCall()
    override suspend fun attributionRecords(boot: String): Nothing = unexpectedWakeCall()
    override suspend fun saveAttribution(records: List<WakeRecord>, relations: List<WakeRelation>, continuations: List<WakeContinuation>): Nothing = unexpectedWakeCall()
    override suspend fun observedComponents(since: Long): Nothing = unexpectedWakeCall()
    override suspend fun resetFirewall(target: String?): Nothing = unexpectedWakeCall()
    override suspend fun firewallResets(): Nothing = unexpectedWakeCall()
    override suspend fun protections(): Nothing = unexpectedWakeCall()
    override suspend fun saveProtections(paths: List<WakeProtection>): Nothing = unexpectedWakeCall()
    override fun statuses(): Nothing = unexpectedWakeCall()
    override fun retentionStart(now: Long): Nothing = unexpectedWakeCall()
    override suspend fun oldestRecord(): Nothing = unexpectedWakeCall()
    override suspend fun oldestImported(): Nothing = unexpectedWakeCall()
    override suspend fun detectionEvidence(since: Long, now: Long): Nothing = unexpectedWakeCall()
    override suspend fun saveDetection(from: Long, through: Long, findings: List<WakeFindingDay>, verdicts: List<WakeVerdictDay>, firstKeptDay: Long): Nothing = unexpectedWakeCall()
    override fun hasFindings(): Nothing = unexpectedWakeCall()
    override fun findingsInvolving(packageName: String, fromDay: Long): Nothing = unexpectedWakeCall()
    override fun findingsSince(fromDay: Long): Nothing = unexpectedWakeCall()
    override fun verdictsSince(fromDay: Long): Nothing = unexpectedWakeCall()
    override fun observeExemptions(targets: List<String>): Nothing = unexpectedWakeCall()
    override fun observeSpecialBindings(targets: List<String>): Nothing = unexpectedWakeCall()
    override suspend fun collect(diagnostic: Boolean, identity: PrivilegedIdentity): Nothing = unexpectedWakeCall()
}

private object UnusedWakeProtection : WakeProtectionRepository {
    override suspend fun writeAccess(): Nothing = unexpectedWakeCall()
    override suspend fun resetFirewall(packageName: String?): Nothing = unexpectedWakeCall()
    override suspend fun syncUserFirewallRules(writes: WakeWrites): Nothing = unexpectedWakeCall()
    override suspend fun applyReviewedPaths(capture: WakeImport, writes: WakeWrites, now: Long): Nothing = unexpectedWakeCall()
    override suspend fun revokeStaleFirewallRules(writes: WakeWrites): Nothing = unexpectedWakeCall()
    override suspend fun supportsPeerFilter(): Nothing = unexpectedWakeCall()
}

private fun unexpectedWakeCall(): Nothing = throw AssertionError("Reconciliation should stop at the wake access check")
