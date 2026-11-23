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

import android.os.Parcel
import android.os.ParcelFileDescriptor
import android.os.Parcelable
import java.io.IOException
import kotlin.concurrent.thread

/**
 * The outcome from [encodeOutcome] on its way back from a privileged process: the root process or
 * the Shizuku user service. librootkotlinx answers through a one-way binder call, which fails
 * above a few hundred kilobytes, and every binder reply shares one buffer of about a megabyte,
 * while a package list or the components of a large app can be megabytes. A large outcome
 * therefore travels through a pipe that the privileged process fills from a background thread.
 */
internal class CallReply private constructor(
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

    companion object CREATOR : Parcelable.Creator<CallReply> {
        private const val INLINE = 0
        private const val PIPED = 1

        // Well below the space binder keeps for one-way calls.
        private const val INLINE_LIMIT = 64 * 1024

        fun of(bytes: ByteArray): CallReply = if (bytes.size <= INLINE_LIMIT) {
            CallReply(bytes, null)
        } else {
            CallReply(null, pipeOf(bytes))
        }

        private fun pipeOf(bytes: ByteArray): ParcelFileDescriptor {
            val (read, write) = ParcelFileDescriptor.createPipe()
            thread(isDaemon = true, name = "CallReply") {
                try {
                    ParcelFileDescriptor.AutoCloseOutputStream(write).use { it.write(bytes) }
                } catch (_: IOException) {
                    // The app stopped reading, for example because its caller was cancelled.
                }
            }
            return read
        }

        override fun createFromParcel(source: Parcel): CallReply = when (val kind = source.readInt()) {
            INLINE -> CallReply(source.createByteArray(), null)
            PIPED -> CallReply(null, ParcelFileDescriptor.CREATOR.createFromParcel(source))
            else -> error("Unknown reply $kind")
        }

        override fun newArray(size: Int): Array<CallReply?> = arrayOfNulls(size)
    }
}
