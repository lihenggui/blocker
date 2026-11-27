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

package com.merxury.blocker.core.privileged

import android.app.IActivityManager
import android.content.pm.IPackageInstaller
import android.content.pm.IPackageManager
import android.os.IDeviceIdleController
import android.os.IUserManager
import android.os.Parcel
import android.os.Parcelable
import com.android.internal.app.IAppOpsService
import com.merxury.blocker.core.model.component.UserId
import com.merxury.blocker.core.model.privileged.CallFailure
import com.merxury.blocker.core.model.privileged.CallResult
import com.merxury.blocker.core.model.privileged.ChannelState
import com.merxury.blocker.core.model.privileged.UnavailableReason
import kotlinx.parcelize.Parceler

/** The hidden system service interfaces as the privileged identity sees them. */
internal interface SystemServices {
    val packageManager: IPackageManager
    val activityManager: IActivityManager
    val userManager: IUserManager
    val appOps: IAppOpsService
    val deviceIdle: IDeviceIdleController

    /**
     * The package installer, called as the privileged identity. The default asks [packageManager]
     * for it, which is right where calls reach the system directly, as in the root process.
     */
    val packageInstaller: IPackageInstaller get() = packageManager.packageInstaller

    /** What to pass where the system asks for the calling package. */
    val callerPackage: String

    /** The uid the system sees as the caller: 0 for root, 1000 for system, 2000 for the shell. */
    val callerUid: Int
}

/**
 * One request to the system, written once for both transports. The root transport parcels the
 * call, runs [run] in the root process and parcels the result back with [writeResult] and
 * [readResult]; the Shizuku transport runs [run] in this process, or, when
 * [needsPrivilegedProcess], parcels it the same way to its user service.
 *
 * [run] blocks and throws what the system throws. It addresses its user explicitly and never
 * takes one from a Context.
 *
 * A call must be safe to run twice unless [retryable] is false. When the root process goes away
 * during a call, the root transport sends a retryable call again on a new process, and the first
 * attempt may already have reached the system. Reads and setting a value to a fixed state are
 * safe; anything that adds, toggles or consumes what it reads is not.
 */
internal interface PrivilegedCall<R> : Parcelable {
    /**
     * Whether [run] must itself run with the channel's identity, for example to open files under
     * `/data/system` or to start a system tool, rather than only talk to system services through
     * [SystemServices]. Such a call runs in the root process or the Shizuku user service, never in
     * the app's process.
     */
    val needsPrivilegedProcess: Boolean get() = false

    /**
     * Whether [run] reads or writes files under `/data/system`, which only root and the system
     * uid may. The Shizuku transport answers such a call as the shell with
     * [CallFailure.NotPermitted] without starting its user service. Defaults to
     * [needsPrivilegedProcess]; a call that needs the process only to run a tool the shell may
     * also run says false.
     */
    val needsSystemFiles: Boolean get() = needsPrivilegedProcess

    /**
     * Whether the call may be sent again after the privileged process went away while running it.
     * False for a call that must not reach the system twice, such as a read that deletes what it
     * returns; the root transport then fails it with [CallFailure.ChannelUnavailable] instead.
     */
    val retryable: Boolean get() = true

    fun run(system: SystemServices): R

    /** The default handles every value `Parcel.writeValue` accepts. */
    fun writeResult(result: R, parcel: Parcel) {
        parcel.writeValue(result)
    }

    @Suppress("UNCHECKED_CAST")
    fun readResult(parcel: Parcel): R = parcel.readValue(javaClass.classLoader) as R
}

/** A [PrivilegedCall] that only changes something. */
internal interface PrivilegedAction : PrivilegedCall<Unit> {
    override fun writeResult(result: Unit, parcel: Parcel) = Unit

    override fun readResult(parcel: Parcel) = Unit
}

/** Runs [PrivilegedCall]s through a channel. */
internal interface PrivilegedExecutor {
    /**
     * Runs [call]. When the channel is not [ChannelState.Ready], connects once first, which may
     * show a permission prompt, and fails with [CallFailure.ChannelUnavailable] if that does not
     * make it ready.
     */
    suspend fun <R> execute(call: PrivilegedCall<R>): CallResult<R>

    /**
     * Runs [call] only when [checkedState] is [ChannelState.Ready]; otherwise fails with
     * [CallFailure.ChannelUnavailable]. Follows the quiet policy in
     * [PrivilegedChannel.connectIfGranted], including when restarting an idle-closed root server.
     */
    suspend fun <R> executeIfReady(call: PrivilegedCall<R>): CallResult<R>

    /**
     * The channel's state once the first attempt to acquire it has ended. Returns the state at
     * once unless it is still [UnavailableReason.NOT_CHECKED]; then makes a quiet attempt or
     * waits for the quiet attempt already
     * running, bounded by the transport's own timeouts. Never waits for a prompt to be answered and
     * never calls [PrivilegedChannel.connect]. When the attempt it waited for was cancelled, it
     * makes its own. Never throws except for cancellation.
     */
    suspend fun checkedState(): ChannelState
}

/** One way to reach the system with privilege: a root process or Shizuku. */
internal interface PrivilegedTransport :
    PrivilegedChannel,
    PrivilegedExecutor {
    /** Whether a previously ready connection can be reused without acquiring it again. */
    suspend fun isConnectionCurrent(): Boolean
}

/** Lets calls carry a [UserId] with `@TypeParceler`. */
internal object UserIdParceler : Parceler<UserId> {
    override fun create(parcel: Parcel): UserId = UserId(parcel.readInt())

    override fun UserId.write(parcel: Parcel, flags: Int) {
        parcel.writeInt(value)
    }
}
