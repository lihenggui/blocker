// Copyright 2026 Blocker
// SPDX-License-Identifier: Apache-2.0

package com.merxury.blocker.core.database.wake

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Transaction
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.merxury.blocker.core.model.component.UIDS_PER_USER
import com.merxury.blocker.core.model.wake.SYSTEM_WAKE_EVENTS
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.Serializable
import javax.inject.Singleton

@Entity(indices = [Index("wallMillis"), Index(value = ["boot", "targetUid", "elapsedNanos"]), Index("kind")])
data class WakeRecord(
    @PrimaryKey val id: String,
    val boot: String,
    val elapsedNanos: Long,
    val wallMillis: Long,
    val source: String,
    val kind: String,
    val callerUid: Int?,
    val targetUid: Int?,
    val callerPackage: String?,
    val targetPackage: String?,
    val component: String?,
    val action: String?,
    val startType: Int?,
    val pid: Int?,
    val processName: String?,
    val attributes: String,
)

@Entity(primaryKeys = ["uid", "packageName", "revision"])
data class WakeIdentity(
    val uid: Int,
    val packageName: String,
    val revision: String,
    val versionCode: Long,
    val signatures: String,
    val components: String,
    val firstSeen: Long,
    val lastSeen: Long,
)

@Entity
data class WakeSourceStatus(@PrimaryKey val source: String, val checkedAt: Long, val status: String)

@Entity
data class WakeRelation(
    @PrimaryKey val requestId: String,
    val processId: String,
    val evidence: String,
)

@Entity(indices = [Index("incoming")])
data class WakeContinuation(@PrimaryKey val outgoing: String, val incoming: String)

data class WakeChainSegment(val outgoing: String, val incoming: String, val origin: String, val middle: String, val destination: String, val wallMillis: Long)

@Entity
@Serializable
data class WakeProtection(
    @PrimaryKey val pathId: String,
    val caller: String,
    val target: String,
    val component: String,
    val action: String,
    val proof: String,
    val disabledByUser: Boolean,
    val configured: Boolean,
    val method: String = "IFW",
    val callerRevision: String = "",
    val targetRevision: String = "",
    val targetSigners: String = "",
    val previousState: Int? = null,
    val pendingChange: Boolean = false,
)

@Entity(indices = [Index("observedAt")])
data class ImportedWakeId(@PrimaryKey val id: String, val observedAt: Long)

@Entity(primaryKeys = ["day", "kind"])
data class WakeDailyTotal(val day: Long, val kind: String, val count: Long)

data class WakeReciprocalPair(val firstPackage: String, val secondPackage: String, val forward: Long, val reverse: Long)

data class WakePathSummary(
    val kind: String,
    val caller: String,
    val target: String,
    val component: String?,
    val requests: Long,
    val coldRequests: Long,
    val linkedStarts: Long,
    val latest: Long,
)

