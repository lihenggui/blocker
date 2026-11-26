// Copyright 2026 Blocker
// SPDX-License-Identifier: Apache-2.0

package com.merxury.blocker.core.domain.wake

import com.merxury.blocker.core.data.wake.WakeProtectionRepository
import com.merxury.blocker.core.data.wake.WakeRepository
import com.merxury.blocker.core.data.wake.WakeRuleLock
import com.merxury.blocker.core.data.wake.WakeWrites
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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import timber.log.Timber
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
    // Keep captures and history imports serial while allowing users to change the rules between phases.
    private val reconciliation = Mutex()

    /** Resets the wake rules of [packageName], or all; false when the channel cannot write them. */
    suspend fun resetFirewall(packageName: String?): Boolean = withContext(ioDispatcher) {
        protection.resetFirewall(packageName)
    }

    suspend fun reconcile(): Boolean = withContext(ioDispatcher) {
        reconciliation.withLock {
            val now = System.currentTimeMillis()
            try {
                if (!repository.isOwnerProfile) {
                    repository.reportStatus(WakeSourceStatus(PROTECTION_SOURCE, now, "OWNER_PROFILE_REQUIRED"))
                    return@withLock true
                }
                // The channel the user chose reads as any identity; it is the only way in.
                val identity = repository.readAccess()
                if (identity == null) {
                    repository.reportStatus(WakeSourceStatus("authorization", now, "WAITING_FOR_PERMISSION"))
                    return@withLock true
                }
                val initialWrites = lock.withLock {
                    val writes = protection.writeAccess()
                    protection.revokeStaleFirewallRules(writes)
                    syncUserFirewallRules(writes, now)
                    writes
                }
                val capture = repository.collect(now - repository.lastDiagnostic >= 86_400_000, identity)
                val records = repository.attributionRecords(capture.boot)
                val relations = WakeAttribution.relationships(records)
                repository.saveAttribution(records, relations, WakeAttribution.continuations(records, relations))
                detect(now)
                if (capture.everyReadRefused) {
                    // Shizuku before 13 refuses the calls reads need; retrying cannot change that.
                    repository.reportStatus(WakeSourceStatus(PROTECTION_SOURCE, now, READS_REFUSED_STATUS))
                    return@withLock true
                }
                if (0 !in capture.profiles) {
                    repository.reportStatus(WakeSourceStatus(PROTECTION_SOURCE, now, "PROFILE_SCOPE_UNAVAILABLE"))
                    return@withLock false
                }
                val status = lock.withLock {
                    val writes = protection.writeAccess()
                    if (initialWrites != WakeWrites.Enforce) syncUserFirewallRules(writes, now)
                    protection.applyReviewedPaths(capture, writes, now)
                }
                repository.reportStatus(WakeSourceStatus(PROTECTION_SOURCE, now, status))
                // A capture the channel could not finish runs again soon instead of in half an hour.
                !capture.channelUnavailable
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                repository.reportStatus(WakeSourceStatus(PROTECTION_SOURCE, now, e.message ?: e.javaClass.simpleName))
                false
            }
        }
    }

    /** Reports user firewall synchronization independently of evidence collection and protection. */
    private suspend fun syncUserFirewallRules(writes: WakeWrites, now: Long) {
        if (writes != WakeWrites.Enforce) return
        val status = try {
            protection.syncUserFirewallRules(writes)
            "OK"
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.w(e, "Cannot synchronize user wake firewall rules")
            e.message ?: e.javaClass.simpleName
        }
        repository.reportStatus(WakeSourceStatus("user firewall", now, status))
    }

    /** Detection only reads evidence and writes its own tables. A failure is reported and never stops enforcement. */
    private suspend fun detect(now: Long) {
        try {
            val zone = ZoneId.systemDefault()
            val window = WakeDetection.window(now, repository.retentionStart(now), repository.oldestRecord(), repository.oldestImported(), zone)
            val since = WakeDetection.dayStartMillis(window.from, zone) - WakeRevives.KILL_WINDOW / 1_000_000
            val detected = WakeDetection.detect(repository.detectionEvidence(since, now), zone)
            repository.saveDetection(window.from, window.through, WakeDetection.daily(detected, zone), WakeDetection.verdicts(detected, zone), window.firstKeptDay)
            repository.reportStatus(WakeSourceStatus(DETECTION_SOURCE, now, DETECTION_OK_STATUS))
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            repository.reportStatus(WakeSourceStatus(DETECTION_SOURCE, now, e.message ?: e.javaClass.simpleName))
        }
    }
}

internal const val PROTECTION_SOURCE = "protection"
internal const val READS_REFUSED_STATUS = "OBSERVING_NOTHING_READS_REFUSED"
internal const val DETECTION_SOURCE = "detection"
internal const val DETECTION_OK_STATUS = "OK"

@Module
@InstallIn(SingletonComponent::class)
internal object WakeResetModule {
    @Provides fun wakeReset(manager: WakeProtectionManager) = ManagedWakeReset(manager::resetFirewall)
}
