/*
 * Copyright 2023 Blocker
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
import com.merxury.blocker.core.network.model.BinaryFileWriter
import com.merxury.blocker.core.network.model.GitHub
import com.merxury.blocker.core.network.model.GitLab
import com.merxury.blocker.core.network.model.NetworkChangeList
import com.merxury.blocker.core.network.model.NetworkComponentDetail
import com.merxury.blocker.core.network.model.NetworkGeneralRule
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Call
import okhttp3.Request
import retrofit2.Retrofit
import retrofit2.http.GET
import retrofit2.http.Path
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Retrofit API declaration for Blocker Network API
 */
interface BlockerNetworkApi {
    @GET("components/zh-cn/{path}")
    suspend fun getOnlineComponentData(@Path("path") relativePath: String): NetworkComponentDetail?

    @GET("zh-cn/general.json")
    suspend fun getGeneralRules(): List<NetworkGeneralRule>
}

/**
 * [Retrofit] backed [BlockerNetworkDataSource]
 */
@Singleton
class RetrofitBlockerNetwork @Inject constructor(
    private val okhttpCallFactory: Call.Factory,
    @GitHub private val gitHubNetworkApi: BlockerNetworkApi,
    @GitLab private val gitLabNetworkApi: BlockerNetworkApi,
) : BlockerNetworkDataSource {

    private var networkApi: BlockerNetworkApi = gitHubNetworkApi
    private var provider: RuleServerProvider = GITHUB

    override suspend fun getComponentData(path: String): NetworkComponentDetail? =
        networkApi.getOnlineComponentData(path)

    override suspend fun getGeneralRules(): List<NetworkGeneralRule> =
        networkApi.getGeneralRules()

    override suspend fun getRuleLatestCommitId(): NetworkChangeList {
        val request = Request.Builder()
            .url(provider.commitApiUrl)
            .build()
        return try {
            val json = okhttpCallFactory.newCall(request)
                .await()
                .body
                ?.string() ?: ""
            val commitId = getLatestCommitId(json)
            NetworkChangeList(commitId)
        } catch (e: Exception) {
            Timber.e("Failed to get latest commit id from $provider", e)
            NetworkChangeList("")
        }
    }

    override suspend fun downloadRules(writer: BinaryFileWriter): Long {
        val request = Request.Builder()
            .url(provider.downloadLink)
            .build()
        val response = okhttpCallFactory.newCall(request)
            .await()
        val responseBody = response.body
        if (responseBody == null) {
            Timber.e("Response body is null.")
            return 0
        }
        val length = response.header("Content-Length", "0")?.toLong() ?: 0L
        return writer.write(responseBody.byteStream(), length)
    }

    private fun getLatestCommitId(json: String): String {
        if (json.isBlank()) {
            Timber.e("Json is blank, cannot get latest commit id.")
            return ""
        }
        try {
            val elements = Json.parseToJsonElement(json)
            val firstElementInList = elements.jsonArray.firstOrNull()
            if (firstElementInList == null) {
                Timber.e("Cannot get first element in list.")
                return ""
            }
            val commitId = if (provider == GITHUB) {
                firstElementInList.jsonObject["sha"]?.jsonPrimitive?.content
            } else {
                firstElementInList.jsonObject["id"]?.jsonPrimitive?.content
            }
            if (commitId.isNullOrBlank()) {
                Timber.e("Cannot get commit id from json.")
                return ""
            }
            return commitId
        } catch (e: SerializationException) {
            Timber.e("The given string is not a valid JSON", e)
            return ""
        } catch (e: IllegalArgumentException) {
            Timber.e("Malformed JSON string", e)
            return ""
        }
    }

    override fun changeServerProvider(provider: RuleServerProvider) {
        Timber.d("Switch backend API to $provider")
        this.provider = provider
        networkApi = if (provider == GITHUB) {
            gitHubNetworkApi
        } else {
            gitLabNetworkApi
        }
    }
}
