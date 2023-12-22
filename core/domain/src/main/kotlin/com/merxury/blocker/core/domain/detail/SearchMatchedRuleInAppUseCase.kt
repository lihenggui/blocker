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

package com.merxury.blocker.core.domain.detail

import com.merxury.blocker.core.data.respository.component.ComponentRepository
import com.merxury.blocker.core.data.respository.generalrule.GeneralRuleRepository
import com.merxury.blocker.core.dispatchers.BlockerDispatchers.DEFAULT
import com.merxury.blocker.core.dispatchers.Dispatcher
import com.merxury.blocker.core.model.data.ComponentInfo
import com.merxury.blocker.core.model.data.GeneralRule
import com.merxury.blocker.core.result.Result
import com.merxury.blocker.core.result.asResult
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * A use case to search matched rules in an app
 */
class SearchMatchedRuleInAppUseCase @Inject constructor(
    private val componentRepository: ComponentRepository,
    private val ruleRepository: GeneralRuleRepository,
    @Dispatcher(DEFAULT) private val dispatcher: CoroutineDispatcher,
) {
    operator fun invoke(packageName: String): Flow<Result<Map<GeneralRule, List<ComponentInfo>>>> {
        return combine(
            ruleRepository.getGeneralRules(),
            componentRepository.getComponentList(packageName),
        ) { rules, components ->
            rules.mapNotNull { rule ->
                findMatchedComponents(rule, components)
            }.toMap()
        }.asResult()
    }

    private suspend fun findMatchedComponents(
        rule: GeneralRule,
        components: List<ComponentInfo>,
    ): Pair<GeneralRule, List<ComponentInfo>>? = withContext(dispatcher) {
        val matchedItem = components.filter { item ->
            // Check if the component name matched the keyword in the list
            val keywords = rule.searchKeyword
            keywords.forEach { keyword ->
                if (item.name.contains(keyword, true)) {
                    return@filter true
                }
            }
            return@filter false
        }
        if (matchedItem.isEmpty()) {
            return@withContext null
        }
        return@withContext rule to matchedItem
    }
}
