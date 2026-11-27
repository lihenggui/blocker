/*
 * Copyright 2025 Blocker
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

package com.merxury.blocker.core.logging

import android.annotation.SuppressLint
import android.util.Log
import com.merxury.blocker.core.di.ApplicationScope
import com.merxury.blocker.core.di.FilesDir
import com.merxury.blocker.core.dispatchers.BlockerDispatchers.IO
import com.merxury.blocker.core.dispatchers.Dispatcher
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.toLocalDateTime
import timber.log.Timber
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import kotlin.time.Clock
import kotlin.time.Instant

const val LOG_DIR = "logs"
private const val MAX_FILE_SIZE = 5 * 1024 * 1024
private const val RETENTION_DAYS = 7
private val logFilePattern = Regex("""(\d{4}-\d{2}-\d{2})\.log(?:\.1)?""")

object LogFileLock {
    val mutex = Mutex()
}

class ReleaseTree internal constructor(
    filesDir: File,
    private val coroutineScope: CoroutineScope,
    private val ioDispatcher: CoroutineDispatcher,
    private val clock: Clock,
) : Timber.Tree() {
    @Inject
    constructor(
        @FilesDir filesDir: File,
        @ApplicationScope coroutineScope: CoroutineScope,
        @Dispatcher(IO) ioDispatcher: CoroutineDispatcher,
    ) : this(filesDir, coroutineScope, ioDispatcher, Clock.System)

    private val logDir = filesDir.resolve(LOG_DIR)
    private val started = AtomicBoolean()
    private val entries = Channel<LogEntry>(capacity = 256, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    fun start() {
        if (!started.compareAndSet(false, true)) return
        coroutineScope.launch(ioDispatcher) {
            val startupDate = clock.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
            var cleanupDate = startupDate.takeIf {
                LogFileLock.mutex.withLock { tryLogIo { clearOldLogs(it) } }
            }
            for (entry in entries) {
                val date = entry.timestamp.toLocalDateTime(TimeZone.currentSystemDefault()).date
                LogFileLock.mutex.withLock {
                    if (date != cleanupDate && tryLogIo { clearOldLogs(date) }) {
                        cleanupDate = date
                    }
                    tryLogIo { writeEntry(entry, date) }
                }
            }
        }
    }

    override fun isLoggable(
        tag: String?,
        priority: Int,
    ): Boolean = priority != Log.VERBOSE

    override fun log(
        priority: Int,
        tag: String?,
        message: String,
        t: Throwable?,
    ) {
        if (priority == Log.VERBOSE) return
        val timestamp = clock.now()
        val level =
            when (priority) {
                Log.DEBUG -> "D"
                Log.INFO -> "I"
                Log.WARN -> "W"
                Log.ERROR -> "E"
                Log.ASSERT -> "A"
                else -> "Unknown"
            }
        val stackTrace = t?.stackTraceToString()
        // Timber already appends the stack trace before calling Tree.log.
        val text =
            if (stackTrace != null && message.endsWith(stackTrace)) {
                message.removeSuffix(stackTrace).removeSuffix("\n")
            } else {
                message
            }
        entries.trySend(LogEntry(timestamp, level, tag ?: "Blocker", text, stackTrace))
    }

    private fun clearOldLogs(date: LocalDate) {
        if (!logDir.exists()) return
        val cutoff = date.minus(RETENTION_DAYS, DateTimeUnit.DAY).toString()
        val files = logDir.listFiles() ?: throw IOException("Cannot list logs in $logDir")
        for (file in files) {
            val dateText = logFilePattern.matchEntire(file.name)?.groupValues?.get(1) ?: continue
            if (!file.isFile) continue
            if (dateText < cutoff) {
                if (!file.delete()) throw IOException("Cannot delete old log $file")
            } else if (file.length() > MAX_FILE_SIZE) {
                keepNewestLogBytes(file)
            }
        }
    }

    private fun keepNewestLogBytes(file: File) {
        val temp = File.createTempFile("log-tail-", ".tmp", logDir)
        try {
            RandomAccessFile(file, "r").use { input ->
                input.seek(input.length() - MAX_FILE_SIZE)
                // Skip continuation bytes if the cutoff falls inside a UTF-8 character.
                var firstByte = input.read()
                while ((firstByte and 0xC0) == 0x80) firstByte = input.read()
                temp.outputStream().buffered().use { output ->
                    if (firstByte != -1) output.write(firstByte)
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    while (true) {
                        val count = input.read(buffer)
                        if (count == -1) break
                        output.write(buffer, 0, count)
                    }
                }
            }
            if (!temp.renameTo(file)) throw IOException("Cannot replace oversized log $file")
        } finally {
            tryLogIo {
                if (!temp.delete() && temp.exists()) throw IOException("Cannot delete temporary log $temp")
            }
        }
    }

    private fun writeEntry(
        entry: LogEntry,
        date: LocalDate,
    ) {
        if (!logDir.isDirectory && !logDir.mkdirs()) {
            throw IOException("Cannot create log directory $logDir")
        }
        val file = logDir.resolve("$date.log")
        val bytes = entry.encode()
        if (file.exists() && file.length() > MAX_FILE_SIZE.toLong() - bytes.size) {
            val rotated = logDir.resolve("$date.log.1")
            if (!file.renameTo(rotated)) throw IOException("Cannot rotate log $file")
        }
        file.appendBytes(bytes)
    }

    @SuppressLint("LogNotTimber")
    private inline fun tryLogIo(action: () -> Unit): Boolean = try {
        action()
        true
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Log.e("ReleaseTree", "Failed to maintain log files", e)
        false
    }

    private data class LogEntry(
        val timestamp: Instant,
        val level: String,
        val tag: String,
        val message: String,
        val stackTrace: String?,
    ) {
        fun encode(): ByteArray {
            val bytes =
                buildString {
                    append("$timestamp $level/$tag:")
                    if (message.isNotEmpty()) append(" $message")
                    appendLine()
                    stackTrace?.let(::append)
                }.encodeToByteArray()
            if (bytes.size <= MAX_FILE_SIZE) return bytes

            val marker = "\n[log entry truncated]\n".encodeToByteArray()
            var length = MAX_FILE_SIZE - marker.size
            // Leave any partial UTF-8 character out of the truncated log.
            while ((bytes[length].toInt() and 0xC0) == 0x80) length--
            return bytes.copyOf(length) + marker
        }
    }
}
