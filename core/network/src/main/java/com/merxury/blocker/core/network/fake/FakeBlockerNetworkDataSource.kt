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

package com.merxury.blocker.core.network.fake

import com.merxury.blocker.core.dispatchers.BlockerDispatchers.IO
import com.merxury.blocker.core.dispatchers.Dispatcher
import com.merxury.blocker.core.model.preference.RuleServerProvider
import com.merxury.blocker.core.network.BlockerNetworkDataSource
import com.merxury.blocker.core.network.JvmUnitTestFakeAssetManager
import com.merxury.blocker.core.network.io.BinaryFileWriter
import com.merxury.blocker.core.network.model.NetworkChangeList
import com.merxury.blocker.core.network.model.NetworkComponentDetail
import com.merxury.blocker.core.network.model.NetworkGeneralRule
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import okio.use
import javax.inject.Inject

/**
 * [BlockerNetworkDataSource] implementation that provides static news resources to aid development
 */
class FakeBlockerNetworkDataSource @Inject constructor(
    @Dispatcher(IO) private val ioDispatcher: CoroutineDispatcher,
    private val networkJson: Json,
    private val assets: FakeAssetManager = JvmUnitTestFakeAssetManager,
) : BlockerNetworkDataSource {
    override suspend fun getComponentData(path: String): NetworkComponentDetail? {
        return null
    }

    @OptIn(ExperimentalSerializationApi::class)
    override suspend fun getGeneralRules(): List<NetworkGeneralRule> {
        return withContext(ioDispatcher) {
            assets.open(RULES_ASSET).use(networkJson::decodeFromStream)
        }
    }

    override suspend fun getRuleLatestCommitId(): NetworkChangeList {
        return NetworkChangeList("")
    }

    override fun changeServerProvider(provider: RuleServerProvider) {
    }

    override suspend fun downloadRules(writer: BinaryFileWriter): Long = 0

    companion object {
        private const val RULES_ASSET = "rules.json"
    }
}
