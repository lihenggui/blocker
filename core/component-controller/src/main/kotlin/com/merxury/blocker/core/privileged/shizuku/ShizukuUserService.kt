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

import android.app.ActivityManager
import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.os.DeadObjectException
import android.os.IBinder
import com.merxury.blocker.core.di.ApplicationScope
import com.merxury.blocker.core.dispatchers.BlockerDispatchers.IO
import com.merxury.blocker.core.dispatchers.Dispatcher
import com.merxury.blocker.core.model.privileged.CallFailure
import com.merxury.blocker.core.model.privileged.CallResult
import com.merxury.blocker.core.privileged.CallBytes
import com.merxury.blocker.core.privileged.PrivilegedCall
import com.merxury.blocker.core.privileged.PrivilegedProcessUnavailableException
import com.merxury.blocker.core.privileged.decodeOutcome
import com.merxury.blocker.core.privileged.marshallCall
import com.merxury.blocker.core.utils.currentUserId
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.plus
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import rikka.shizuku.Shizuku
import timber.log.Timber
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

// A service is a full app_process: after a background collection it held about 160 MB of PSS.
// Short, because a process left in the background is cached and soon frozen, and a frozen
// process runs no timer: a longer wait would keep the service until Blocker runs again.
private val IDLE_STOP = 5.seconds

/**
 * When to stop the service between uses: once it has run no call for [after] while
 * [screenVisible] says no screen of Blocker is visible. A screen in front keeps it, so the calls of
 * a screen the user works in never wait for a new process.
 */
internal class IdleStop(
    val after: Duration,
    val screenVisible: () -> Boolean,
)

/** Whether a screen of this process is visible, as the activity manager ranks the process. */
private fun hasVisibleScreen(): Boolean {
    val state = ActivityManager.RunningAppProcessInfo()
    ActivityManager.getMyMemoryState(state)
    return state.importance <= ActivityManager.RunningAppProcessInfo.IMPORTANCE_VISIBLE
}

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
    // Shizuku keeps one service per package and tag for the whole device, and the service serves
    // only the uid it was started for. A tag per Android user gives Blocker in a work profile its
    // own service instead of the owner's, which would refuse it.
    private val userId = currentUserId().value
    private val args = Shizuku.UserServiceArgs(ComponentName(context, ShizukuPrivilegedService::class.java))
        .daemon(false)
        .processNameSuffix("privileged_u$userId")
        .tag("privileged_u$userId")
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
 * time. With [idleStop], stops it when it is idle and no screen needs it, as after a collection in
 * the background; the next call starts it again.
 */
@Singleton
internal class ShizukuUserService(
    private val connector: UserServiceConnector,
    private val attachTimeout: Duration,
    private val scope: CoroutineScope,
    private val idleStop: IdleStop? = null,
) {
    @Inject constructor(
        connector: ShizukuUserServiceConnector,
        @ApplicationScope scope: CoroutineScope,
        @Dispatcher(IO) ioDispatcher: CoroutineDispatcher,
    ) : this(connector, ATTACH_TIMEOUT, scope + ioDispatcher, IdleStop(IDLE_STOP, ::hasVisibleScreen))

    private class Attached(val service: IPrivilegedService, val connection: ServiceConnection)

    // The previous bind finishes detaching before a new one can ask Shizuku to start the service.
    private val bindLock = Mutex()

    // Guards the service and its lifetime across calls, binder callbacks and the idle timer.
    private val serviceLock = Any()
    private var attached: Attached? = null
    private var pending: Deferred<Result<Attached>>? = null
    private var inFlight = 0
    private var idleJob: Job? = null

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
        synchronized(serviceLock) {
            inFlight++
            idleJob?.cancel()
        }
        try {
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
        } finally {
            synchronized(serviceLock) {
                inFlight--
                if (inFlight == 0 && pending == null) scheduleIdleStop()
            }
        }
    }

    // Call holding serviceLock.
    private fun scheduleIdleStop() {
        val policy = idleStop ?: return
        if (attached == null) return
        idleJob = scope.launch {
            do delay(policy.after) while (policy.screenVisible())
            synchronized(serviceLock) {
                ensureActive()
                if (inFlight > 0 || pending != null) return@synchronized
                attached?.let {
                    attached = null
                    Timber.i("Stopping the idle Shizuku user service")
                    detach(it.connection, stop = true)
                }
            }
        }
    }

    /** Drops the bound service, for example because Shizuku stopped. The next call binds again. */
    fun reset() {
        synchronized(serviceLock) {
            val attempt = pending
            pending = null
            attempt?.cancel()
            idleJob?.cancel()
            attached?.let(::forget)
        }
    }

    private suspend fun attach(): Attached {
        val attempt = synchronized(serviceLock) {
            attached?.let { current ->
                if (current.service.asBinder().isBinderAlive) return current
                forget(current)
            }
            // A caller's cancellation only stops its wait. The next call can join this attach.
            pending ?: scope.async(start = CoroutineStart.LAZY) { bindAndKeep() }.also { pending = it }
        }
        return try {
            attempt.await().getOrThrow()
        } catch (e: CancellationException) {
            currentCoroutineContext().ensureActive()
            throw PrivilegedProcessUnavailableException("Shizuku disconnected while its user service was starting", e)
        }
    }

    private suspend fun bindAndKeep(): Result<Attached> {
        val attempt = currentCoroutineContext()[Job]
        try {
            return bindLock.withLock {
                val current = bind()
                synchronized(serviceLock) {
                    if (pending !== attempt) {
                        detach(current.connection, stop = false)
                        throw PrivilegedProcessUnavailableException("Shizuku disconnected while its user service was starting")
                    }
                    attached = current
                }
                Result.success(current)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.w(e, "Cannot attach the Shizuku user service")
            return Result.failure(e)
        } finally {
            synchronized(serviceLock) {
                if (pending === attempt) {
                    pending = null
                    if (inFlight == 0) scheduleIdleStop()
                }
            }
        }
    }

    private suspend fun bind(): Attached {
        val delivered = CompletableDeferred<IPrivilegedService>()
        val connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
                val service = IPrivilegedService.Stub.asInterface(binder) ?: return
                // Shizuku may deliver again after starting the service anew.
                if (!delivered.complete(service)) {
                    synchronized(serviceLock) {
                        if (attached?.connection === this) attached = Attached(service, this)
                    }
                }
            }

            override fun onServiceDisconnected(name: ComponentName?) {
                synchronized(serviceLock) {
                    attached?.takeIf { it.connection === this }?.let(::forget)
                }
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
            // Stop a failed attach or one cancelled when Shizuku disconnected.
            if (!bound) detach(connection, stop = true)
        }
    }

    private fun forget(current: Attached) {
        synchronized(serviceLock) {
            if (attached === current) {
                attached = null
                Timber.w("The Shizuku user service is gone; the next call starts it again")
                detach(current.connection, stop = false)
            }
        }
    }

    private fun detach(connection: ServiceConnection, stop: Boolean) {
        try {
            connector.unbind(connection, stop)
        } catch (e: RuntimeException) {
            // Expected when Shizuku itself is gone, which ends the service too.
            Timber.d("Cannot unbind the Shizuku user service (stop=$stop): ${e.message}")
        }
    }
}
