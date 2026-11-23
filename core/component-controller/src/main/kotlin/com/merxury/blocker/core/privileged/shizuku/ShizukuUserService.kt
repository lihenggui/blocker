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

package com.merxury.blocker.core.privileged.shizuku

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.os.DeadObjectException
import android.os.IBinder
import com.merxury.blocker.core.model.privileged.CallFailure
import com.merxury.blocker.core.model.privileged.CallResult
import com.merxury.blocker.core.privileged.CallBytes
import com.merxury.blocker.core.privileged.PrivilegedCall
import com.merxury.blocker.core.privileged.PrivilegedProcessUnavailableException
import com.merxury.blocker.core.privileged.decodeOutcome
import com.merxury.blocker.core.privileged.marshallCall
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import rikka.shizuku.Shizuku
import timber.log.Timber
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

// The first Shizuku that passes a Context to a user service, which the caller check needs.
private const val MIN_SHIZUKU_VERSION = 13

// Starting the service is a new app_process, which takes a few seconds on a slow device.
private val ATTACH_TIMEOUT = 15.seconds

// Raise when the AIDL interface changes, so Shizuku replaces a service an older app started.
private const val SERVICE_VERSION = 1

/** The [Shizuku] calls that start and stop [ShizukuPrivilegedService], behind an interface for tests. */
internal interface UserServiceConnector {
    /** The version of the running Shizuku server. */
    fun serverVersion(): Int

    /** Starts the service if it is not running and delivers its binder to [connection]. */
    fun bind(connection: ServiceConnection)

    /** Stops delivering to [connection]; with [stop], also ends the service process. */
    fun unbind(connection: ServiceConnection, stop: Boolean)
}

internal class ShizukuUserServiceConnector @Inject constructor(
    @ApplicationContext context: Context,
) : UserServiceConnector {
    private val args = Shizuku.UserServiceArgs(ComponentName(context, ShizukuPrivilegedService::class.java))
        .daemon(false)
        .processNameSuffix("privileged")
        .tag("privileged")
        .version(SERVICE_VERSION)

    override fun serverVersion(): Int = Shizuku.getVersion()

    override fun bind(connection: ServiceConnection) {
        Shizuku.bindUserService(args, connection)
    }

    override fun unbind(connection: ServiceConnection, stop: Boolean) {
        // Removing alone leaves the callback registered until Shizuku notices the death.
        Shizuku.unbindUserService(args, connection, false)
        if (stop) Shizuku.unbindUserService(args, connection, true)
    }
}

/**
 * Runs calls in [ShizukuPrivilegedService]. Binds it on first use, keeps it for later calls and
 * binds again on the next call after it died or Shizuku stopped. Never binds more than once at a
 * time.
 */
@Singleton
internal class ShizukuUserService(
    private val connector: UserServiceConnector,
    private val attachTimeout: Duration,
) {
    @Inject constructor(connector: ShizukuUserServiceConnector) : this(connector, ATTACH_TIMEOUT)

    private class Attached(val service: IPrivilegedService, val connection: ServiceConnection)

    private val attached = AtomicReference<Attached?>()
    private val bindLock = Mutex()

    /**
     * Runs [call] in the service and returns its outcome. Blocks while the call runs, so call it
     * off the main thread. Fails with [CallFailure.NotPermitted] on a Shizuku too old to say who
     * may use the service, without starting it.
     *
     * @throws PrivilegedProcessUnavailableException when the service does not attach in time or
     * dies while running [call]
     */
    suspend fun <R> run(call: PrivilegedCall<R>): CallResult<R> {
        val version = connector.serverVersion()
        if (version < MIN_SHIZUKU_VERSION) {
            return CallResult.Failure(
                CallFailure.NotPermitted("Shizuku $version cannot run $call; $MIN_SHIZUKU_VERSION or later can"),
            )
        }
        val current = attach()
        // Closing this side's copy of a piped request when the call ends stops its writer even
        // when the service never read it.
        val reply = CallBytes.request(marshallCall(call)).use { request ->
            try {
                current.service.run(request)
            } catch (e: DeadObjectException) {
                forget(current)
                throw PrivilegedProcessUnavailableException("The Shizuku user service died while running $call", e)
            }
        }
        return decodeOutcome(call, reply.read())
    }

    /** Drops the bound service, for example because Shizuku stopped. The next call binds again. */
    fun reset() {
        attached.get()?.let(::forget)
    }

    private suspend fun attach(): Attached {
        alive()?.let { return it }
        return bindLock.withLock {
            alive() ?: bind().also { attached.set(it) }
        }
    }

    private fun alive(): Attached? {
        val current = attached.get() ?: return null
        if (current.service.asBinder().isBinderAlive) return current
        forget(current)
        return null
    }

    private suspend fun bind(): Attached {
        val delivered = CompletableDeferred<IPrivilegedService>()
        val connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
                val service = IPrivilegedService.Stub.asInterface(binder) ?: return
                // Shizuku may deliver again after starting the service anew.
                if (!delivered.complete(service)) {
                    attached.getAndUpdate { if (it?.connection === this) Attached(service, this) else it }
                }
            }

            override fun onServiceDisconnected(name: ComponentName?) {
                attached.get()?.takeIf { it.connection === this }?.let(::forget)
            }
        }
        var bound = false
        try {
            Timber.i("Starting the Shizuku user service")
            connector.bind(connection)
            val service = withTimeoutOrNull(attachTimeout) { delivered.await() }
                ?: throw PrivilegedProcessUnavailableException("The Shizuku user service did not attach within $attachTimeout")
            bound = true
            return Attached(service, connection)
        } finally {
            // Also when cancelled: a service that attaches later has no one to serve.
            if (!bound) detach(connection, stop = true)
        }
    }

    private fun forget(current: Attached) {
        if (attached.compareAndSet(current, null)) {
            Timber.w("The Shizuku user service is gone; the next call starts it again")
        }
        detach(current.connection, stop = false)
    }

    private fun detach(connection: ServiceConnection, stop: Boolean) {
        try {
            connector.unbind(connection, stop)
        } catch (e: RuntimeException) {
            // Shizuku itself may be gone, which ends the service too.
            Timber.w(e, "Cannot unbind the Shizuku user service (stop=$stop)")
        }
    }
}
