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
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/** Reads and changes Android's background-usage setting for installed apps. */
interface BackgroundUsageRepository {
    /** Observes the setting, refreshing quietly on subscription and when access becomes ready. Null means it is unavailable. */
    fun observe(app: AppKey): Flow<BackgroundUsage?>

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
    // Keep reads and publication ordered with writes, which span two system services.
    private val access = Mutex()
    private val settings = MutableStateFlow<Map<AppKey, BackgroundUsage?>>(emptyMap())

    override fun observe(app: AppKey): Flow<BackgroundUsage?> = channelFlow {
        send(settings.value[app])
        launch(start = CoroutineStart.UNDISPATCHED) {
            this@LocalBackgroundUsageRepository.channel.state.map { it is ChannelState.Ready }.distinctUntilChanged().drop(1).filter { it }.collect {
                read(app)
            }
        }
        launch { read(app) }
        settings.map { it[app] }.collect { send(it) }
    }.distinctUntilChanged()

    override suspend fun read(app: AppKey): BackgroundUsageResult = access.withLock {
        readQuietly(app).also { publish(app, it) }
    }

    override suspend fun setAllowed(app: AppKey, allowed: Boolean): BackgroundUsageResult = access.withLock {
        writeAndReadBack(app, allowed).also { result ->
            if (result != BackgroundUsageResult.Failure(BackgroundUsageFailure.NOT_CHANGEABLE)) publish(app, result)
        }
    }

    private fun publish(app: AppKey, result: BackgroundUsageResult) {
        val usage = (result as? BackgroundUsageResult.Success)?.value
        settings.update { it + (app to usage) }
    }

    private suspend fun readQuietly(app: AppKey): BackgroundUsageResult {
        if (!control.isSupported) return BackgroundUsageResult.Failure(BackgroundUsageFailure.UNSUPPORTED)
        if (channel.connectIfGranted() !is ChannelState.Ready) {
            return BackgroundUsageResult.Failure(BackgroundUsageFailure.NO_ACCESS)
        }
        return readConnected(app)
    }

    private suspend fun writeAndReadBack(app: AppKey, allowed: Boolean): BackgroundUsageResult {
        if (!control.isSupported) return BackgroundUsageResult.Failure(BackgroundUsageFailure.UNSUPPORTED)
        if (channel.connect() !is ChannelState.Ready) return BackgroundUsageResult.Failure(BackgroundUsageFailure.NO_ACCESS)
        return when (val write = control.setAllowed(app, allowed)) {
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