@Dao
abstract class WakeHistoryDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract suspend fun insertRecords(records: List<WakeRecord>)

    @Query("INSERT OR IGNORE INTO WakeDailyTotal(day,kind,count) VALUES (:day,:kind,0)")
    abstract suspend fun initDay(day: Long, kind: String)

    @Query("UPDATE WakeDailyTotal SET count = count + :count WHERE day = :day AND kind = :kind")
    abstract suspend fun increment(day: Long, kind: String, count: Long)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract suspend fun markSeen(ids: List<ImportedWakeId>): List<Long>

    @Query("DELETE FROM ImportedWakeId WHERE observedAt < :cutoff")
    abstract suspend fun forgetSeen(cutoff: Long)

    @Query("DELETE FROM WakeDailyTotal WHERE day < :day")
    abstract suspend fun expireTotals(day: Long)

    @Transaction
    open suspend fun importRecords(records: List<WakeRecord>, cutoff: Long) {
        for (chunk in records.filter { it.wallMillis >= cutoff }.chunked(500)) {
            val inserted = markSeen(chunk.map { ImportedWakeId(it.id, it.wallMillis) })
            val fresh = chunk.filterIndexed { i, _ -> inserted[i] != -1L }
            insertRecords(fresh)
            for ((key, count) in fresh.groupingBy { it.wallMillis / 86_400_000 to it.kind }.eachCount()) {
                initDay(key.first, key.second)
                increment(key.first, key.second, count.toLong())
            }
        }
        forgetSeen(cutoff)
        expireTotals(cutoff / 86_400_000 - 365)
        expire(cutoff)
        trim()
        pruneRelations()
        pruneContinuations()
        pruneIdentities(cutoff)
    }

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract suspend fun markStartInfoSeen(rows: List<WakeStartInfoSeen>)

    /** Start-info records count as seen only together with their import. */
    @Transaction
    open suspend fun importStartInfo(records: List<WakeRecord>, seen: List<WakeStartInfoSeen>, cutoff: Long) {
        importRecords(records, cutoff)
        markStartInfoSeen(seen)
    }

    @Query("DELETE FROM WakeRecord WHERE wallMillis < :cutoff")
    abstract suspend fun expire(cutoff: Long)

    @Query("DELETE FROM WakeRecord WHERE id IN (SELECT id FROM WakeRecord ORDER BY wallMillis DESC, id DESC LIMIT -1 OFFSET 30000)")
    abstract suspend fun trim()

    @Query("DELETE FROM WakeRelation WHERE requestId NOT IN (SELECT id FROM WakeRecord) OR processId NOT IN (SELECT id FROM WakeRecord)")
    abstract suspend fun pruneRelations()

    @Query("DELETE FROM WakeIdentity WHERE lastSeen < :cutoff")
    abstract suspend fun pruneIdentities(cutoff: Long)

    @Query("DELETE FROM WakeContinuation WHERE incoming NOT IN (SELECT requestId FROM WakeRelation) OR outgoing NOT IN (SELECT requestId FROM WakeRelation)")
    abstract suspend fun pruneContinuations()

    @Query(
        """
        SELECT c.incoming, c.outgoing, COALESCE(a.callerPackage, CAST(a.callerUid AS TEXT)) AS origin,
          COALESCE(a.targetPackage, CAST(a.targetUid AS TEXT)) AS middle,
          COALESCE(b.targetPackage, CAST(b.targetUid AS TEXT)) AS destination, b.wallMillis
        FROM WakeContinuation c JOIN WakeRecord a ON c.incoming=a.id JOIN WakeRecord b ON c.outgoing=b.id
        ORDER BY b.wallMillis DESC, c.outgoing LIMIT 250
    """,
    )
    abstract fun chains(): Flow<List<WakeChainSegment>>

    @Query("DELETE FROM WakeContinuation WHERE incoming IN (:ids) OR outgoing IN (:ids)")
    abstract suspend fun clearContinuations(ids: List<String>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun putContinuations(rows: List<WakeContinuation>)

    @Query("SELECT * FROM WakeRecord ORDER BY wallMillis DESC, elapsedNanos DESC LIMIT 120")
    abstract fun recent(): Flow<List<WakeRecord>>

    @Query("SELECT * FROM WakeSourceStatus WHERE source LIKE 'firewall-reset/%'")
    abstract suspend fun firewallResets(): List<WakeSourceStatus>

    @Query("UPDATE WakeProtection SET configured = 0, disabledByUser = 1 WHERE method = 'IFW' AND (:target IS NULL OR target = :target)")
    abstract suspend fun muteFirewall(target: String?)

    @Transaction
    open suspend fun resetFirewall(target: String?, now: Long) {
        muteFirewall(target)
        status(WakeSourceStatus("firewall-reset/${target.orEmpty()}", now, "RESET"))
    }

    @Query("SELECT * FROM WakeRecord WHERE boot = :boot AND elapsedNanos >= :since AND ((source = 'statsd' AND kind IN ('ServiceRequestEventReported','ProviderAcquisitionEventReported','BroadcastDeliveryEventReported','ProcessStartTime','AppStartOccurred','AppProcessDied')) OR (source = '$SYSTEM_WAKE_EVENTS' AND kind IN ('am_proc_start','am_proc_died'))) ORDER BY elapsedNanos")
    abstract suspend fun attributionWindow(boot: String, since: Long): List<WakeRecord>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun putRelations(relations: List<WakeRelation>)

    @Query("DELETE FROM WakeRelation WHERE requestId IN (:ids)")
    abstract suspend fun clearRelations(ids: List<String>)

    @Transaction
    open suspend fun replaceRelations(ids: List<String>, relations: List<WakeRelation>, continuations: List<WakeContinuation>) {
        // clearContinuations binds each ID twice. Older Android SQLite accepts 999 parameters.
        ids.chunked(499).forEach {
            clearRelations(it)
            clearContinuations(it)
        }
        putRelations(relations)
        putContinuations(continuations)
    }

    @Query(
        """
        SELECT e.kind, COALESCE(e.callerPackage, CAST(e.callerUid AS TEXT)) AS caller,
          COALESCE(e.targetPackage, CAST(e.targetUid AS TEXT)) AS target, e.component,
          COUNT(*) AS requests, SUM(CASE WHEN e.startType=3 THEN 1 ELSE 0 END) AS coldRequests,
          COUNT(r.requestId) AS linkedStarts, MAX(e.wallMillis) AS latest
        FROM WakeRecord e LEFT JOIN WakeRelation r ON r.requestId=e.id
        WHERE e.kind IN ('ServiceRequestEventReported','BroadcastDeliveryEventReported','ProviderAcquisitionEventReported','AppStartOccurred')
        AND (e.callerPackage IS NOT NULL OR e.callerUid IS NOT NULL)
        AND (e.targetPackage IS NOT NULL OR e.targetUid IS NOT NULL)
        GROUP BY e.callerUid,e.callerPackage,e.targetUid,e.targetPackage,e.component,e.kind
        ORDER BY latest DESC LIMIT 100
        """,
    )
    abstract fun paths(): Flow<List<WakePathSummary>>

    @Query(
        """
        WITH directions AS (
          SELECT e.callerUid, e.targetUid, e.callerPackage, e.targetPackage,
            COUNT(DISTINCT r.processId) AS launches, MAX(e.wallMillis) AS latest
          FROM WakeRecord e JOIN WakeRelation r ON r.requestId=e.id
          WHERE e.kind IN ('ServiceRequestEventReported','ProviderAcquisitionEventReported','BroadcastDeliveryEventReported')
            AND e.startType=3 AND e.callerPackage IS NOT NULL AND e.targetPackage IS NOT NULL
            AND e.callerUid % $UIDS_PER_USER >= 10000 AND e.targetUid % $UIDS_PER_USER >= 10000
            AND e.callerUid / $UIDS_PER_USER = e.targetUid / $UIDS_PER_USER AND e.callerUid != e.targetUid
          GROUP BY e.callerUid,e.targetUid,e.callerPackage,e.targetPackage HAVING COUNT(DISTINCT r.processId)>=2
        )
        SELECT a.callerPackage AS firstPackage, a.targetPackage AS secondPackage, a.launches AS forward, b.launches AS reverse
        FROM directions a JOIN directions b ON a.callerUid=b.targetUid AND a.targetUid=b.callerUid
          AND a.callerPackage=b.targetPackage AND a.targetPackage=b.callerPackage
        WHERE a.callerUid < a.targetUid ORDER BY MAX(a.latest,b.latest) DESC LIMIT 100
    """,
    )
    abstract fun recurringPeers(): Flow<List<WakeReciprocalPair>>

    @Query("SELECT * FROM WakeRelation")
    abstract fun relations(): Flow<List<WakeRelation>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertIdentities(rows: List<WakeIdentity>)

    @Query("UPDATE WakeIdentity SET lastSeen=:now WHERE uid=:uid AND packageName=:name AND revision=:revision")
    abstract suspend fun touch(uid: Int, name: String, revision: String, now: Long)

    @Transaction
    open suspend fun index(rows: List<WakeIdentity>) {
        val (withManifest, previouslyIndexed) = rows.partition { it.components.isNotEmpty() }
        if (withManifest.isNotEmpty()) insertIdentities(withManifest)
        previouslyIndexed.forEach { touch(it.uid, it.packageName, it.revision, it.lastSeen) }
    }

    // The manifest can be larger than Android's cursor window. Attribution needs only identity.
    @Query("SELECT uid,packageName,revision,versionCode,signatures,'' AS components,firstSeen,lastSeen FROM WakeIdentity WHERE lastSeen >= :since")
    abstract suspend fun identities(since: Long): List<WakeIdentity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun status(row: WakeSourceStatus)

    @Query("SELECT * FROM WakeRecord WHERE kind IN ('ScheduledJobStateChanged','PendingAlarm','PendingIntentRegistration') AND boot=:boot AND elapsedNanos >= :since ORDER BY elapsedNanos")
    abstract suspend fun scheduledWork(boot: String, since: Long): List<WakeRecord>

    @Query("SELECT * FROM WakeDailyTotal ORDER BY day DESC, count DESC LIMIT 120")
    abstract fun totals(): Flow<List<WakeDailyTotal>>

    @Query("SELECT * FROM WakeSourceStatus ORDER BY source")
    abstract fun statuses(): Flow<List<WakeSourceStatus>>

    @Query("SELECT * FROM WakeProtection ORDER BY pathId")
    abstract fun protections(): Flow<List<WakeProtection>>

    @Query("SELECT * FROM WakeProtection")
    abstract suspend fun readProtections(): List<WakeProtection>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun putProtections(rows: List<WakeProtection>)

    @Query("SELECT * FROM WakeRecord WHERE ((source = 'statsd' AND kind IN ('ServiceRequestEventReported','BroadcastDeliveryEventReported','ProviderAcquisitionEventReported','ProcessStartTime')) OR (source = '$SYSTEM_WAKE_EVENTS' AND kind = 'am_proc_start') OR (source = 'SERVICES' AND kind = 'HeldConnection' AND action IS NOT NULL)) AND wallMillis >= :since ORDER BY wallMillis DESC")
    abstract suspend fun componentRequests(since: Long): List<WakeRecord>
}

