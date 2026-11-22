// Copyright 2026 Blocker
// SPDX-License-Identifier: Apache-2.0

package com.merxury.blocker.core.database.wake

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Transaction
import com.merxury.blocker.core.model.wake.SYSTEM_WAKE_EVENTS
import kotlinx.coroutines.flow.Flow

/** A procstats period. Its rows always hold the latest read of that period, never a sum of reads. */
@Entity
data class WakeHoldPeriod(@PrimaryKey val periodId: String, val startMillis: Long, val endMillis: Long, val checkedAt: Long)

/** Process uptime in hold states within one period, split by screen state, and the part spent as a foreground service. */
@Entity(primaryKeys = ["periodId", "target", "uid", "process"])
data class WakeHoldProcess(
    val periodId: String,
    val target: String,
    val uid: Int,
    val process: String,
    val screenOffMillis: Long,
    val screenOnMillis: Long,
    val fgsScreenOffMillis: Long = 0,
    val fgsScreenOnMillis: Long = 0,
)

/**
 * A binding or provider connection of [target] from a client of another uid within one period: how
 * long it existed, and how long its client was above cached.
 */
@Entity(primaryKeys = ["periodId", "target", "uid", "component", "sourceProcess", "sourceUid"])
data class WakeHoldAssociation(
    val periodId: String,
    val target: String,
    val uid: Int,
    val component: String,
    val sourceProcess: String,
    val sourceUid: Int,
    val boundMillis: Long,
    val activeMillis: Long,
)

/** Start-info signatures already examined. Rejected records are kept too, so an older boot cannot reappear later. */
@Entity(indices = [Index("firstSeen")])
data class WakeStartInfoSeen(@PrimaryKey val signature: String, val firstSeen: Long)

/** Findings of one local day aggregated per path and classification. */
@Entity(tableName = "WakeFinding", indices = [Index(value = ["target", "day"]), Index("day")])
data class WakeFindingDay(
    @PrimaryKey val id: String,
    val day: Long,
    val target: String,
    val pathKey: String,
    val trigger: String?,
    val hold: String?,
    val actor: String?,
    val detail: String?,
    val actorInBackground: Boolean?,
    val afterKill: String?,
    val justification: String,
    val confidence: String,
    val chainRoot: String?,
    val chainDepth: Int,
    val count: Long,
    val durationNanos: Long,
    val screenOffNanos: Long,
    val lastSeenNanos: Long,
    // Cold starts further down the chains that these origin revives began, direct and transitive.
    val downstreamStarts: Long,
)

/** The verdict of one package for one local day, with the counters that produced it. */
@Entity(tableName = "WakeVerdict", primaryKeys = ["day", "target"], indices = [Index("target")])
data class WakeVerdictDay(
    val day: Long,
    val target: String,
    val verdict: String,
    val revives: Long,
    val noneRevives: Long,
    val afterKillRevives: Long,
    val afterKillNoneRevives: Long,
    val peerBackgroundNoneRevives: Long,
    val screenOffHoldNoneNanos: Long,
    val wakesOthers: Long,
    // Cold starts down the chains whose origin revive this package's request caused.
    val downstreamStarts: Long,
)

/** The latest power exemption snapshot. Null means the source was unavailable. */
@Entity
data class WakeExemption(
    @PrimaryKey val target: String,
    val idleWhitelist: String?,
    val standbyBucket: Int?,
    val backgroundRestricted: Boolean?,
    val checkedAt: Long,
)

@Dao
abstract class WakeDetectionDao {
    @Query("SELECT MAX(wallMillis) FROM WakeRecord WHERE boot = :boot AND source = '$SYSTEM_WAKE_EVENTS'")
    abstract suspend fun latestEvent(boot: String): Long?

    @Query("SELECT periodId FROM WakeHoldPeriod ORDER BY periodId DESC LIMIT 1")
    abstract suspend fun latestPeriod(): String?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun putPeriod(period: WakeHoldPeriod)

    @Query("DELETE FROM WakeHoldProcess WHERE periodId = :periodId")
    abstract suspend fun clearProcesses(periodId: String)

