/*
 * Copyright 2023 Blocker
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

import android.util.Log
import com.merxury.blocker.core.di.ApplicationScope
import com.merxury.blocker.core.di.FilesDir
import com.merxury.blocker.core.dispatchers.BlockerDispatchers.IO
import com.merxury.blocker.core.dispatchers.Dispatcher
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import timber.log.Timber
import java.io.File
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject

const val LOG_DIR = "logs"

class ReleaseTree @Inject constructor(
    @FilesDir private val filesDir: File,
    @ApplicationScope private val coroutineScope: CoroutineScope,
    @Dispatcher(IO) private val ioDispatcher: CoroutineDispatcher,
) : Timber.DebugTree() {
    private val writeFile = AtomicReference<File>()
    private val initMutex: Mutex = Mutex()

    init {
        coroutineScope.launch(ioDispatcher) {
            initMutex.withLock {
                createLogFile()
                clearOldLogsIfNecessary(days = 7)
            }
        }
    }

    private suspend fun createLogFile() = withContext(ioDispatcher) {
        val logFolder = filesDir.resolve(LOG_DIR)
        if (!logFolder.exists()) {
            logFolder.mkdirs()
        }
        val date = Clock.System.todayIn(TimeZone.currentSystemDefault())
        val fileName = "$date.log"
        val logFile = logFolder.resolve(fileName)
        writeFile.set(logFile)
    }

    private suspend fun clearOldLogsIfNecessary(days: Int) = withContext(ioDispatcher) {
        val logFolder = filesDir.resolve(LOG_DIR)
        val files = logFolder.listFiles()
        if (files != null && files.size > days) {
            files.sortedBy { it.lastModified() }
                .take(files.size - days)
                .forEach { it.delete() }
        }
    }

    override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
        if (priority == Log.VERBOSE) {
            return
        }
        coroutineScope.launch(ioDispatcher) {
            // Wait until initMutex being unlocked
            if (initMutex.isLocked) {
                initMutex.withLock { }
            }
            val logFile = writeFile.get() ?: return@launch
            val time = Clock.System.now().toString()
            val level = when (priority) {
                Log.DEBUG -> "D"
                Log.INFO -> "I"
                Log.WARN -> "W"
                Log.ERROR -> "E"
                Log.ASSERT -> "A"
                else -> "Unknown"
            }
            val log = "$time $level/$tag: $message\n"
            logFile.appendText(log)
        }
    }
}