@Database(
    entities = [
        WakeRecord::class, WakeIdentity::class, WakeRelation::class, WakeContinuation::class, WakeSourceStatus::class, WakeProtection::class, WakeDailyTotal::class, ImportedWakeId::class,
        WakeHoldPeriod::class, WakeHoldProcess::class, WakeHoldAssociation::class, WakeStartInfoSeen::class, WakeFindingDay::class, WakeVerdictDay::class, WakeExemption::class, WakeBlockEntity::class,
    ],
    version = 3,
    exportSchema = true,
)
abstract class WakeHistoryDatabase : RoomDatabase() {
    abstract fun history(): WakeHistoryDao
    abstract fun detection(): WakeDetectionDao
    abstract fun blocks(): WakeBlockDao
}

/** Version 2 adds the detection tables. Existing history is kept. */
val WAKE_HISTORY_MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        for (statement in WAKE_DETECTION_TABLES) db.execSQL(statement)
    }
}

// Copied from the exported schema 2.json so Room's validation accepts the migrated database.
internal val WAKE_DETECTION_TABLES = listOf(
    "CREATE TABLE IF NOT EXISTS `WakeHoldPeriod` (`periodId` TEXT NOT NULL, `startMillis` INTEGER NOT NULL, `endMillis` INTEGER NOT NULL, `checkedAt` INTEGER NOT NULL, PRIMARY KEY(`periodId`))",
    "CREATE TABLE IF NOT EXISTS `WakeHoldProcess` (`periodId` TEXT NOT NULL, `target` TEXT NOT NULL, `uid` INTEGER NOT NULL, `process` TEXT NOT NULL, `screenOffMillis` INTEGER NOT NULL, `screenOnMillis` INTEGER NOT NULL, `fgsScreenOffMillis` INTEGER NOT NULL, `fgsScreenOnMillis` INTEGER NOT NULL, PRIMARY KEY(`periodId`, `target`, `uid`, `process`))",
    "CREATE TABLE IF NOT EXISTS `WakeHoldAssociation` (`periodId` TEXT NOT NULL, `target` TEXT NOT NULL, `uid` INTEGER NOT NULL, `component` TEXT NOT NULL, `sourceProcess` TEXT NOT NULL, `sourceUid` INTEGER NOT NULL, `boundMillis` INTEGER NOT NULL, `activeMillis` INTEGER NOT NULL, PRIMARY KEY(`periodId`, `target`, `uid`, `component`, `sourceProcess`, `sourceUid`))",
    "CREATE TABLE IF NOT EXISTS `WakeStartInfoSeen` (`signature` TEXT NOT NULL, `firstSeen` INTEGER NOT NULL, PRIMARY KEY(`signature`))",
    "CREATE INDEX IF NOT EXISTS `index_WakeStartInfoSeen_firstSeen` ON `WakeStartInfoSeen` (`firstSeen`)",
    "CREATE TABLE IF NOT EXISTS `WakeFinding` (`id` TEXT NOT NULL, `day` INTEGER NOT NULL, `target` TEXT NOT NULL, `pathKey` TEXT NOT NULL, `trigger` TEXT, `hold` TEXT, `actor` TEXT, `detail` TEXT, `actorInBackground` INTEGER, `afterKill` TEXT, `justification` TEXT NOT NULL, `confidence` TEXT NOT NULL, `chainRoot` TEXT, `chainDepth` INTEGER NOT NULL, `count` INTEGER NOT NULL, `durationNanos` INTEGER NOT NULL, `screenOffNanos` INTEGER NOT NULL, `lastSeenNanos` INTEGER NOT NULL, `downstreamStarts` INTEGER NOT NULL, PRIMARY KEY(`id`))",
    "CREATE INDEX IF NOT EXISTS `index_WakeFinding_target_day` ON `WakeFinding` (`target`, `day`)",
    "CREATE INDEX IF NOT EXISTS `index_WakeFinding_day` ON `WakeFinding` (`day`)",
    "CREATE TABLE IF NOT EXISTS `WakeVerdict` (`day` INTEGER NOT NULL, `target` TEXT NOT NULL, `verdict` TEXT NOT NULL, `revives` INTEGER NOT NULL, `noneRevives` INTEGER NOT NULL, `afterKillRevives` INTEGER NOT NULL, `afterKillNoneRevives` INTEGER NOT NULL, `peerBackgroundNoneRevives` INTEGER NOT NULL, `screenOffHoldNoneNanos` INTEGER NOT NULL, `wakesOthers` INTEGER NOT NULL, `downstreamStarts` INTEGER NOT NULL, PRIMARY KEY(`day`, `target`))",
    "CREATE INDEX IF NOT EXISTS `index_WakeVerdict_target` ON `WakeVerdict` (`target`)",
    "CREATE TABLE IF NOT EXISTS `WakeExemption` (`target` TEXT NOT NULL, `idleWhitelist` TEXT, `standbyBucket` INTEGER, `backgroundRestricted` INTEGER, `checkedAt` INTEGER NOT NULL, PRIMARY KEY(`target`))",
)

