// Copyright 2026 Blocker
// SPDX-License-Identifier: Apache-2.0

package com.merxury.blocker.core.controllers.wake

import android.os.Process
import com.merxury.blocker.core.model.component.UserId
import com.merxury.blocker.core.model.privileged.CallFailure
import com.merxury.blocker.core.model.privileged.CallResult
import com.merxury.blocker.core.privileged.PrivilegedExecutor
import com.merxury.blocker.core.privileged.calls.ReadWakeSource
import java.io.IOException

/**
 * A wake source could not be read; the message is the status its source records. The run goes on
 * with the other sources.
 */
internal class WakeSourceUnavailableException(message: String, cause: Throwable? = null) : IOException(message, cause)

/**
 * Reads wake sources through the privileged channel, only while it is ready: never with a prompt,
 * so background collection never shows one. Every read is one call in the channel's privileged
 * process, the root process or the Shizuku user service, so a collection starts no process of its
 * own beyond the tools it runs.
 *
 * @param owner the uid the privileged process runs as, which owns the statsd config it installs
 * @param generation the boot count, which names this boot's statsd config
 */
internal class WakeReads(
    private val executor: PrivilegedExecutor,
    val owner: Int,
    val generation: Int,
) {
    /**
     * Whether a read found the channel unavailable: gone, or its privileged process out of reach.
     * Other failures say nothing about the channel.
     */
    var channelUnavailable = false
        private set

    /**
     * The stdout of [command] for [profile].
     *
     * @param boot the boot count of the statsd config the command names
     * @throws WakeSourceUnavailableException when it cannot be read, with the status to record
     */
    suspend fun bytes(command: WakeCommand, profile: Int = 0, boot: Int = generation, argument: Long = 0): ByteArray {
        val call = try {
            ReadWakeSource(command, UserId(profile), Process.myUid(), boot, argument)
        } catch (e: IllegalArgumentException) {
            throw WakeSourceUnavailableException(e.message ?: "${command.name} arguments rejected", e)
        }
        return when (val result = executor.executeIfReady(call)) {
            is CallResult.Success -> result.value

            is CallResult.Failure -> {
                if (result.reason == CallFailure.ChannelUnavailable) channelUnavailable = true
                throw WakeSourceUnavailableException(result.reason.status())
            }
        }
    }

    /**
     * [bytes] as text, refused when the service answered with a refusal or a timeout instead of
     * its dump.
     */
    suspend fun text(command: WakeCommand, profile: Int = 0, argument: Long = 0): String {
        val output = bytes(command, profile, argument = argument).decodeToString()
        if (output.startsWith("Permission Denial") || output.startsWith("Can't find service") || output.contains("DUMP TIMEOUT")) {
            throw WakeSourceUnavailableException("Service unavailable")
        }
        return output
    }
}

/** The status a source records when a read failed this way. */
private fun CallFailure.status(): String = when (this) {
    CallFailure.ChannelUnavailable -> "privileged channel unavailable"
    is CallFailure.NotPermitted -> "not permitted: ${message ?: "refused"}"
    is CallFailure.TargetNotFound -> "not found: ${message ?: "unknown target"}"
    is CallFailure.SystemError -> message ?: "unavailable"
}
