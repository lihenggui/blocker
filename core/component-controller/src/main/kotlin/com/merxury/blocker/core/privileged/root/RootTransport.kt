/*
 * Copyright 2026 Blocker
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.merxury.blocker.core.privileged.root

import android.os.RemoteException
import be.mygod.librootkotlinx.NoShellException
import com.merxury.blocker.core.dispatchers.BlockerDispatchers.IO
import com.merxury.blocker.core.dispatchers.Dispatcher
import com.merxury.blocker.core.model.privileged.CallResult
import com.merxury.blocker.core.model.privileged.ChannelState
import com.merxury.blocker.core.model.privileged.PrivilegedIdentity
import com.merxury.blocker.core.model.privileged.UnavailableReason
import com.merxury.blocker.core.privileged.AbstractPrivilegedTransport
import com.merxury.blocker.core.privileged.CallBytes
import com.merxury.blocker.core.privileged.ChannelLostException
import com.merxury.blocker.core.privileged.PrivilegedCall
import com.merxury.blocker.core.privileged.PrivilegedProcessUnavailableException
import com.merxury.blocker.core.privileged.calls.CallerUid
import com.merxury.blocker.core.privileged.decodeOutcome
import com.merxury.blocker.core.privileged.identityOfUid
import com.merxury.blocker.core.privileged.lostReason
import com.merxury.blocker.core.privileged.marshallCall
import com.merxury.blocker.core.privileged.permissionPromptsAllowed
import com.merxury.blocker.core.root.BlockerRootSession
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/** Sends one [RootCallCommand] to the root process, starting that process when needed. */
internal fun interface RootCommandRunner {
    /**
     * @throws NoShellException when su refuses or is missing
     * @throws RemoteException when the root process is gone
     */
    suspend fun run(command: RootCallCommand): CallBytes
}

/**
 * Runs calls in a root process started through su with librootkotlinx. Connecting starts that
 * process, which shows the su prompt when root has not been granted yet.
 *
 * [grants] remembers across process starts whether su gave a root process: a successful connect
 * sets it and a refused su clears it. Quiet connects and calls start su only while it is set.
 *
 * A call that finds the root process gone is sent once more on a fresh process, which librootkotlinx
 * starts without a prompt while root stays granted. Only when that fails too is the channel lost.
 * A call that is not [PrivilegedCall.retryable] is not sent again: it fails as unavailable and
 * leaves the channel ready, since the next call starts a fresh process.
 */
@Singleton
internal class RootTransport(
    private val runner: RootCommandRunner,
    private val grants: RootGrantMemory,
    private val ioDispatcher: CoroutineDispatcher,
    private val hasActiveServer: () -> Boolean,
) : AbstractPrivilegedTransport() {

    @Inject constructor(
        rootSession: BlockerRootSession,
        grants: RootGrantMemory,
        @Dispatcher(IO) ioDispatcher: CoroutineDispatcher,
    ) : this(
        RootCommandRunner { command ->
            rootSession.use { server -> server.execute(command, RootCallCommand::class.java.classLoader) }
        },
        grants,
        ioDispatcher,
        { rootSession.hasActiveServer },
    )

    override suspend fun acquire(): ChannelState = startRootProcess().also {
        if (it is ChannelState.Ready) grants.remember(true)
    }

    override suspend fun acquireIfGranted(): ChannelState = if (grants.isRemembered()) {
        startRootProcess()
    } else {
        ChannelState.Unavailable(UnavailableReason.DENIED)
    }

    override suspend fun isConnectionCurrent(): Boolean = hasActiveServer() && grants.isRemembered()

    private suspend fun startRootProcess(): ChannelState = try {
        when (val result = sendOnce(CallerUid)) {
            is CallResult.Success -> identityOfUid(result.value)
                ?.takeIf { it == PrivilegedIdentity.ROOT }
                ?.let { ChannelState.Ready(it) }
                ?: ChannelState.Unavailable(UnavailableReason.FAILED)
                    .also { Timber.e("The root process runs as uid ${result.value}") }

            is CallResult.Failure -> ChannelState.Unavailable(UnavailableReason.FAILED)
                .also { Timber.e("Cannot ask the root process for its uid: ${result.reason}") }
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        val reason = e.lostReason() ?: UnavailableReason.FAILED
        Timber.w(e, "Root is not available")
        ChannelState.Unavailable(reason)
    }

    override suspend fun <R> send(call: PrivilegedCall<R>): CallResult<R> = try {
        sendOnce(call)
    } catch (e: ChannelLostException) {
        // A refused su would refuse again.
        if (e.reason == UnavailableReason.DENIED) throw e
        // It may already have reached the system; the caller decides what to do.
        if (!call.retryable) throw PrivilegedProcessUnavailableException("The root process went away while running $call", e)
        Timber.w(e, "The root process went away; sending $call again on a new one")
        sendOnce(call)
    }

    private suspend fun <R> sendOnce(call: PrivilegedCall<R>): CallResult<R> {
        // Check before taking the session lease, since use can start su even if a server
        // was active just before the call.
        if (!permissionPromptsAllowed() && !grants.isRemembered()) {
            throw ChannelLostException(UnavailableReason.DENIED, "No remembered root grant")
        }
        // A call too large for one root call goes through a pipe. Closing this side's copy when
        // the call ends, however it ends, stops the pipe's writer if the request never arrived.
        val request = CallBytes.request(marshallCall(call))
        val reply = try {
            runner.run(RootCallCommand(request))
        } catch (e: NoShellException) {
            // su refused or is gone, so starting it again may prompt.
            grants.remember(false)
            throw ChannelLostException(UnavailableReason.DENIED, "No root shell", e)
        } catch (e: RemoteException) {
            throw ChannelLostException(UnavailableReason.FAILED, "The root process is gone", e)
        } catch (e: CancellationException) {
            // librootkotlinx also cancels a call when its root process closes.
            currentCoroutineContext().ensureActive()
            throw ChannelLostException(UnavailableReason.FAILED, "The root process closed", e)
        } finally {
            request.close()
        }
        val bytes = withContext(ioDispatcher) { reply.read() }
        return decodeOutcome(call, bytes)
    }
}
