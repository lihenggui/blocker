// Copyright 2026 Blocker
// SPDX-License-Identifier: Apache-2.0

package com.merxury.blocker.core.data.device

import android.app.AppOpsManager.MODE_ALLOWED
import android.app.AppOpsManager.MODE_DEFAULT
import android.app.AppOpsManager.MODE_IGNORED
import android.content.Context
import com.merxury.blocker.core.appcontrol.AppRestrictionControl
import com.merxury.blocker.core.appcontrol.AppRestrictionReading
import com.merxury.blocker.core.dispatchers.BlockerDispatchers.IO
import com.merxury.blocker.core.dispatchers.Dispatcher
import com.merxury.blocker.core.model.component.AppKey
import com.merxury.blocker.core.model.privileged.CallFailure
import com.merxury.blocker.core.model.privileged.CallResult
import com.merxury.blocker.core.model.privileged.ChannelState
import com.merxury.blocker.core.model.system.DeepBackgroundRestriction
import com.merxury.blocker.core.model.system.DeepRestrictionSnapshot
import com.merxury.blocker.core.privileged.PrivilegedChannel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

interface AppRestrictionRepository {
    /** Observes the setting, refreshing quietly on subscription and when access becomes ready. */
    fun observe(app: AppKey): Flow<DeepBackgroundRestriction?>

    /** Reads the current state without prompting for privileged access. */
    suspend fun read(app: AppKey): AppRestrictionResult

    /** May prompt for access. Returns the system state read after the change. */
    suspend fun setDeepRestricted(app: AppKey, restricted: Boolean): AppRestrictionResult

    suspend fun restoreOwned(app: AppKey): AppRestrictionRestoreResult

    suspend fun reapplyOwned(
        app: AppKey,
        target: DeepRestrictionSnapshot,
        baseline: DeepRestrictionSnapshot,
        firstInstallTime: Long? = null,
    ): AppRestrictionResult
}

sealed interface AppRestrictionResult {
    data class Success(val value: DeepBackgroundRestriction) : AppRestrictionResult
    data class Failure(val reason: AppRestrictionFailure) : AppRestrictionResult
}

sealed interface AppRestrictionRestoreResult {
    data object NotOwned : AppRestrictionRestoreResult
    data class Restored(
        val reapply: DeepRestrictionSnapshot,
        val baseline: DeepRestrictionSnapshot,
        val firstInstallTime: Long,
    ) : AppRestrictionRestoreResult
    data class Failure(val reason: AppRestrictionFailure) : AppRestrictionRestoreResult
}

enum class AppRestrictionFailure { NO_ACCESS, NOT_INSTALLED, NOT_CHANGEABLE, SYSTEM_ERROR }

