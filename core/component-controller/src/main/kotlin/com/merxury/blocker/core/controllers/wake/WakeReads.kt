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
 * Once a read finds the channel or its process unavailable, every later read of the same
 * collection fails at once without a call: a Shizuku user service that does not attach would
 * otherwise be waited for, and stopped, once per source.
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

    /** How many reads returned output. */
    var read = 0
        private set

    // How many reads the channel refused as not permitted, and why the first read failed.
    private var refused = 0
    private var firstFailure: String? = null

    /**
     * Whether the channel refused every read it was asked for, as Shizuku before 13 does, which
     * cannot run calls in its own process: the collection read nothing and never will this way.
     */
    val everyReadRefused: Boolean get() = read == 0 && refused > 0 && !channelUnavailable

    /**
     * What the collection as a whole could read, in words for the authorization status, or null
     * when every read reached the channel.
     */
    fun accessGap(): String? = when {
        read == 0 && firstFailure != null -> "nothing read: $firstFailure"
        channelUnavailable -> "privileged channel lost after $read reads"
        else -> null
    }

    /**
     * The stdout of [command] for [profile].
     *
     * @param boot the boot count of the statsd config the command names
     * @throws WakeSourceUnavailableException when it cannot be read, with the status to record
     */
    suspend fun bytes(command: WakeCommand, profile: Int = 0, boot: Int = generation, argument: Long = 0): ByteArray {
        if (channelUnavailable) throw WakeSourceUnavailableException(CHANNEL_UNAVAILABLE)
        val call = try {
            ReadWakeSource(command, UserId(profile), Process.myUid(), boot, argument, owner)
        } catch (e: IllegalArgumentException) {
            throw WakeSourceUnavailableException(e.message ?: "${command.name} arguments rejected", e)
        }
        return when (val result = executor.executeIfReady(call)) {
            is CallResult.Success -> {
                read++
                result.value
            }

            is CallResult.Failure -> {
                when (result.reason) {
                    CallFailure.ChannelUnavailable -> channelUnavailable = true
                    is CallFailure.NotPermitted -> refused++
                    else -> Unit
                }
                val status = result.reason.status()
                if (firstFailure == null) firstFailure = status
                throw WakeSourceUnavailableException(status)
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

internal const val CHANNEL_UNAVAILABLE = "privileged channel unavailable"

// What CallFailure carries for an exception without its own mapping: its class, then its message.
private val exceptionPrefix = Regex("""^[\w$.]+(Exception|Error): """)

/** The status a source records when a read failed this way, in words. */
private fun CallFailure.status(): String = when (this) {
    CallFailure.ChannelUnavailable -> CHANNEL_UNAVAILABLE
    is CallFailure.NotPermitted -> "not permitted: ${message ?: "refused"}"
    is CallFailure.TargetNotFound -> "not found: ${message ?: "unknown target"}"
    is CallFailure.SystemError -> message?.replace(exceptionPrefix, "")?.ifBlank { null } ?: "failed"
}
