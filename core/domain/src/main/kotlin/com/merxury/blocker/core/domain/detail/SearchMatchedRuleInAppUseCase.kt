/*
 * Copyright 2024 Blocker
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

import com.merxury.blocker.core.data.di.RuleBaseFolder
import com.merxury.blocker.core.data.respository.component.ComponentRepository
import com.merxury.blocker.core.data.respository.componentdetail.ComponentDetailRepository
import com.merxury.blocker.core.data.respository.generalrule.GeneralRuleRepository
import com.merxury.blocker.core.di.FilesDir
import com.merxury.blocker.core.dispatchers.BlockerDispatchers.DEFAULT
import com.merxury.blocker.core.dispatchers.Dispatcher
import com.merxury.blocker.core.domain.model.MatchedHeaderData
import com.merxury.blocker.core.domain.model.MatchedItem
import com.merxury.blocker.core.model.data.ComponentInfo
import com.merxury.blocker.core.model.data.GeneralRule
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

/**
 * A use case to search matched rules in an app
 */
class SearchMatchedRuleInAppUseCase @Inject constructor(
    private val componentRepository: ComponentRepository,
    private val componentDetailRepository: ComponentDetailRepository,
    private val ruleRepository: GeneralRuleRepository,
    @FilesDir private val filesDir: File,
    @RuleBaseFolder private val ruleBaseFolder: String,
    @Dispatcher(DEFAULT) private val dispatcher: CoroutineDispatcher,
) {
    operator fun invoke(packageName: String): Flow<List<MatchedItem>> = combine(
        ruleRepository.getGeneralRules(),
        componentRepository.getComponentList(packageName),
    ) { rules, components ->
        rules.mapNotNull { rule ->
            findMatchedComponents(rule, components)
        }.map { (rule, matchedComponents) ->
            val iconUrl = rule.iconUrl?.let { url ->
                filesDir
                    .resolve(ruleBaseFolder)
                    .resolve(url)
            }
            val componentsWithDescription = matchedComponents.map { component ->
                val desc = componentDetailRepository.getLocalComponentDetail(component.name)
                    .first()
                    ?.description
                if (desc != null) {
                    component.copy(description = desc)
                } else {
                    component
                }
            }
            MatchedItem(
                header = MatchedHeaderData(
                    title = rule.name,
                    uniqueId = rule.id.toString(),
                    icon = iconUrl,
                ),
                componentList = componentsWithDescription,
            )
        }
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
