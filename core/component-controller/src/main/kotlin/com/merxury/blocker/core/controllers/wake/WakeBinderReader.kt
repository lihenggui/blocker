// Copyright 2026 Blocker
// SPDX-License-Identifier: Apache-2.0

package com.merxury.blocker.core.controllers.wake

import android.os.IBinder
import android.os.Parcel
import android.os.ParcelFileDescriptor
import android.os.ResultReceiver
import rikka.shizuku.ShizukuBinderWrapper
import rikka.shizuku.SystemServiceHelper
import java.io.File
import java.io.IOException
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/** Diagnostic fallback for services visible to the app. The native stats recorder requires the user service. */
internal object WakeBinderReader {
    private const val SHELL_COMMAND_TRANSACTION = 0x5f434d44
    private val executor = Executors.newFixedThreadPool(2)
    private val timer = Executors.newSingleThreadScheduledExecutor()

    fun read(command: WakeCommand, profile: Int, appUid: Int, boot: Int, argument: Long = 0): ByteArray {
        val argv = command.arguments(profile, appUid, boot, argument)
        val dump = argv.first() == "dumpsys"
        // The event log has no Binder service. Its commands report a gap in this fallback.
        val (service, skipped) = when (argv.first()) {
            "dumpsys" -> argv[3] to 4
            "cmd" -> if (argv[1] == "stats") throw IOException("The system recorder requires the Shizuku user service") else argv[1] to 2
            "pm" -> "package" to 1
            "am" -> "activity" to 1
            "settings" -> "settings" to 1
            else -> throw IOException("${command.name} requires the Shizuku user service")
        }
        val args = argv.drop(skipped).toTypedArray()
        val binder = ShizukuBinderWrapper(requireNotNull(SystemServiceHelper.getSystemService(service)) { "$service unavailable" })
        val (reader, writer) = ParcelFileDescriptor.createReliablePipe()
        val task = executor.submit {
            try {
                val input = ParcelFileDescriptor.open(File("/dev/null"), ParcelFileDescriptor.MODE_READ_ONLY)
                input.use { source ->
                    ParcelFileDescriptor.open(File("/dev/null"), ParcelFileDescriptor.MODE_WRITE_ONLY).use { error ->
                        val data = Parcel.obtain()
                        val reply = Parcel.obtain()
                        try {
                            if (!dump) data.writeFileDescriptor(source.fileDescriptor)
                            data.writeFileDescriptor(writer.fileDescriptor)
                            if (!dump) data.writeFileDescriptor(error.fileDescriptor)
                            data.writeStringArray(args)
                            val result = CommandResult()
                            if (!dump) {
                                data.writeStrongBinder(null)
                                result.writeToParcel(data, 0)
                            }
                            check(binder.transact(if (dump) IBinder.DUMP_TRANSACTION else SHELL_COMMAND_TRANSACTION, data, reply, 0))
                            reply.readException()
                            if (!dump) result.checkSuccess()
                        } finally {
                            data.recycle()
                            reply.recycle()
                        }
                    }
                }
                writer.close()
            } catch (e: Exception) {
                writer.closeWithError(e.message ?: command.name)
            }
        }
        val timeout = timer.schedule({
            try {
                writer.closeWithError("System service timeout")
            } catch (e: IOException) {
                timber.log.Timber.d(e, "Wake dump writer already closed")
            }
            try {
                reader.close()
            } catch (e: IOException) {
                timber.log.Timber.d(e, "Cannot close timed-out wake dump reader")
            }
        }, 20, TimeUnit.SECONDS)
        try {
            return ParcelFileDescriptor.AutoCloseInputStream(reader).use { input ->
                val bytes = java.io.ByteArrayOutputStream()
                input.copyBounded(bytes)
                reader.checkError()
                bytes.toByteArray()
            }
        } finally {
            timeout.cancel(false)
            task.cancel(true)
        }
    }

    private class CommandResult : ResultReceiver(null) {
        private val done = java.util.concurrent.CountDownLatch(1)
        private var code = -1
        override fun onReceiveResult(resultCode: Int, resultData: android.os.Bundle?) {
            code = resultCode
            done.countDown()
        }
        fun checkSuccess() {
            check(done.await(15, TimeUnit.SECONDS) && code == 0) { "System shell command unavailable ($code)" }
        }
    }
}
