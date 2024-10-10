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

package com.merxury.blocker.core.network

import com.merxury.blocker.core.network.io.BinaryFileWriter
import okhttp3.Call
import okhttp3.Request
import okhttp3.ResponseBody
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import javax.inject.Inject

class FileDownloader @Inject constructor(
    private val okhttpCallFactory: dagger.Lazy<Call.Factory>,
) {

    fun downloadFile(url: String, outputFilePath: String, onProgressUpdate: (Double) -> Unit) {
        val request = Request.Builder().url(url).build()
        okhttpCallFactory.get()
            .newCall(request)
            .execute()
            .use { response ->
                if (!response.isSuccessful) {
                    throw IOException("Failed to download file: HTTP ${response.code}")
                }

                val body: ResponseBody? = response.body
                val contentLength = body?.contentLength() ?: -1

                if (contentLength == -1L) {
                    throw IOException("Failed to get content length")
                }

                val inputStream = body?.byteStream()
                val outputFile = File(outputFilePath)

                try {
                    FileOutputStream(outputFile).use { outputStream ->
                        BinaryFileWriter(outputStream, onProgressUpdate).use { writer ->
                            writer.write(inputStream, contentLength)
                        }
                    }
                } catch (e: IOException) {
                    throw IOException("Error writing file to disk", e)
                }
            }
    }
}
