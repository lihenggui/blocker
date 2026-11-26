// Copyright 2026 Blocker
// SPDX-License-Identifier: Apache-2.0

package com.merxury.blocker.core.testing.repository.device

import android.app.AppOpsManager.MODE_ALLOWED
import android.app.AppOpsManager.MODE_DEFAULT
import android.app.AppOpsManager.MODE_IGNORED
import com.merxury.blocker.core.data.device.AppRestrictionFailure
import com.merxury.blocker.core.data.device.AppRestrictionRepository
import com.merxury.blocker.core.data.device.AppRestrictionRestoreResult
import com.merxury.blocker.core.data.device.AppRestrictionResult
import com.merxury.blocker.core.model.component.AppKey
import com.merxury.blocker.core.model.system.DeepBackgroundRestriction
import com.merxury.blocker.core.model.system.DeepRestrictionSnapshot
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FakeAppRestrictionRepository @Inject constructor() : AppRestrictionRepository {
    private val unrestricted = DeepBackgroundRestriction(false, false, false, changeable = true)
    private val settings = MutableStateFlow<Map<AppKey, DeepBackgroundRestriction>>(emptyMap())
    private val snapshots = mutableMapOf<AppKey, DeepRestrictionSnapshot>()
    private val owned = mutableMapOf<AppKey, JournalEntry>()
    val writes = mutableListOf<Pair<AppKey, Boolean>>()
    var writeFailure: AppRestrictionFailure? = null

    fun sendRestriction(app: AppKey, restriction: DeepBackgroundRestriction) {
        snapshots[app] = restriction.snapshot()
        settings.update { it + (app to restriction) }
    }

    fun sendSnapshot(app: AppKey, snapshot: DeepRestrictionSnapshot) {
        val prior = settings.value[app] ?: unrestricted
        sendRestriction(
            app,
            prior.copy(
                restrictedBucket = snapshot.standbyBucket == prior.restrictedBucketValue,
                runInBackgroundIgnored = snapshot.runInBackgroundMode == MODE_IGNORED,
                exactAlarmIgnored = snapshot.exactAlarmMode == MODE_IGNORED,
                bucketSupported = snapshot.standbyBucket != null,
                exactAlarmSupported = snapshot.exactAlarmMode != null,
            ),
        )
        snapshots[app] = snapshot
    }

    fun snapshot(app: AppKey): DeepRestrictionSnapshot = snapshots[app] ?: unrestricted.snapshot()

    override fun observe(app: AppKey) = settings.map { it[app] ?: unrestricted }.distinctUntilChanged()

    override suspend fun read(app: AppKey) = AppRestrictionResult.Success(settings.value[app] ?: unrestricted)

    override suspend fun setDeepRestricted(app: AppKey, restricted: Boolean): AppRestrictionResult {
        writes += app to restricted
        writeFailure?.let { return AppRestrictionResult.Failure(it) }
        val setting = read(app).value
        if (!setting.changeable) return AppRestrictionResult.Failure(AppRestrictionFailure.NOT_CHANGEABLE)
        val before = snapshot(app)
        val journal = owned[app]
        val after = when {
            restricted -> setting.copy(restrictedBucket = true, runInBackgroundIgnored = true, exactAlarmIgnored = true).snapshot()

            journal == null -> setting.copy(restrictedBucket = false, runInBackgroundIgnored = false, exactAlarmIgnored = false).snapshot()

            else -> before.restoreMatching(journal.written, journal.baseline).let {
                DeepRestrictionSnapshot(
                    it.standbyBucket?.let { bucket -> if (bucket == setting.restrictedBucketValue) 10 else bucket },
                    it.runInBackgroundMode.takeUnless { mode -> mode == MODE_IGNORED } ?: MODE_ALLOWED,
                    it.exactAlarmMode?.let { mode -> if (mode == MODE_IGNORED) MODE_DEFAULT else mode },
                )
            }
        }
        sendSnapshot(app, after)
        if (restricted) {
            val baseline = journal?.let { before.restoreMatching(it.written, it.baseline) } ?: before
            if (baseline != after) {
                owned[app] = JournalEntry(baseline, after)
                return read(app)
            }
        }
        owned.remove(app)
        return read(app)
    }

    override suspend fun restoreOwned(app: AppKey): AppRestrictionRestoreResult {
        val (baseline, written) = owned[app] ?: return AppRestrictionRestoreResult.NotOwned
        writes += app to false
        writeFailure?.let { return AppRestrictionRestoreResult.Failure(it) }
        val before = snapshot(app)
        val target = before.restoreMatching(written, baseline)
        if (target != before && !read(app).value.changeable) return AppRestrictionRestoreResult.Failure(AppRestrictionFailure.NOT_CHANGEABLE)
        sendSnapshot(app, target)
        owned.remove(app)
        if (target == before) return AppRestrictionRestoreResult.NotOwned
        return AppRestrictionRestoreResult.Restored(before, target, firstInstallTime = 1L)
    }

    override suspend fun reapplyOwned(
        app: AppKey,
        target: DeepRestrictionSnapshot,
        baseline: DeepRestrictionSnapshot,
        firstInstallTime: Long?,
    ): AppRestrictionResult {
        writes += app to true
        writeFailure?.let { return AppRestrictionResult.Failure(it) }
        val before = snapshot(app)
        val desired = before.restoreMatching(baseline, target)
        if (desired == before) return read(app)
        if (!read(app).value.changeable) return AppRestrictionResult.Failure(AppRestrictionFailure.NOT_CHANGEABLE)
        sendSnapshot(app, desired)
        owned[app] = JournalEntry(before, desired)
        return read(app)
    }

    private data class JournalEntry(
        val baseline: DeepRestrictionSnapshot,
        val written: DeepRestrictionSnapshot,
    )

    private val DeepBackgroundRestriction.restrictedBucketValue get() = if (exactAlarmSupported) 45 else 40

    private fun DeepBackgroundRestriction.snapshot() = DeepRestrictionSnapshot(
        if (bucketSupported) if (restrictedBucket) restrictedBucketValue else 10 else null,
        if (runInBackgroundIgnored) MODE_IGNORED else MODE_ALLOWED,
        if (exactAlarmSupported) if (exactAlarmIgnored) MODE_IGNORED else MODE_DEFAULT else null,
    )
}