@Singleton
internal class LocalAppRestrictionRepository @Inject constructor(
    private val channel: PrivilegedChannel,
    private val control: AppRestrictionControl,
    @ApplicationContext context: Context,
    @Dispatcher(IO) private val io: CoroutineDispatcher,
) : AppRestrictionRepository {
    private val access = Mutex()
    private val settings = MutableStateFlow<Map<AppKey, DeepBackgroundRestriction?>>(emptyMap())
    private val journal = context.getSharedPreferences("wake_deep_restriction_journal", Context.MODE_PRIVATE)

    override fun observe(app: AppKey): Flow<DeepBackgroundRestriction?> = channelFlow {
        send(settings.value[app])
        launch(start = CoroutineStart.UNDISPATCHED) {
            this@LocalAppRestrictionRepository.channel.state.map { it is ChannelState.Ready }
                .distinctUntilChanged().drop(1).filter { it }.collect { read(app) }
        }
        launch { read(app) }
        settings.map { it[app] }.collect { send(it) }
    }.distinctUntilChanged()

    override suspend fun read(app: AppKey): AppRestrictionResult = withAccess {
        if (channel.connectIfGranted() !is ChannelState.Ready) return@withAccess unavailable(app)
        readConnected(app).toResult()
    }

    override suspend fun setDeepRestricted(app: AppKey, restricted: Boolean): AppRestrictionResult = withAccess {
        when (val read = readForWrite(app)) {
            is CallResult.Failure -> read.toResult()

            is CallResult.Success -> {
                val before = read.value
                val existing = entry(app)?.takeIf { it.installTime == before.firstInstallTime }
                if (restricted) {
                    if (!before.setting.changeable) return@withAccess AppRestrictionResult.Failure(AppRestrictionFailure.NOT_CHANGEABLE)
                    val baseline = existing?.let { before.snapshot.restoreMatching(it.written, it.baseline) } ?: before.snapshot
                    val desired = DeepRestrictionSnapshot(
                        before.snapshot.standbyBucket?.let { before.restrictedStandbyBucket },
                        MODE_IGNORED,
                        before.snapshot.exactAlarmMode?.let { MODE_IGNORED },
                    )
                    change(app, before, desired, JournalEntry(before.firstInstallTime, baseline, desired))
                } else {
                    change(app, before, existing?.let { before.manualClearTarget(it) })
                }
            }
        }
    }

    override suspend fun restoreOwned(app: AppKey): AppRestrictionRestoreResult = withAccess {
        val saved = entry(app) ?: return@withAccess AppRestrictionRestoreResult.NotOwned
        when (val read = readForWrite(app)) {
            is CallResult.Failure -> when (val reason = read.reason.toRestrictionFailure()) {
                AppRestrictionFailure.NOT_INSTALLED -> AppRestrictionRestoreResult.NotOwned
                else -> AppRestrictionRestoreResult.Failure(reason)
            }

            is CallResult.Success -> {
                val before = read.value
                if (before.firstInstallTime != saved.installTime) return@withAccess AppRestrictionRestoreResult.NotOwned
                val target = before.snapshot.restoreMatching(saved.written, saved.baseline)
                when (val result = change(app, before, target)) {
                    is AppRestrictionResult.Success -> if (target == before.snapshot) {
                        AppRestrictionRestoreResult.NotOwned
                    } else {
                        AppRestrictionRestoreResult.Restored(before.snapshot, target, saved.installTime)
                    }

                    is AppRestrictionResult.Failure -> AppRestrictionRestoreResult.Failure(result.reason)
                }
            }
        }
    }

    override suspend fun reapplyOwned(
        app: AppKey,
        target: DeepRestrictionSnapshot,
        baseline: DeepRestrictionSnapshot,
        firstInstallTime: Long?,
    ): AppRestrictionResult = withAccess {
        when (val read = readForWrite(app)) {
            is CallResult.Failure -> read.toResult()

            is CallResult.Success -> {
                val before = read.value
                if (firstInstallTime != null && before.firstInstallTime != firstInstallTime) {
                    return@withAccess AppRestrictionResult.Failure(AppRestrictionFailure.NOT_INSTALLED)
                }
                val desired = before.snapshot.restoreMatching(baseline, target)
                if (desired == before.snapshot) return@withAccess AppRestrictionResult.Success(before.setting)
                change(app, before, desired, JournalEntry(before.firstInstallTime, before.snapshot, desired))
            }
        }
    }

    private suspend fun <T> withAccess(action: suspend () -> T): T = access.withLock { withContext(io) { action() } }

    private suspend fun change(
        app: AppKey,
        before: AppRestrictionReading,
        target: DeepRestrictionSnapshot?,
        pending: JournalEntry? = null,
    ): AppRestrictionResult {
        currentCoroutineContext().ensureActive()
        // Keep a started system write and its journal update together if the caller closes.
        return withContext(NonCancellable) { writeAndSave(app, before, target, pending) }
    }

    private suspend fun writeAndSave(
        app: AppKey,
        before: AppRestrictionReading,
        target: DeepRestrictionSnapshot?,
        pending: JournalEntry?,
    ): AppRestrictionResult {
        if (pending != null && before.firstInstallTime <= 0) return AppRestrictionResult.Failure(AppRestrictionFailure.SYSTEM_ERROR)
        val priorEntry = entry(app)
        if (pending != null && !save(app, pending)) {
            save(app, priorEntry)
            return AppRestrictionResult.Failure(AppRestrictionFailure.SYSTEM_ERROR)
        }
        val write: CallResult<AppRestrictionReading>
        val readBack: CallResult<AppRestrictionReading>
        if (target == before.snapshot) {
            write = CallResult.Success(before)
            readBack = write
        } else {
            write = control.setDeepRestricted(app, before, restricted = pending != null, target)
            readBack = readConnected(app)
        }
        if (readBack is CallResult.Failure) return if (write is CallResult.Failure) write.toResult() else readBack.toResult()
        val after = (readBack as CallResult.Success).value
        if (after.firstInstallTime != before.firstInstallTime) return AppRestrictionResult.Failure(AppRestrictionFailure.NOT_INSTALLED)

        if (pending != null) {
            // Read-back can confirm all or only part of a write whose reply was lost.
            val confirmedBaseline = after.snapshot.restoreMatching(pending.written, pending.baseline)
            val confirmed = pending.copy(baseline = confirmedBaseline, written = after.snapshot)
                .takeUnless { after.snapshot == pending.baseline }
            if (!save(app, confirmed)) {
                save(app, pending)
                return AppRestrictionResult.Failure(AppRestrictionFailure.SYSTEM_ERROR)
            }
        }
        if (write is CallResult.Failure) return write.toResult()
        if (target != before.snapshot && (!(write as CallResult.Success).value.setting.changeable || !after.setting.changeable)) {
            return AppRestrictionResult.Failure(AppRestrictionFailure.NOT_CHANGEABLE)
        }
        if (target != null && after.snapshot != target) return AppRestrictionResult.Failure(AppRestrictionFailure.SYSTEM_ERROR)
        if (pending == null && priorEntry != null && !save(app, null)) {
            save(app, priorEntry)
            return AppRestrictionResult.Failure(AppRestrictionFailure.SYSTEM_ERROR)
        }
        return AppRestrictionResult.Success(after.setting)
    }

    private suspend fun readForWrite(app: AppKey): CallResult<AppRestrictionReading> {
        if (channel.connect() !is ChannelState.Ready) {
            unavailable(app)
            return CallResult.Failure(CallFailure.ChannelUnavailable)
        }
        return readConnected(app)
    }

    private suspend fun readConnected(app: AppKey): CallResult<AppRestrictionReading> = control.read(app).also { result ->
        publish(app, (result as? CallResult.Success)?.value?.setting)
    }

    private fun publish(app: AppKey, value: DeepBackgroundRestriction?) {
        settings.value = settings.value + (app to value)
    }

    private fun unavailable(app: AppKey): AppRestrictionResult.Failure {
        publish(app, null)
        return AppRestrictionResult.Failure(AppRestrictionFailure.NO_ACCESS)
    }

    private fun entry(app: AppKey): JournalEntry? {
        val parts = journal.getString(app.journalKey, null)?.split('/') ?: return null
        if (parts.size != 10) return null
        val token = parts[0].toLongOrNull()?.takeIf { it > 0 } ?: return null
        // Earlier entries stored a separate restoration baseline in the last three slots.
        return JournalEntry(token, parts.snapshotAt(7) ?: return null, parts.snapshotAt(4) ?: return null)
    }

    private fun save(app: AppKey, entry: JournalEntry?): Boolean {
        val value = entry?.let { "${it.installTime}/${it.baseline.encode()}/${it.written.encode()}/${it.baseline.encode()}" }
        return journal.edit().putString(app.journalKey, value).commit().also { saved ->
            if (!saved) Timber.w("Cannot save the deep background restriction journal for %s", app)
        }
    }
}

