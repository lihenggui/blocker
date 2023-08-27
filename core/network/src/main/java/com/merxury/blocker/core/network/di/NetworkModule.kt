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

package com.merxury.blocker.core.network.di

import android.content.Context
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import com.merxury.blocker.core.model.preference.RuleServerProvider
import com.merxury.blocker.core.network.BlockerNetworkDataSource
import com.merxury.blocker.core.network.BuildConfig
import com.merxury.blocker.core.network.fake.FakeAssetManager
import com.merxury.blocker.core.network.model.GitHub
import com.merxury.blocker.core.network.model.GitLab
import com.merxury.blocker.core.network.retrofit.BlockerNetworkApi
import com.merxury.blocker.core.network.retrofit.RetrofitBlockerNetwork
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.Call
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun okHttpCallFactory(): Call.Factory = OkHttpClient.Builder()
        .addInterceptor(
            HttpLoggingInterceptor()
                .apply {
                    if (BuildConfig.DEBUG) {
                        setLevel(HttpLoggingInterceptor.Level.HEADERS)
                    }
                },
        )
        .build()

    @Provides
    @Singleton
    fun providesNetworkJson(): Json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
        encodeDefaults = true
    }

    @Provides
    @Singleton
    fun provideBlockerNetworkDataSource(
        okHttpCallFactory: Call.Factory,
        @GitHub gitHubNetworkApi: BlockerNetworkApi,
        @GitLab gitLabNetworkApi: BlockerNetworkApi,
    ): BlockerNetworkDataSource {
        return RetrofitBlockerNetwork(okHttpCallFactory, gitHubNetworkApi, gitLabNetworkApi)
    }

    @Provides
    @Singleton
    fun providesFakeAssetManager(
        @ApplicationContext context: Context,
    ): FakeAssetManager = FakeAssetManager(context.assets::open)

    @Singleton
    @Provides
    @GitHub
    fun provideGitHubNetworkApi(
        okHttpCallFactory: Call.Factory,
        networkJson: Json,
    ): BlockerNetworkApi =
        provideBlockerNetworkApi(okHttpCallFactory, networkJson, RuleServerProvider.GITHUB.baseUrl)

    @Singleton
    @Provides
    @GitLab
    fun provideGitLabNetworkApi(
        okHttpCallFactory: Call.Factory,
        networkJson: Json,
    ): BlockerNetworkApi =
        provideBlockerNetworkApi(okHttpCallFactory, networkJson, RuleServerProvider.GITLAB.baseUrl)

    private fun provideBlockerNetworkApi(
        okHttpCallFactory: Call.Factory,
        networkJson: Json,
        url: String,
    ): BlockerNetworkApi {
        return Retrofit.Builder()
            .baseUrl(url)
            .callFactory(okHttpCallFactory)
            .addConverterFactory(
                networkJson.asConverterFactory("application/json".toMediaType()),
            )
            .build()
            .create(BlockerNetworkApi::class.java)
    }
}