/** Version 3 stores user wake blocks separately from automatic protection. */
val WAKE_HISTORY_MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        for (statement in WAKE_BLOCK_TABLES) db.execSQL(statement)
    }
}

// Copied from the exported schema 3.json.
internal val WAKE_BLOCK_TABLES = listOf(
    "CREATE TABLE IF NOT EXISTS `WakeBlock` (`pathKey` TEXT NOT NULL, `target` TEXT NOT NULL, `method` TEXT NOT NULL, `componentType` TEXT, `sender` TEXT, `component` TEXT, `action` TEXT, `blockedAt` INTEGER NOT NULL, PRIMARY KEY(`pathKey`))",
    "CREATE INDEX IF NOT EXISTS `index_WakeBlock_target` ON `WakeBlock` (`target`)",
)

@Module
@InstallIn(SingletonComponent::class)
object WakeDatabaseModule {
    @Provides
    @Singleton
    fun database(@ApplicationContext context: Context): WakeHistoryDatabase = Room.databaseBuilder(context, WakeHistoryDatabase::class.java, "wake_history")
        .addMigrations(WAKE_HISTORY_MIGRATION_1_2, WAKE_HISTORY_MIGRATION_2_3).build()

    @Provides
    fun history(database: WakeHistoryDatabase): WakeHistoryDao = database.history()

    @Provides
    fun detection(database: WakeHistoryDatabase): WakeDetectionDao = database.detection()

    @Provides
    fun blocks(database: WakeHistoryDatabase): WakeBlockDao = database.blocks()
}