    @Query("DELETE FROM WakeHoldAssociation WHERE periodId = :periodId")
    abstract suspend fun clearAssociations(periodId: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun putProcesses(rows: List<WakeHoldProcess>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun putAssociations(rows: List<WakeHoldAssociation>)

    /** Replaces everything known about a period with a newer read of it. */
    @Transaction
    open suspend fun replacePeriod(period: WakeHoldPeriod, processes: List<WakeHoldProcess>, associations: List<WakeHoldAssociation>) {
        clearProcesses(period.periodId)
        clearAssociations(period.periodId)
        putPeriod(period)
        putProcesses(processes)
        putAssociations(associations)
    }

    @Query("SELECT signature FROM WakeStartInfoSeen WHERE signature IN (:signatures)")
    abstract suspend fun seenSignatures(signatures: List<String>): List<String>

    /** The signatures among [signatures] that were not imported before. */
    suspend fun unseen(signatures: List<String>): Set<String> {
        val distinct = signatures.distinct()
        val seen = distinct.chunked(900).flatMap { seenSignatures(it) }.toSet()
        return distinct.filterNot { it in seen }.toSet()
    }

    @Query("DELETE FROM WakeExemption")
    abstract suspend fun clearExemptions()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun putExemptions(rows: List<WakeExemption>)

    @Transaction
    open suspend fun replaceExemptions(rows: List<WakeExemption>) {
        clearExemptions()
        putExemptions(rows)
    }

    @Query(
        """
        SELECT * FROM WakeRecord WHERE wallMillis >= :since AND kind IN (
          'ApplicationStartInfo','ProcessStartTime','am_proc_start','am_kill','AppProcessDied','am_schedule_service_restart',
          'ServiceRequestEventReported','BroadcastDeliveryEventReported','ProviderAcquisitionEventReported','AppStartOccurred',
          'ScheduledJobStateChanged','SyncStateChanged','WakeupAlarmOccurred','AlarmBatchDelivered','NotificationReported',
          'ForegroundServiceStateChanged','LongPartialWakelockStateChanged',
          'SystemBinding','RoleHolder','CompanionAssociation','WallpaperBinding','HeldConnection','NotificationsOff'
        ) ORDER BY elapsedNanos
        """,
    )
    abstract suspend fun detectionRecords(since: Long): List<WakeRecord>

    @Query("SELECT MIN(wallMillis) FROM WakeRecord")
    abstract suspend fun oldestRecord(): Long?

    // Import identifiers expire by time only, so an older entry than any record means records were trimmed.
    @Query("SELECT MIN(observedAt) FROM ImportedWakeId")
    abstract suspend fun oldestImported(): Long?

    @Query("SELECT * FROM WakeRelation")
    abstract suspend fun relations(): List<WakeRelation>

    @Query("SELECT * FROM WakeContinuation")
    abstract suspend fun continuations(): List<WakeContinuation>

    @Query("SELECT * FROM WakeHoldPeriod WHERE endMillis >= :since ORDER BY startMillis")
    abstract suspend fun periods(since: Long): List<WakeHoldPeriod>

    @Query("SELECT p.* FROM WakeHoldProcess p JOIN WakeHoldPeriod h ON p.periodId = h.periodId WHERE h.endMillis >= :since")
    abstract suspend fun holdProcesses(since: Long): List<WakeHoldProcess>

    @Query("SELECT a.* FROM WakeHoldAssociation a JOIN WakeHoldPeriod h ON a.periodId = h.periodId WHERE h.endMillis >= :since")
    abstract suspend fun holdAssociations(since: Long): List<WakeHoldAssociation>

    @Query("DELETE FROM WakeFinding WHERE day BETWEEN :from AND :through")
    abstract suspend fun clearFindings(from: Long, through: Long)

    @Query("DELETE FROM WakeVerdict WHERE day BETWEEN :from AND :through")
    abstract suspend fun clearVerdicts(from: Long, through: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun putFindings(rows: List<WakeFindingDay>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun putVerdicts(rows: List<WakeVerdictDay>)

    @Query("DELETE FROM WakeFinding WHERE day < :day")
    abstract suspend fun expireFindings(day: Long)

    @Query("DELETE FROM WakeVerdict WHERE day < :day")
    abstract suspend fun expireVerdicts(day: Long)

    @Query("DELETE FROM WakeHoldPeriod WHERE endMillis < :cutoff")
    abstract suspend fun expirePeriods(cutoff: Long)

    @Query("DELETE FROM WakeHoldProcess WHERE periodId NOT IN (SELECT periodId FROM WakeHoldPeriod)")
    abstract suspend fun pruneProcesses()

    @Query("DELETE FROM WakeHoldAssociation WHERE periodId NOT IN (SELECT periodId FROM WakeHoldPeriod)")
    abstract suspend fun pruneAssociations()

    @Query("DELETE FROM WakeStartInfoSeen WHERE firstSeen < :cutoff")
    abstract suspend fun expireStartInfo(cutoff: Long)

    /** Evidence follows the retention of the event history. */
    @Transaction
    open suspend fun expireEvidence(cutoff: Long) {
        expirePeriods(cutoff)
        pruneProcesses()
        pruneAssociations()
        expireStartInfo(cutoff)
    }

    /** Recomputed days replace their previous rows. Days before [firstKeptDay] expire. */
    @Transaction
    open suspend fun replaceDetection(from: Long, through: Long, findings: List<WakeFindingDay>, verdicts: List<WakeVerdictDay>, firstKeptDay: Long) {
        clearFindings(from, through)
        clearVerdicts(from, through)
        putFindings(findings.filter { it.day in from..through })
        putVerdicts(verdicts.filter { it.day in from..through })
        expireFindings(firstKeptDay)
        expireVerdicts(firstKeptDay)
    }

    @Query("SELECT * FROM WakeVerdict ORDER BY day DESC, target")
    abstract fun verdicts(): Flow<List<WakeVerdictDay>>

    @Query("SELECT * FROM WakeFinding WHERE target = :target ORDER BY day DESC, count DESC, pathKey")
    abstract fun findings(target: String): Flow<List<WakeFindingDay>>
}
