// Copyright 2026 Blocker
// SPDX-License-Identifier: Apache-2.0

package com.merxury.blocker.core.appcontrol

import com.merxury.blocker.core.model.component.AppKey
import com.merxury.blocker.core.model.privileged.CallResult
import com.merxury.blocker.core.model.system.BackgroundUsage
import com.merxury.blocker.core.privileged.PrivilegedExecutor
import com.merxury.blocker.core.privileged.calls.ReadBackgroundUsage
import com.merxury.blocker.core.privileged.calls.SetBackgroundUsageAllowed
import com.merxury.blocker.core.privileged.calls.backgroundUsageSupported
import javax.inject.Inject

/** Background-usage operations on a connected privileged channel. Neither call prompts. */
interface BackgroundUsageControl {
    val isSupported: Boolean

    suspend fun read(app: AppKey): CallResult<BackgroundUsage>

    /** Returns false if the system power allowlist protects the app from changes. */
    suspend fun setAllowed(app: AppKey, allowed: Boolean): CallResult<Boolean>
}

internal class PrivilegedBackgroundUsageControl @Inject constructor(
    private val executor: PrivilegedExecutor,
) : BackgroundUsageControl {
    override val isSupported: Boolean get() = backgroundUsageSupported

    override suspend fun read(app: AppKey): CallResult<BackgroundUsage> = executor.executeIfReady(ReadBackgroundUsage(app.packageName, app.userId)).map { it.toBackgroundUsage() }

    override suspend fun setAllowed(app: AppKey, allowed: Boolean): CallResult<Boolean> = executor.executeIfReady(SetBackgroundUsageAllowed(app.packageName, app.userId, allowed))
}
