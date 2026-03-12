/*
 * Copyright 2025 Blocker
 * Copyright 2022 The Android Open Source Project
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

package com.merxury.blocker.core.network.retrofit

import com.merxury.blocker.core.model.preference.RuleServerProvider
import com.merxury.blocker.core.model.preference.RuleServerProvider.GITHUB
import com.merxury.blocker.core.network.BlockerNetworkDataSource
import com.merxury.blocker.core.network.io.BinaryFileWriter
import com.merxury.blocker.core.network.model.NetworkChangeList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Call
import okhttp3.Request
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * OkHttp backed [BlockerNetworkDataSource]
 */
@Singleton
internal class OkHttpBlockerNetwork @Inject constructor(
    private val okhttpCallFactory: dagger.Lazy<Call.Factory>,
    private val networkJson: Json,
) : BlockerNetworkDataSource {

    override suspend fun getRuleLatestCommitId(provider: RuleServerProvider): NetworkChangeList {
        val request = Request.Builder()
            .url(provider.commitApiUrl)
            .build()
        val json = okhttpCallFactory.get()
            .newCall(request)
            .await()
            .use { response ->
                if (!response.isSuccessful) {
                    throw NetworkException("Failed to get latest commit id: HTTP ${response.code}")
                }
                response.body.string()
            }
        val commitId = getLatestCommitId(provider, json)
        return NetworkChangeList(commitId)
    }

    override suspend fun downloadRules(
        provider: RuleServerProvider,
        writer: BinaryFileWriter,
    ): Long {
        Timber.d("Downloading rules from ${provider.downloadLink}")
        val request = Request.Builder()
            .url(provider.downloadLink)
            .build()
        val response = okhttpCallFactory.get()
            .newCall(request)
            .await()
        return response.use {
            if (!it.isSuccessful) {
                throw NetworkException("Failed to download rules: HTTP ${it.code}")
            }
            val responseBody = it.body
            val contentLength = responseBody.contentLength()
            if (contentLength == 0L) {
                Timber.e("Response body is empty.")
                return@use 0L
            }
            Timber.v("Zip length: $contentLength")
            withContext(Dispatchers.IO) {
                writer.write(responseBody.byteStream(), contentLength)
            }
        }
    }

    internal fun getLatestCommitId(provider: RuleServerProvider, json: String): String {
        if (json.isBlank()) {
            throw NetworkException("Empty response body when fetching commit id")
        }
        val elements = networkJson.parseToJsonElement(json)
        val firstElementInList = elements.jsonArray.firstOrNull()
            ?: throw NetworkException("Empty commit list in response")
        val commitId = if (provider == GITHUB) {
            firstElementInList.jsonObject["sha"]?.jsonPrimitive?.content
        } else {
            firstElementInList.jsonObject["id"]?.jsonPrimitive?.content
        }
        if (commitId.isNullOrBlank()) {
            throw NetworkException("Missing commit id in response JSON")
        }
        return commitId
    }
}
