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

import android.os.BadParcelableException
import android.os.Parcel
import android.os.ParcelFileDescriptor
import android.os.Parcelable
import java.io.ByteArrayOutputStream
import java.io.Closeable
import java.io.IOException
import java.io.InputStream
import java.util.concurrent.ExecutorService
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.Semaphore
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

/**
 * A call from [marshallCall], or its outcome from [encodeOutcome], on its way to or from a
 * privileged process: the root process or the Shizuku user service. librootkotlinx sends both
 * directions in one-way binder calls, which fail above a few hundred kilobytes, and every binder
 * transaction of a process shares one buffer of about a megabyte, while a package list or a rule
 * file can be larger. Up to [INLINE_LIMIT] bytes therefore travel in the parcel; more go through
 * a pipe that the sending side fills from a background thread.
 *
 * A write finishes once every byte is written or the reading side closes its end.
 */
internal class CallBytes private constructor(
    private val bytes: ByteArray?,
    private val pipe: ParcelFileDescriptor?,
    private val closeWhenParceled: Boolean,
) : Parcelable,
    Closeable {

    /**
     * The bytes. Reads the pipe to its end and closes it, so call it once.
     *
     * @throws IOException when the pipe breaks or the payload exceeds [maxBytes]
     */
    fun read(maxBytes: Int = Int.MAX_VALUE): ByteArray {
        require(maxBytes >= 0)
        bytes?.let {
            if (it.size > maxBytes) throw IOException("More than $maxBytes bytes")
            return it
        }
        return ParcelFileDescriptor.AutoCloseInputStream(pipe).use { it.readAtMost(maxBytes) }
    }

    /** Closes this side's copy of the pipe, so its writer stops if nobody else reads. Safe to repeat. */
    override fun close() {
        pipe?.close()
    }

    override fun describeContents(): Int = if (pipe != null) Parcelable.CONTENTS_FILE_DESCRIPTOR else 0

    override fun writeToParcel(dest: Parcel, flags: Int) {
        if (pipe == null) {
            dest.writeInt(INLINE)
            dest.writeByteArray(bytes)
        } else {
            dest.writeInt(PIPED)
            pipe.writeToParcel(dest, if (closeWhenParceled) Parcelable.PARCELABLE_WRITE_RETURN_VALUE else 0)
        }
    }

    companion object CREATOR : Parcelable.Creator<CallBytes> {
        private const val INLINE = 0
        private const val PIPED = 1

        // Well below the space binder keeps for one-way calls.
        private const val INLINE_LIMIT = 64 * 1024

        private const val MAX_PENDING_PIPE_WRITES = 20
        private val pipeWriterSlots = Semaphore(MAX_PENDING_PIPE_WRITES)
        private val pipeWriters: ExecutorService = ThreadPoolExecutor(
            4,
            4,
            30,
            TimeUnit.SECONDS,
            LinkedBlockingQueue(MAX_PENDING_PIPE_WRITES),
            { task -> Thread(task, "CallBytes").apply { isDaemon = true } },
        ).apply { allowCoreThreadTimeOut(true) }

        /** Robolectric pipes are files, so JVM tests wait for pending writes before reading. */
        internal fun hasPendingPipeWrites(): Boolean = pipeWriterSlots.availablePermits() != MAX_PENDING_PIPE_WRITES

        /** No call needs more; a larger one is a bug, not a rule file. */
        const val MAX_REQUEST_BYTES = 16 * 1024 * 1024

        /**
         * An outcome a privileged process sends back. This side's copy of the pipe closes once it
         * is in the reply, so the writer sees a broken pipe if the app goes away without reading.
         */
        fun reply(bytes: ByteArray): CallBytes = of(bytes, closeWhenParceled = true)

        /**
         * A call the app sends. The sender keeps its copy of the pipe while the call runs, since
         * the request may be parceled more than once, and must [close] it when the call returns
         * or fails, which also stops the writer when the request never arrived.
         *
         * @throws IllegalStateException above [MAX_REQUEST_BYTES]
         */
        fun request(bytes: ByteArray): CallBytes {
            check(bytes.size <= MAX_REQUEST_BYTES) { "A call of ${bytes.size} bytes is more than $MAX_REQUEST_BYTES" }
            return of(bytes, closeWhenParceled = false)
        }

        private fun of(bytes: ByteArray, closeWhenParceled: Boolean): CallBytes = if (bytes.size <= INLINE_LIMIT) {
            CallBytes(bytes, null, closeWhenParceled)
        } else {
            CallBytes(null, pipeOf(bytes), closeWhenParceled)
        }

        private fun pipeOf(bytes: ByteArray): ParcelFileDescriptor {
            // Bound the total queued and running work before allocating pipe descriptors.
            if (!pipeWriterSlots.tryAcquire()) throw IOException("Too many pending privileged pipes")
            var scheduled = false
            try {
                val (read, write) = ParcelFileDescriptor.createPipe()
                try {
                    pipeWriters.execute { writePipe(write, bytes) }
                    scheduled = true
                } catch (e: RejectedExecutionException) {
                    read.use { write.use { throw IOException("Cannot schedule a privileged pipe write", e) } }
                }
                return read
            } finally {
                if (!scheduled) pipeWriterSlots.release()
            }
        }

        private fun writePipe(write: ParcelFileDescriptor, bytes: ByteArray) {
            try {
                ParcelFileDescriptor.AutoCloseOutputStream(write).use { it.write(bytes) }
            } catch (_: IOException) {
                // The other side stopped reading, for example because its caller was cancelled.
            } finally {
                pipeWriterSlots.release()
            }
        }

        override fun createFromParcel(source: Parcel): CallBytes = when (val kind = source.readInt()) {
            INLINE -> {
                val bytes = source.createByteArray() ?: throw BadParcelableException("Missing inline call bytes")
                CallBytes(bytes, null, closeWhenParceled = false)
            }

            PIPED -> {
                val pipe = ParcelFileDescriptor.CREATOR.createFromParcel(source)
                    ?: throw BadParcelableException("Missing call pipe")
                CallBytes(null, pipe, closeWhenParceled = false)
            }

            else -> error("Unknown kind $kind")
        }

        override fun newArray(size: Int): Array<CallBytes?> = arrayOfNulls(size)
    }
}

private fun InputStream.readAtMost(maxBytes: Int): ByteArray {
    val output = ByteArrayOutputStream()
    val buffer = ByteArray(64 * 1024)
    while (true) {
        val count = read(buffer)
        if (count < 0) return output.toByteArray()
        if (output.size() > maxBytes - count) throw IOException("More than $maxBytes bytes")
        output.write(buffer, 0, count)
    }
}