private data class JournalEntry(
    val installTime: Long,
    val baseline: DeepRestrictionSnapshot,
    val written: DeepRestrictionSnapshot,
)

private val AppKey.journalKey get() = "${userId.value}/$packageName"

private fun DeepRestrictionSnapshot.encode() = "${standbyBucket ?: "-"}/$runInBackgroundMode/${exactAlarmMode ?: "-"}"

private fun List<String>.snapshotAt(index: Int): DeepRestrictionSnapshot? {
    val bucket = if (this[index] == "-") null else this[index].toIntOrNull() ?: return null
    val background = this[index + 1].toIntOrNull() ?: return null
    val alarm = if (this[index + 2] == "-") null else this[index + 2].toIntOrNull() ?: return null
    return DeepRestrictionSnapshot(bucket, background, alarm)
}

private val AppRestrictionReading.restrictedStandbyBucket get() = if (setting.exactAlarmSupported) 45 else 40

private fun AppRestrictionReading.manualClearTarget(entry: JournalEntry): DeepRestrictionSnapshot {
    val restored = snapshot.restoreMatching(entry.written, entry.baseline)
    return restored.copy(
        standbyBucket = restored.standbyBucket?.let { if (it == restrictedStandbyBucket) ACTIVE_STANDBY_BUCKET else it },
        runInBackgroundMode = restored.runInBackgroundMode.takeUnless { it == MODE_IGNORED } ?: MODE_ALLOWED,
        exactAlarmMode = restored.exactAlarmMode?.let { if (it == MODE_IGNORED) MODE_DEFAULT else it },
    )
}

private fun CallResult<AppRestrictionReading>.toResult() = when (this) {
    is CallResult.Success -> AppRestrictionResult.Success(value.setting)
    is CallResult.Failure -> AppRestrictionResult.Failure(reason.toRestrictionFailure())
}

private fun CallFailure.toRestrictionFailure() = when (this) {
    CallFailure.ChannelUnavailable, is CallFailure.NotPermitted -> AppRestrictionFailure.NO_ACCESS
    is CallFailure.TargetNotFound -> AppRestrictionFailure.NOT_INSTALLED
    is CallFailure.SystemError -> AppRestrictionFailure.SYSTEM_ERROR
}

private const val ACTIVE_STANDBY_BUCKET = 10
