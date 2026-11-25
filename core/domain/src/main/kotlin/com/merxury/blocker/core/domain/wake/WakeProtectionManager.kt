// Copyright 2026 Blocker
// SPDX-License-Identifier: Apache-2.0

package com.merxury.blocker.core.domain.wake

import com.merxury.blocker.core.data.wake.WakeProtectionRepository
import com.merxury.blocker.core.data.wake.WakeRepository
import com.merxury.blocker.core.data.wake.WakeRuleLock
import com.merxury.blocker.core.database.wake.WakeSourceStatus
import com.merxury.blocker.core.dispatchers.BlockerDispatchers.IO
import com.merxury.blocker.core.dispatchers.Dispatcher
import com.merxury.core.ifw.ManagedWakeReset
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WakeProtectionManager @Inject constructor(
    private val repository: WakeRepository,
    private val protection: WakeProtectionRepository,
    private val lock: WakeRuleLock,
    @Dispatcher(IO) private val ioDispatcher: CoroutineDispatcher,
) {
    /** Resets the wake rules of [packageName], or all; false when the channel cannot write them. */
    suspend fun resetFirewall(packageName: String?): Boolean = withContext(ioDispatcher) {
        protection.resetFirewall(packageName)
    }

    suspend fun reconcile(): Boolean = withContext(ioDispatcher) {
        lock.withLock {
            val now = System.currentTimeMillis()
            try {
                if (!repository.isOwnerProfile) {
                    repository.reportStatus(WakeSourceStatus("protection", now, "OWNER_PROFILE_REQUIRED"))
                    return@withLock true
                }
                // The channel the user chose reads as any identity; it is the only way in.
                val identity = repository.readAccess()
                if (identity == null) {
                    repository.reportStatus(WakeSourceStatus("authorization", now, "WAITING_FOR_PERMISSION"))
                    return@withLock true
                }
                val writes = protection.writeAccess()
                protection.revokeStaleFirewallRules(writes)
                protection.syncUserFirewallRules(writes)
                val capture = repository.collect(now - repository.lastDiagnostic >= 86_400_000, identity)
                val records = repository.attributionRecords(capture.boot)
                val relations = WakeAttribution.relationships(records)
                repository.saveAttribution(records, relations, WakeAttribution.continuations(records, relations))
                detect(now)
                if (capture.everyReadRefused) {
                    // Shizuku before 13 refuses the calls reads need; retrying cannot change that.
                    repository.reportStatus(WakeSourceStatus("protection", now, "OBSERVING_NOTHING_READS_REFUSED"))
                    return@withLock true
                }
                if (capture.profiles.isEmpty()) {
                    repository.reportStatus(WakeSourceStatus("protection", now, "PROFILE_SCOPE_UNAVAILABLE"))
                    return@withLock false
                }
                val status = protection.applyReviewedPaths(capture, writes, now)
                repository.reportStatus(WakeSourceStatus("protection", now, status))
                // A capture the channel could not finish runs again soon instead of in half an hour.
                !capture.channelUnavailable
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                repository.reportStatus(WakeSourceStatus("protection", now, e.message ?: e.javaClass.simpleName))
                false
            }
        }
    }

    /** Detection only reads evidence and writes its own tables. A failure is reported and never stops enforcement. */
    private suspend fun detect(now: Long) {
        try {
            val zone = ZoneId.systemDefault()
            val window = WakeDetection.window(now, repository.retentionStart(now), repository.oldestRecord(), repository.oldestImported(), zone)
            val since = WakeDetection.dayStartMillis(window.from, zone) - WakeRevives.KILL_WINDOW / 1_000_000
            val detected = WakeDetection.detect(repository.detectionEvidence(since, now), zone)
            repository.saveDetection(window.from, window.through, WakeDetection.daily(detected, zone), WakeDetection.verdicts(detected, zone), window.firstKeptDay)
            repository.reportStatus(WakeSourceStatus("detection", now, "OK"))
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            repository.reportStatus(WakeSourceStatus("detection", now, e.message ?: e.javaClass.simpleName))
        }
    }
}

@Module
@InstallIn(SingletonComponent::class)
internal object WakeResetModule {
    @Provides fun wakeReset(manager: WakeProtectionManager) = ManagedWakeReset(manager::resetFirewall)
}
