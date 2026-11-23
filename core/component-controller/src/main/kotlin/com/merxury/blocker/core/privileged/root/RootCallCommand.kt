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

import android.app.ActivityManagerNative
import android.app.IActivityManager
import android.content.pm.IPackageManager
import android.os.Build
import android.os.IBinder
import android.os.IUserManager
import android.os.Parcel
import android.os.ParcelFileDescriptor
import android.os.Parcelable
import be.mygod.librootkotlinx.RootCommand
import be.mygod.librootkotlinx.systemContext
import com.merxury.blocker.core.controllers.root.api.SystemServiceHelper
import com.merxury.blocker.core.privileged.PrivilegedCall
import com.merxury.blocker.core.privileged.SystemServices
import kotlinx.parcelize.Parcelize
import java.io.IOException
import kotlin.concurrent.thread

/**
 * Runs [call] in the root process and sends back its outcome from [encodeOutcome]. A call that
 * throws still completes this command; only a broken root process fails it.
 */
@Parcelize
internal class RootCallCommand(val call: PrivilegedCall<*>) : RootCommand<RootCallReply> {
    override suspend fun execute(): RootCallReply = RootCallReply.of(encodeOutcome(call, RootSystemServices))
}

/**
 * The encoded outcome of a [RootCallCommand]. librootkotlinx answers through a one-way binder
 * call, which fails above a few hundred kilobytes, while a package list or the components of a
 * large app can be megabytes. A large outcome therefore travels through a pipe that the root
 * process fills from a background thread.
 */
internal class RootCallReply private constructor(
    private val bytes: ByteArray?,
    private val pipe: ParcelFileDescriptor?,
) : Parcelable {

    /** The encoded outcome. Reads the pipe to its end, so call it once. */
    fun read(): ByteArray = bytes ?: ParcelFileDescriptor.AutoCloseInputStream(pipe).use { it.readBytes() }

    override fun describeContents(): Int = if (pipe != null) Parcelable.CONTENTS_FILE_DESCRIPTOR else 0

    override fun writeToParcel(dest: Parcel, flags: Int) {
        if (pipe == null) {
            dest.writeInt(INLINE)
            dest.writeByteArray(bytes)
        } else {
            dest.writeInt(PIPED)
            // Closes this side's copy of the read end once it is in the reply, so the writer sees
            // a broken pipe if the app goes away without reading.
            pipe.writeToParcel(dest, Parcelable.PARCELABLE_WRITE_RETURN_VALUE)
        }
    }

    companion object CREATOR : Parcelable.Creator<RootCallReply> {
        private const val INLINE = 0
        private const val PIPED = 1

        // Well below the space binder keeps for one-way calls.
        private const val INLINE_LIMIT = 64 * 1024

        fun of(bytes: ByteArray): RootCallReply = if (bytes.size <= INLINE_LIMIT) {
            RootCallReply(bytes, null)
        } else {
            RootCallReply(null, pipeOf(bytes))
        }

        private fun pipeOf(bytes: ByteArray): ParcelFileDescriptor {
            val (read, write) = ParcelFileDescriptor.createPipe()
            thread(isDaemon = true, name = "RootCallReply") {
                try {
                    ParcelFileDescriptor.AutoCloseOutputStream(write).use { it.write(bytes) }
                } catch (_: IOException) {
                    // The app stopped reading, for example because its caller was cancelled.
                }
            }
            return read
        }

        override fun createFromParcel(source: Parcel): RootCallReply = when (val kind = source.readInt()) {
            INLINE -> RootCallReply(source.createByteArray(), null)
            PIPED -> RootCallReply(null, ParcelFileDescriptor.CREATOR.createFromParcel(source))
            else -> error("Unknown reply $kind")
        }

        override fun newArray(size: Int): Array<RootCallReply?> = arrayOfNulls(size)
    }
}

/** The system services as seen from the root process, where calls run as uid 0. */
private object RootSystemServices : SystemServices {
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

    private fun service(name: String): IBinder = checkNotNull(SystemServiceHelper.getSystemService(name)) { "No $name service" }
}
