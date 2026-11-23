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

import android.os.Parcel
import android.os.RemoteException
import be.mygod.librootkotlinx.NoShellException
import com.merxury.blocker.core.dispatchers.BlockerDispatchers.IO
import com.merxury.blocker.core.dispatchers.Dispatcher
import com.merxury.blocker.core.model.privileged.CallResult
import com.merxury.blocker.core.model.privileged.ChannelState
import com.merxury.blocker.core.model.privileged.PrivilegedIdentity
import com.merxury.blocker.core.model.privileged.UnavailableReason
import com.merxury.blocker.core.privileged.AbstractPrivilegedTransport
import com.merxury.blocker.core.privileged.ChannelLostException
import com.merxury.blocker.core.privileged.PrivilegedCall
import com.merxury.blocker.core.privileged.calls.CallerUid
import com.merxury.blocker.core.privileged.identityOfUid
import com.merxury.blocker.core.privileged.lostReason
import com.merxury.blocker.core.root.BlockerRootSession
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Runs calls in a root process started through su with librootkotlinx. Connecting starts that
 * process, which shows the su prompt when root has not been granted yet.
 */
@Singleton
internal class RootTransport @Inject constructor(
    private val rootSession: BlockerRootSession,
    @Dispatcher(IO) private val ioDispatcher: CoroutineDispatcher,
) : AbstractPrivilegedTransport() {

    override suspend fun acquire(): ChannelState = try {
        when (val result = send(CallerUid)) {
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

    override suspend fun <R> send(call: PrivilegedCall<R>): CallResult<R> {
        val command = RootCallCommand(call)
        val size = parceledSize(command)
        check(size <= MAX_REQUEST_BYTES) { "$call needs $size bytes, more than one root call carries" }
        val reply = try {
            rootSession.use { server -> server.execute(command, RootCallCommand::class.java.classLoader) }
        } catch (e: NoShellException) {
            throw ChannelLostException(UnavailableReason.DENIED, "No root shell", e)
        } catch (e: RemoteException) {
            throw ChannelLostException(UnavailableReason.FAILED, "The root process is gone", e)
        } catch (e: CancellationException) {
            // librootkotlinx also cancels a call when its root process closes.
            currentCoroutineContext().ensureActive()
            throw ChannelLostException(UnavailableReason.FAILED, "The root process closed", e)
        }
        val bytes = withContext(ioDispatcher) { reply.read() }
        return decodeOutcome(call, bytes)
    }

    private fun parceledSize(command: RootCallCommand): Int {
        val parcel = Parcel.obtain()
        try {
            command.writeToParcel(parcel, 0)
            return parcel.dataSize()
        } finally {
            parcel.recycle()
        }
    }

    private companion object {
        // librootkotlinx sends a request in a one-way binder call too, and a request that does not
        // fit closes the whole root session.
        const val MAX_REQUEST_BYTES = 128 * 1024
    }
}
