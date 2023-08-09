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

package com.merxury.blocker.core.data.respository.fake

import com.merxury.blocker.core.data.respository.generalrule.GeneralRuleRepository
import com.merxury.blocker.core.dispatchers.BlockerDispatchers.IO
import com.merxury.blocker.core.dispatchers.Dispatcher
import com.merxury.blocker.core.model.data.GeneralRule
import com.merxury.blocker.core.network.fake.FakeBlockerNetworkDataSource
import com.merxury.blocker.core.result.Result
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/**
 * Fake implementation of the [GeneralRuleRepository] that retrieves the rules from a JSON String, and
 * uses a local DataStore instance to save and retrieve followed rule ids.
 *
 * This allows us to run the app with fake data, without needing an internet connection or working
 * backend.
 */

class FakeGeneralRuleRepository@Inject constructor(
    @Dispatcher(IO) private val ioDispatcher: CoroutineDispatcher,
    private val datasource: FakeBlockerNetworkDataSource,
) : GeneralRuleRepository {
    override fun getGeneralRules(): Flow<List<GeneralRule>> = flow {
        emit(
            datasource.getGeneralRules().map {
                GeneralRule(
                    id = it.id,
                    name = it.name,
                    iconUrl = it.iconUrl,
                    company = it.company,
                    description = it.description,
                    searchKeyword = it.searchKeyword,
                    useRegexSearch = it.useRegexSearch,
                    safeToBlock = it.safeToBlock,
                    sideEffect = it.sideEffect,
                    contributors = it.contributors,
                )
            },
        )
    }.flowOn(ioDispatcher)

    override fun getGeneralRule(id: Int): Flow<GeneralRule> {
        return getGeneralRules().map { it.first { rule -> rule.id == id } }
    }

    override fun updateGeneralRule(): Flow<Result<Unit>> {
        return flow {
            emit(Result.Success(Unit))
        }
    }

    override suspend fun saveGeneralRule(rule: GeneralRule) {
    }

    override fun searchGeneralRule(keyword: String): Flow<List<GeneralRule>> {
        return getGeneralRules().map { rules ->
            rules.filter { rule ->
                rule.name.contains(keyword, ignoreCase = true) || rule.searchKeyword.contains(keyword)
            }
        }
    }
}
