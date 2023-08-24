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

package com.merxury.blocker.core.testing.repository

import com.merxury.blocker.core.data.respository.generalrule.GeneralRuleRepository
import com.merxury.blocker.core.model.data.GeneralRule
import com.merxury.blocker.core.result.Result
import kotlinx.coroutines.channels.BufferOverflow.DROP_OLDEST
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

class TestGeneralRuleRepository : GeneralRuleRepository {
    /**
     * The backing hot flow for the list of rule for testing.
     */
    private val rulesFlow: MutableSharedFlow<List<GeneralRule>> =
        MutableSharedFlow(replay = 1, onBufferOverflow = DROP_OLDEST)
    override fun getGeneralRules(): Flow<List<GeneralRule>> = rulesFlow

    override fun getGeneralRule(id: Int): Flow<GeneralRule> {
        return rulesFlow.map { rules -> rules.find { it.id == id }!! }
    }

    override fun updateGeneralRule(): Flow<Result<Unit>> {
        return flowOf(Result.Success(Unit))
    }

    override suspend fun saveGeneralRule(rule: GeneralRule) {
        rulesFlow.tryEmit(listOf(rule))
    }

    override fun searchGeneralRule(keyword: String): Flow<List<GeneralRule>> {
        return rulesFlow.map { rules ->
            rules.filter { rule ->
                rule.name.contains(keyword, ignoreCase = true) || rule.searchKeyword.contains(keyword)
            }
        }
    }
}
