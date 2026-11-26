// Copyright 2026 Blocker
// SPDX-License-Identifier: Apache-2.0

package com.merxury.blocker.core.data.repository.catalog

import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStream
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

internal fun interface CatalogHttpFetcher {
    suspend fun fetch(url: String, consume: (InputStream, Long) -> Unit)
}

internal class CatalogNetworkException(message: String) : IOException(message)

@Singleton
internal class OkHttpCatalogFetcher @Inject constructor(client: OkHttpClient) : CatalogHttpFetcher {
    private val client = client.newBuilder()
        .followRedirects(true)
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .callTimeout(2, TimeUnit.MINUTES)
        .build()

    override suspend fun fetch(url: String, consume: (InputStream, Long) -> Unit) = coroutineScope {
        val request = Request.Builder().url(url).header("Accept-Encoding", "identity").build()
        val call = client.newCall(request)
        val cancelCall = launch(start = CoroutineStart.UNDISPATCHED) {
            try {
                awaitCancellation()
            } finally {
                call.cancel()
            }
        }
        try {
            networkIo { call.execute() }.use { response ->
                val status = response.code
                if (!response.isSuccessful) {
                    if (status in 400..499 && status != 408 && status != 429) {
                        throw InvalidCatalog("HTTP $status")
                    }
                    throw CatalogNetworkException("HTTP $status")
                }
                val body = response.body
                val input = object : FilterInputStream(body.byteStream()) {
                    override fun read(): Int = networkIo { super.read() }

                    override fun read(buffer: ByteArray, offset: Int, length: Int): Int = networkIo { super.read(buffer, offset, length) }
                }
                consume(input, body.contentLength())
            }
        } finally {
            cancelCall.cancel()
        }
    }

    // Redirect targets may contain signed query parameters. Report only the failure type.
    private inline fun <T> networkIo(block: () -> T): T = try {
        block()
    } catch (e: IOException) {
        throw CatalogNetworkException(e.javaClass.simpleName)
    }
}
