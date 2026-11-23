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

import android.app.ActivityManagerNative
import android.app.IActivityManager
import android.content.pm.IPackageManager
import android.os.Build
import android.os.IBinder
import android.os.IUserManager
import android.os.Parcel
import android.os.Parcelable
import android.os.Process
import be.mygod.librootkotlinx.systemContext
import com.merxury.blocker.core.controllers.root.api.SystemServiceHelper

/**
 * The system services as seen from a process that itself runs as root, system or the shell: the root
 * process, or the Shizuku user service. They need no binder wrapper there.
 */
internal object PrivilegedProcessSystemServices : SystemServices {
    override val packageManager: IPackageManager by lazy { IPackageManager.Stub.asInterface(service("package")) }

    override val activityManager: IActivityManager by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            IActivityManager.Stub.asInterface(service("activity"))
        } else {
            ActivityManagerNative.asInterface(service("activity"))
        }
    }

    override val userManager: IUserManager by lazy { IUserManager.Stub.asInterface(service("user")) }

    override val callerPackage: String get() = systemContext.packageName

    override val callerUid: Int get() = Process.myUid()

    private fun service(name: String): IBinder = checkNotNull(SystemServiceHelper.getSystemService(name)) { "No $name service" }
}

/**
 * A call that [PrivilegedCall.needsPrivilegedProcess] could not reach that process, while the
 * channel itself still works: the Shizuku user service did not attach in time or died. Maps to
 * [com.merxury.blocker.core.model.privileged.CallFailure.ChannelUnavailable] but leaves the
 * channel state alone, since calls that only talk to system services still succeed.
 */
internal class PrivilegedProcessUnavailableException(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause)

/**
 * Writes [call] as bytes for [unmarshallCall]: its class name, then its fields. Calls carry only
 * plain values, never binders or file descriptors, so the bytes can travel in a `byte[]`.
 */
internal fun marshallCall(call: PrivilegedCall<*>): ByteArray {
    val parcel = Parcel.obtain()
    try {
        parcel.writeString(call.javaClass.name)
        call.writeToParcel(parcel, 0)
        return parcel.marshall()
    } finally {
        parcel.recycle()
    }
}

/**
 * Reads a call that [marshallCall] wrote, in the privileged process. Only accepts a class of
 * Blocker's own code that implements [PrivilegedCall], so a caller cannot make this process
 * create any other Parcelable the class path offers: the class is looked up without being
 * initialized and checked before its `CREATOR` is touched.
 *
 * @throws SecurityException when the bytes name any other class
 */
internal fun unmarshallCall(bytes: ByteArray): PrivilegedCall<*> {
    val parcel = Parcel.obtain()
    try {
        parcel.unmarshall(bytes, 0, bytes.size)
        parcel.setDataPosition(0)
        val name = parcel.readString() ?: throw SecurityException("The call names no class")
        val ownLoader = PrivilegedCall::class.java.classLoader
        val type = try {
            Class.forName(name, false, ownLoader)
        } catch (e: ClassNotFoundException) {
            throw SecurityException("Unknown call class $name").apply { initCause(e) }
        }
        if (type.classLoader !== ownLoader || !PrivilegedCall::class.java.isAssignableFrom(type)) {
            throw SecurityException("$name is not a privileged call of this app")
        }
        val creator = type.getField("CREATOR").get(null) as Parcelable.Creator<*>
        return creator.createFromParcel(parcel) as PrivilegedCall<*>
    } finally {
        parcel.recycle()
    }
}
