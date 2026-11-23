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

import android.app.ActivityManagerNative
import android.app.IActivityManager
import android.content.Context
import android.content.pm.IPackageManager
import android.content.pm.PackageManager
import android.os.Build
import android.os.IUserManager
import com.merxury.blocker.core.controllers.shizuku.IShizukuInitializer
import com.merxury.blocker.core.controllers.shizuku.RegisterShizukuResult
import com.merxury.blocker.core.controllers.shizuku.ShizukuApi
import com.merxury.blocker.core.dispatchers.BlockerDispatchers.IO
import com.merxury.blocker.core.dispatchers.Dispatcher
import com.merxury.blocker.core.model.privileged.CallFailure
import com.merxury.blocker.core.model.privileged.CallResult
import com.merxury.blocker.core.model.privileged.ChannelState
import com.merxury.blocker.core.model.privileged.PrivilegedIdentity
import com.merxury.blocker.core.model.privileged.UnavailableReason
import com.merxury.blocker.core.privileged.AbstractPrivilegedTransport
import com.merxury.blocker.core.privileged.ChannelLostException
import com.merxury.blocker.core.privileged.PrivilegedCall
import com.merxury.blocker.core.privileged.SystemServices
import com.merxury.blocker.core.privileged.identityOfUid
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import rikka.shizuku.ShizukuBinderWrapper
import rikka.shizuku.SystemServiceHelper
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Runs calls in this process against system services reached through Shizuku or Sui, as the
 * uid the Shizuku server runs as: root, or the shell when Shizuku was started through adb.
 * Connecting asks for the Shizuku permission when it has not been granted yet.
 *
 * A call that [PrivilegedCall.needsPrivilegedProcess] runs in [ShizukuUserService] instead, and
 * only when the server runs as root; as the shell it fails with [CallFailure.NotPermitted] and
 * starts nothing.
 *
 * Shizuku's binder events keep [state] current without a prompt: when the binder dies a ready
 * channel becomes [UnavailableReason.NOT_RUNNING], and when a new binder arrives a channel that has
 * been ready becomes ready again, as the uid the new server runs as, if the permission is still
 * granted. The listeners are registered once, for the life of this singleton.
 */
@Singleton
internal class ShizukuTransport(
    private val initializer: IShizukuInitializer,
    private val shizuku: ShizukuApi,
    private val system: SystemServices,
    private val userService: ShizukuUserService,
    private val ioDispatcher: CoroutineDispatcher,
) : AbstractPrivilegedTransport() {

    @Inject constructor(
        initializer: IShizukuInitializer,
        shizuku: ShizukuApi,
        system: ShizukuSystemServices,
        userService: ShizukuUserService,
        @Dispatcher(IO) ioDispatcher: CoroutineDispatcher,
    ) : this(initializer, shizuku, system as SystemServices, userService, ioDispatcher)

    init {
        shizuku.addBinderDeadListener { onBinderDead() }
        shizuku.addBinderReceivedListener { onBinderReceived() }
    }

    override suspend fun acquire(): ChannelState = when (val result = initializer.registerShizuku()) {
        is RegisterShizukuResult.Granted -> identityOfUid(result.uid)?.let { ChannelState.Ready(it) }
            ?: ChannelState.Unavailable(UnavailableReason.FAILED)
                .also { Timber.e("Shizuku runs as uid ${result.uid}, which is neither root nor the shell") }

        RegisterShizukuResult.Denied -> ChannelState.Unavailable(UnavailableReason.DENIED)

        RegisterShizukuResult.NotRunning -> ChannelState.Unavailable(UnavailableReason.NOT_RUNNING)

        RegisterShizukuResult.UnsupportedVersion -> ChannelState.Unavailable(UnavailableReason.UNSUPPORTED_VERSION)

        RegisterShizukuResult.TimedOut -> ChannelState.Unavailable(UnavailableReason.TIMED_OUT)

        // ShizukuInitializer has logged the cause.
        is RegisterShizukuResult.Failed -> ChannelState.Unavailable(UnavailableReason.FAILED)
    }

    override suspend fun <R> send(call: PrivilegedCall<R>): CallResult<R> = withContext(ioDispatcher) {
        if (!shizuku.pingBinder()) throw ChannelLostException(UnavailableReason.NOT_RUNNING, "Shizuku is not running")
        val inProcess = !call.needsPrivilegedProcess
        val identity = (state.value as? ChannelState.Ready)?.identity
        if (!inProcess && identity != PrivilegedIdentity.ROOT) {
            return@withContext CallResult.Failure(CallFailure.NotPermitted("$call needs Shizuku running as root, not $identity"))
        }
        try {
            if (inProcess) CallResult.Success(call.run(system)) else userService.run(call)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Shizuku reports its own death in several ways, most of them a bare RuntimeException.
            if (!shizuku.pingBinder()) throw ChannelLostException(UnavailableReason.NOT_RUNNING, "Shizuku stopped", e)
            throw e
        }
    }

    private fun onBinderDead() {
        userService.reset()
        updateState { current ->
            if (current is ChannelState.Ready) NOT_RUNNING else current
        }
    }

    // Never asks for the permission: a listener must not show a prompt.
    private fun onBinderReceived() {
        // A new server may run as another uid; its user services are new too.
        userService.reset()
        if (!hasBeenReady) return
        val next = try {
            if (shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
                identityOfUid(shizuku.getUid())?.let { ChannelState.Ready(it) }
                    ?: ChannelState.Unavailable(UnavailableReason.FAILED)
            } else {
                ChannelState.Unavailable(UnavailableReason.DENIED)
            }
        } catch (e: Exception) {
            Timber.e(e, "Cannot read the state of the new Shizuku binder")
            ChannelState.Unavailable(UnavailableReason.FAILED)
        }
        if (next !is ChannelState.Ready) Timber.w("Shizuku is back, but the channel stays unavailable: $next")
        updateState { current ->
            when {
                // A ready channel may now run as another uid, or no longer be allowed.
                current is ChannelState.Ready -> next

                current == NOT_RUNNING && next is ChannelState.Ready -> next

                else -> current
            }
        }
    }

    private companion object {
        val NOT_RUNNING = ChannelState.Unavailable(UnavailableReason.NOT_RUNNING)
    }
}

/** The system services as reached through Shizuku, in this process. */
internal class ShizukuSystemServices @Inject constructor(
    @ApplicationContext private val context: Context,
) : SystemServices {
    override val packageManager: IPackageManager by lazy { IPackageManager.Stub.asInterface(service("package")) }

    override val activityManager: IActivityManager by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            IActivityManager.Stub.asInterface(service("activity"))
        } else {
            ActivityManagerNative.asInterface(service("activity"))
        }
    }

    override val userManager: IUserManager by lazy { IUserManager.Stub.asInterface(service("user")) }

    override val callerPackage: String get() = context.packageName

    private fun service(name: String) = ShizukuBinderWrapper(
        checkNotNull(SystemServiceHelper.getSystemService(name)) { "No $name service" },
    )
}
