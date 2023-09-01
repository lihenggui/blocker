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

import com.merxury.blocker.core.model.preference.RuleServerProvider
import com.merxury.blocker.core.network.BlockerNetworkDataSource
import com.merxury.blocker.core.network.io.BinaryFileWriter
import com.merxury.blocker.core.network.model.NetworkChangeList
import javax.inject.Inject

/**
 * [BlockerNetworkDataSource] implementation that provides static news resources to aid development
 */
class FakeBlockerNetworkDataSource @Inject constructor() : BlockerNetworkDataSource {

    override suspend fun getRuleLatestCommitId(provider: RuleServerProvider): NetworkChangeList {
        return NetworkChangeList("")
    }

    override suspend fun downloadRules(provider: RuleServerProvider, writer: BinaryFileWriter): Long = 0
}
