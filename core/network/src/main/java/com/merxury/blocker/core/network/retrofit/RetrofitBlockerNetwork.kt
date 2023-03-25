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
import com.merxury.blocker.core.network.model.GitHub
import com.merxury.blocker.core.network.model.GitLab
import com.merxury.blocker.core.network.model.NetworkChangeList
import com.merxury.blocker.core.network.model.NetworkComponentDetail
import com.merxury.blocker.core.network.model.NetworkGeneralRule
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

    @GET(value = "zh-cn/changeList")
    suspend fun getGeneralRuleChangeList(): List<NetworkChangeList>
}

/**
 * [Retrofit] backed [BlockerNetworkDataSource]
 */
@Singleton
class RetrofitBlockerNetwork @Inject constructor(
    @GitHub private val gitHubNetworkApi: BlockerNetworkApi,
    @GitLab private val gitLabNetworkApi: BlockerNetworkApi,
) : BlockerNetworkDataSource {

    private var networkApi: BlockerNetworkApi = gitHubNetworkApi

    override suspend fun getComponentData(path: String): NetworkComponentDetail? =
        networkApi.getOnlineComponentData(path)

    override suspend fun getGeneralRules(): List<NetworkGeneralRule> =
        networkApi.getGeneralRules()

    override suspend fun getGeneralRuleChangeList(): List<NetworkChangeList> =
        networkApi.getGeneralRuleChangeList()

    override fun changeServerProvider(provider: RuleServerProvider) {
        Timber.d("Switch backend API to $provider")
        networkApi = if (provider == GITHUB) {
            gitHubNetworkApi
        } else {
            gitLabNetworkApi
        }
    }
}
