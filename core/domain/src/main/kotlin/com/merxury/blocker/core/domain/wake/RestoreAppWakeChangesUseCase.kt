// Copyright 2026 Blocker
// SPDX-License-Identifier: Apache-2.0

package com.merxury.blocker.core.domain.wake

import com.merxury.blocker.core.data.device.AppRestrictionFailure
import com.merxury.blocker.core.data.device.AppRestrictionRepository
import com.merxury.blocker.core.data.device.AppRestrictionRestoreResult
import com.merxury.blocker.core.data.device.AppRestrictionResult
import com.merxury.blocker.core.data.device.BackgroundUsageFailure
import com.merxury.blocker.core.data.device.BackgroundUsageRepository
import com.merxury.blocker.core.data.device.BackgroundUsageResult
import com.merxury.blocker.core.data.wake.AutomaticWakeBlockRepository
import com.merxury.blocker.core.data.wake.WakeBlockRepository
import com.merxury.blocker.core.model.component.AppKey
import com.merxury.blocker.core.model.component.UserId
import com.merxury.blocker.core.model.wake.WakeBlock
import com.merxury.blocker.core.model.wake.WakeBlockFailure
import com.merxury.blocker.core.model.wake.WakeBlockOutcome
import com.merxury.blocker.core.model.wake.WakePath
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import javax.inject.Inject

class RestoreAppWakeChangesUseCase @Inject constructor(
    private val activity: ObserveAppWakeActivityUseCase,
    private val user: WakeBlockRepository,
    private val automatic: AutomaticWakeBlockRepository,
    private val backgroundUsage: BackgroundUsageRepository,
    private val appRestriction: AppRestrictionRepository,
) {
    data class Result(val outcome: WakeBlockOutcome, val undo: Undo?)

    /** The changes completed by one restore, including a partially successful restore. */
    class Undo internal constructor(
        internal val app: AppKey,
        internal val blocks: List<WakeBlock>,
        internal val automaticIds: Set<String>,
        internal val restoredBackgroundUsage: Boolean,
        internal val deepRestrictionRestore: AppRestrictionRestoreResult.Restored?,
    )

    /** Restores wake changes and returns the undo token once the writes have started. */
    suspend operator fun invoke(packageName: String): Result {
        val app = AppKey(packageName, UserId.SYSTEM)
        val outbound = activity(packageName).first().outboundPaths().map(WakePath::pathKey).toSet()
        currentCoroutineContext().ensureActive()
        return withContext(NonCancellable) {
            val (manual, blocks) = user.removeAppBlocks(packageName, outbound)
            val reviewed = automatic.allowForApp(packageName)
            val background = when (val setting = backgroundUsage.read(app)) {
                is BackgroundUsageResult.Success -> if (setting.value.allowed) emptyOutcome else setBackgroundUsage(app, allowed = true)

                is BackgroundUsageResult.Failure -> if (setting.reason == BackgroundUsageFailure.UNSUPPORTED) {
                    emptyOutcome
                } else {
                    WakeBlockOutcome(emptySet(), setting.reason.asBlockFailure())
                }
            }
            val restore = appRestriction.restoreOwned(app)
            val deep = when (restore) {
                AppRestrictionRestoreResult.NotOwned -> emptyOutcome
                is AppRestrictionRestoreResult.Restored -> deepOutcome(app)
                is AppRestrictionRestoreResult.Failure -> WakeBlockOutcome(emptySet(), restore.reason.asBlockFailure())
            }
            val restoredBackground = background.done.isNotEmpty()
            val restoredDeep = restore as? AppRestrictionRestoreResult.Restored
            val undo = if (blocks.isNotEmpty() || reviewed.done.isNotEmpty() || restoredBackground || restoredDeep != null) {
                Undo(app, blocks, reviewed.done, restoredBackground, restoredDeep)
            } else {
                null
            }
            Result(manual + reviewed + background + deep, undo)
        }
    }

    /** Reapplies the recorded changes to completion once the writes have started. */
    suspend fun undo(changes: Undo): WakeBlockOutcome {
        currentCoroutineContext().ensureActive()
        return withContext(NonCancellable) {
            val manual = user.restoreBlocks(changes.blocks)
            val reviewed = automatic.setBlocked(changes.automaticIds, blocked = true).outcome
            val background = if (changes.restoredBackgroundUsage) setBackgroundUsage(changes.app, allowed = false) else emptyOutcome
            val deep = changes.deepRestrictionRestore?.let { restore ->
                when (val result = appRestriction.reapplyOwned(changes.app, restore.reapply, restore.baseline, restore.firstInstallTime)) {
                    is AppRestrictionResult.Success -> deepOutcome(changes.app)
                    is AppRestrictionResult.Failure -> WakeBlockOutcome(emptySet(), result.reason.asBlockFailure())
                }
            } ?: emptyOutcome
            manual + reviewed + background + deep
        }
    }

    private suspend fun setBackgroundUsage(app: AppKey, allowed: Boolean): WakeBlockOutcome {
        val key = "background:${app.packageName}"
        return when (val result = backgroundUsage.setAllowed(app, allowed)) {
            is BackgroundUsageResult.Success -> if (result.value.allowed == allowed) {
                WakeBlockOutcome(setOf(key), null)
            } else {
                WakeBlockOutcome(emptySet(), WakeBlockFailure.SystemError("Background usage did not change"))
            }

            is BackgroundUsageResult.Failure -> WakeBlockOutcome(emptySet(), result.reason.asBlockFailure())
        }
    }

    private fun deepOutcome(app: AppKey) = WakeBlockOutcome(setOf("deep-background:${app.packageName}"), null)
}

private val emptyOutcome = WakeBlockOutcome(emptySet(), null)

private fun BackgroundUsageFailure.asBlockFailure(): WakeBlockFailure = when (this) {
    BackgroundUsageFailure.NO_ACCESS -> WakeBlockFailure.NoAccess
    BackgroundUsageFailure.NOT_CHANGEABLE -> WakeBlockFailure.BackgroundNotChangeable
    else -> WakeBlockFailure.SystemError(name)
}

private fun AppRestrictionFailure.asBlockFailure(): WakeBlockFailure = when (this) {
    AppRestrictionFailure.NO_ACCESS -> WakeBlockFailure.NoAccess
    AppRestrictionFailure.NOT_CHANGEABLE -> WakeBlockFailure.BackgroundNotChangeable
    else -> WakeBlockFailure.SystemError(name)
}
