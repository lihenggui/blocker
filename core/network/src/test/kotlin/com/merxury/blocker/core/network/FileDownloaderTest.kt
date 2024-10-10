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

import okhttp3.Call
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.mockito.Mockito.mock
import org.mockito.kotlin.any
import org.mockito.kotlin.whenever
import java.io.ByteArrayInputStream
import java.io.IOException

class FileDownloaderTest {
    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var fileDownloader: FileDownloader
    private val callFactory: Call.Factory = mock()
    private val call: Call = mock()
    private val response: Response = mock()
    private val responseBody: ResponseBody = mock()

    @Before
    fun setUp() {
        whenever(callFactory.newCall(any())).thenReturn(call)
        whenever(call.execute()).thenReturn(response)
        whenever(response.isSuccessful).thenReturn(true)
        whenever(response.body).thenReturn(responseBody)

        fileDownloader = FileDownloader { callFactory }
    }

    @Test
    fun givenValidUrl_whenDownloadFile_thenFileIsDownloadedSuccessfully() {
        val responseBody = ByteArrayInputStream("file content".toByteArray())
            .readBytes()
            .toResponseBody("application/octet-stream".toMediaType())
        val response = Response.Builder()
            .request(Request.Builder().url("http://example.com").build())
            .protocol(Protocol.HTTP_1_1)
            .code(200)
            .message("OK")
            .body(responseBody)
            .build()

        whenever(call.execute()).thenReturn(response)

        val tempFile = tempFolder.newFile("test.tmp")

        fileDownloader.downloadFile("http://example.com", tempFile.absolutePath) { progress ->
            println("Progress: $progress")
        }

        // Verify the file content
        Assert.assertEquals("file content", tempFile.readText())
    }

    @Test(expected = IOException::class)
    fun givenHttpError_whenDownloadFile_thenThrowsIOException() {
        val response = Response.Builder()
            .request(Request.Builder().url("http://example.com").build())
            .protocol(Protocol.HTTP_1_1)
            .code(404)
            .message("Not Found")
            .body(ByteArray(0).toResponseBody(null))
            .build()

        whenever(call.execute()).thenReturn(response)

        fileDownloader.downloadFile("http://example.com", "outputPath") { _ -> }
    }

    @Test(expected = IOException::class)
    fun givenInvalidContentLength_whenDownloadFile_thenThrowsIOException() {
        val responseBody = mock(ResponseBody::class.java)
        whenever(responseBody.contentLength()).thenReturn(-1L)
        val response = Response.Builder()
            .request(Request.Builder().url("http://example.com").build())
            .protocol(Protocol.HTTP_1_1)
            .code(200)
            .message("OK")
            .body(responseBody)
            .build()

        whenever(call.execute()).thenReturn(response)

        fileDownloader.downloadFile("http://example.com", "outputPath") { _ -> }
    }

    @Test(expected = IOException::class)
    fun givenInvalidFilePath_whenDownloadFile_thenThrowsIOException() {
        val responseBody = ByteArrayInputStream("file content".toByteArray())
            .readBytes()
            .toResponseBody("application/octet-stream".toMediaType())
        val response = Response.Builder()
            .request(Request.Builder().url("http://example.com").build())
            .protocol(Protocol.HTTP_1_1)
            .code(200)
            .message("OK")
            .body(responseBody)
            .build()

        whenever(call.execute()).thenReturn(response)

        // Use a path that is likely to be invalid to trigger a write error
        fileDownloader.downloadFile("http://example.com", "/invalid/path") { _ -> }
    }
}
