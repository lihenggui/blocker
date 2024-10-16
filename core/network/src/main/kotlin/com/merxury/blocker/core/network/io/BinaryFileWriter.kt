/*
 * Copyright 2024 Blocker
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

package com.merxury.blocker.core.network.io

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import timber.log.Timber
import java.io.BufferedInputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

private const val CHUNK_SIZE = 1024

class BinaryFileWriter(
    private val outputStream: OutputStream,
) : AutoCloseable {

    private val _progress = MutableStateFlow(0.0)
    val progress: StateFlow<Double> = _progress.asStateFlow()

    @Throws(IOException::class)
    suspend fun write(inputStream: InputStream?, length: Long) {
        if (length.toInt() == 0) {
            Timber.w("Nothing to write, file length is 0")
            _progress.emit(0.0)
            return
        }
        BufferedInputStream(inputStream).use { input ->
            val dataBuffer =
                ByteArray(CHUNK_SIZE)
            var readBytes: Int
            var totalBytes: Long = 0
            _progress.emit(0.0)
            while (input.read(dataBuffer).also { readBytes = it } != -1) {
                totalBytes += readBytes.toLong()
                outputStream.write(dataBuffer, 0, readBytes)
                _progress.emit(totalBytes.toDouble() / length)
            }
        }
    }

    @Throws(IOException::class)
    override fun close() {
        outputStream.close()
    }
}
