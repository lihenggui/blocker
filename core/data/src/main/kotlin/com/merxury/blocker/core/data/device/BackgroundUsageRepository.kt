// Copyright 2026 Blocker
// SPDX-License-Identifier: Apache-2.0

package com.merxury.blocker.core.data.device

import com.merxury.blocker.core.appcontrol.BackgroundUsageControl
import com.merxury.blocker.core.model.component.AppKey
import com.merxury.blocker.core.model.privileged.CallFailure
import com.merxury.blocker.core.model.privileged.CallResult
import com.merxury.blocker.core.model.privileged.ChannelState
import com.merxury.blocker.core.model.system.BackgroundUsage
import com.merxury.blocker.core.privileged.PrivilegedChannel
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/** Reads and changes Android's background-usage setting for installed apps. */
interface BackgroundUsageRepository {
    /** Reads the current state without prompting for privileged access. */
    suspend fun read(app: AppKey): BackgroundUsageResult

    /**
     * May prompt for privileged access. Returns the system state read after applying the change.
     * Removing a power allowlist exemption applies to the package across all Android users.
     */
    suspend fun setAllowed(app: AppKey, allowed: Boolean): BackgroundUsageResult
}

sealed interface BackgroundUsageResult {
    data class Success(val value: BackgroundUsage) : BackgroundUsageResult

    data class Failure(val reason: BackgroundUsageFailure) : BackgroundUsageResult
}

enum class BackgroundUsageFailure {
    NO_ACCESS,
    NOT_INSTALLED,
    UNSUPPORTED,
    NOT_CHANGEABLE,
    SYSTEM_ERROR,
}

@Singleton
internal class LocalBackgroundUsageRepository @Inject constructor(
    private val channel: PrivilegedChannel,
    private val control: BackgroundUsageControl,
) : BackgroundUsageRepository {
    // A write spans two system services. Keep it and its read-back together for local writers.
    private val writes = Mutex()

    override suspend fun read(app: AppKey): BackgroundUsageResult {
        if (!control.isSupported) return BackgroundUsageResult.Failure(BackgroundUsageFailure.UNSUPPORTED)
        if (channel.connectIfGranted() !is ChannelState.Ready) {
            return BackgroundUsageResult.Failure(BackgroundUsageFailure.NO_ACCESS)
        }
        return readConnected(app)
    }

    override suspend fun setAllowed(app: AppKey, allowed: Boolean): BackgroundUsageResult = writes.withLock {
        if (!control.isSupported) return BackgroundUsageResult.Failure(BackgroundUsageFailure.UNSUPPORTED)
        if (channel.connect() !is ChannelState.Ready) {
            return BackgroundUsageResult.Failure(BackgroundUsageFailure.NO_ACCESS)
        }
        when (val write = control.setAllowed(app, allowed)) {
            is CallResult.Failure -> write.reason.toBackgroundUsageFailure(app)

            is CallResult.Success -> if (write.value) {
                readConnected(app)
            } else {
                BackgroundUsageResult.Failure(BackgroundUsageFailure.NOT_CHANGEABLE)
            }
        }
    }

    private suspend fun readConnected(app: AppKey): BackgroundUsageResult = when (val read = control.read(app)) {
        is CallResult.Success -> BackgroundUsageResult.Success(read.value)
        is CallResult.Failure -> read.reason.toBackgroundUsageFailure(app)
    }
}

private fun CallFailure.toBackgroundUsageFailure(app: AppKey): BackgroundUsageResult.Failure {
    val reason = when (this) {
        CallFailure.ChannelUnavailable -> BackgroundUsageFailure.NO_ACCESS

        is CallFailure.NotPermitted -> {
            Timber.w("Background usage access refused for %s: %s", app, message)
            BackgroundUsageFailure.NO_ACCESS
        }

        is CallFailure.TargetNotFound -> BackgroundUsageFailure.NOT_INSTALLED

        is CallFailure.SystemError -> BackgroundUsageFailure.SYSTEM_ERROR
    }
    return BackgroundUsageResult.Failure(reason)
}
