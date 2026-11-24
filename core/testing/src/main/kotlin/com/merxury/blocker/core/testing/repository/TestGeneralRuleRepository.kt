/*
 * Copyright 2025 Blocker
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
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.channels.BufferOverflow.DROP_OLDEST
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

/**
 * Rules held in memory and set with [sendRuleList]. While [readFailure] is set, reading the rules
 * fails with it, as a damaged rule file would; [catalogReads] counts the reads of every rule and
 * [catalogGate] holds them back.
 */
class TestGeneralRuleRepository : GeneralRuleRepository {
    /**
     * The backing hot flow for the list of rule for testing.
     */
    private val rulesFlow: MutableSharedFlow<List<GeneralRule>> =
        MutableSharedFlow(replay = 1, onBufferOverflow = DROP_OLDEST)

    /** When set, collecting [getGeneralRules] or [getGeneralRule] fails with it. */
    var readFailure: Throwable? = null

    /** How often [getGeneralRules] was collected. */
    var catalogReads = 0

    /** When set, [getGeneralRules] emits only once it completes, as a slow read of every rule would. */
    var catalogGate: CompletableDeferred<Unit>? = null

    override fun getGeneralRules(): Flow<List<GeneralRule>> = flow {
        catalogReads++
        readFailure?.let { throw it }
        catalogGate?.await()
        emitAll(rulesFlow)
    }

    override fun getGeneralRule(id: Int): Flow<GeneralRule> = flow {
        readFailure?.let { throw it }
        emitAll(rulesFlow.map { rules -> rules.find { it.id == id }!! })
    }

    override fun updateGeneralRule(): Flow<Result<Unit>> = flowOf(Result.Success(Unit))

    override fun getRuleHash(): Flow<String> = flowOf("")

    override suspend fun saveGeneralRule(rule: GeneralRule) {
        // no-op
    }

    override fun searchGeneralRule(keyword: String): Flow<List<GeneralRule>> = rulesFlow.map { rules ->
        rules.filter { rule ->
            rule.name.contains(keyword, ignoreCase = true) ||
                rule.searchKeyword.contains(
                    keyword,
                )
        }
    }

    fun sendRuleList(ruleList: List<GeneralRule>) {
        this.rulesFlow.tryEmit(ruleList)
    }
}
