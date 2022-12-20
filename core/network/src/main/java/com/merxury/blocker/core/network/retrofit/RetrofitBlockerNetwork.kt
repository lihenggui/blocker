/*
 * Copyright 2022 Blocker
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

import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import com.merxury.blocker.core.network.BlockerNetworkDataSource
import com.merxury.blocker.core.network.BuildConfig
import com.merxury.blocker.core.network.model.NetworkChangeList
import com.merxury.blocker.core.network.model.NetworkComponentDetail
import com.merxury.blocker.core.network.model.NetworkGeneralRule
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * Retrofit API declaration for Blocker Network API
 */
interface BlockerNetworkApi {
    @GET("components/zh-cn/{path}")
    suspend fun getOnlineComponentData(@Path("path") relativePath: String): NetworkComponentDetail?

    @GET("zh-cn/general.json")
    suspend fun getGeneralRules(): List<NetworkGeneralRule>

    @GET(value = "zh-cn/changelists/generalRules")
    suspend fun getGeneralRuleChangeList(
        @Query("after") after: Int?,
    ): List<NetworkChangeList>
}

private const val BlockerBaseUrl = BuildConfig.BACKEND_URL

/**
 * [Retrofit] backed [BlockerNetworkDataSource]
 */
@Singleton
class RetrofitBlockerNetwork @Inject constructor(
    networkJson: Json
) : BlockerNetworkDataSource {

    private val networkApi = Retrofit.Builder()
        .baseUrl(BlockerBaseUrl)
        .client(
            OkHttpClient.Builder()
                .addInterceptor(
                    // TODO: Decide logging logic
                    HttpLoggingInterceptor().apply {
                        setLevel(HttpLoggingInterceptor.Level.BODY)
                    }
                )
                .build()
        )
        .addConverterFactory(
            @OptIn(ExperimentalSerializationApi::class)
            networkJson.asConverterFactory("application/json".toMediaType())
        )
        .build()
        .create(BlockerNetworkApi::class.java)

    override suspend fun getComponentData(path: String): NetworkComponentDetail? =
        networkApi.getOnlineComponentData(path)

    override suspend fun getGeneralRules(): List<NetworkGeneralRule> =
        networkApi.getGeneralRules()

    override suspend fun getGeneralRuleChangeList(after: Int?): List<NetworkChangeList> =
        networkApi.getGeneralRuleChangeList(after)
}
