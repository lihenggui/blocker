// Copyright 2026 Blocker
// SPDX-License-Identifier: Apache-2.0

package com.merxury.blocker.core.appcontrol

import com.merxury.blocker.core.model.component.AppKey
import com.merxury.blocker.core.model.privileged.CallResult
import com.merxury.blocker.core.model.system.DeepBackgroundRestriction
import com.merxury.blocker.core.model.system.DeepRestrictionSnapshot
import com.merxury.blocker.core.privileged.PrivilegedExecutor
import com.merxury.blocker.core.privileged.calls.ReadDeepBackgroundRestriction
import com.merxury.blocker.core.privileged.calls.SetDeepBackgroundRestricted
import javax.inject.Inject

data class AppRestrictionReading(
    val setting: DeepBackgroundRestriction,
    val snapshot: DeepRestrictionSnapshot,
    val firstInstallTime: Long,
)

/** Deep background restriction operations through a connected privileged channel. */
interface AppRestrictionControl {
    suspend fun read(app: AppKey): CallResult<AppRestrictionReading>

    /** Applies [target], or the standard restriction/defaults, provided [before] still matches the system. */
    suspend fun setDeepRestricted(
        app: AppKey,
        before: AppRestrictionReading,
        restricted: Boolean,
        target: DeepRestrictionSnapshot? = null,
    ): CallResult<AppRestrictionReading>
}

internal class PrivilegedAppRestrictionControl @Inject constructor(
    private val executor: PrivilegedExecutor,
) : AppRestrictionControl {
    override suspend fun read(app: AppKey): CallResult<AppRestrictionReading> = executor.executeIfReady(ReadDeepBackgroundRestriction(app.packageName, app.userId)).map { it.toReading() }

    override suspend fun setDeepRestricted(
        app: AppKey,
        before: AppRestrictionReading,
        restricted: Boolean,
        target: DeepRestrictionSnapshot?,
    ): CallResult<AppRestrictionReading> = executor.executeIfReady(
        SetDeepBackgroundRestricted(app.packageName, app.userId, restricted, target, before.snapshot, before.firstInstallTime),
    ).map { it.toReading() }
}
