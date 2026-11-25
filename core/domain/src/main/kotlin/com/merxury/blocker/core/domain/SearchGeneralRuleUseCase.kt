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

package com.merxury.blocker.core.domain

import com.merxury.blocker.core.data.repository.generalrule.GeneralRuleRepository
import com.merxury.blocker.core.domain.rule.GeneralRuleMatcher
import com.merxury.blocker.core.domain.rule.GeneralRuleMatches
import com.merxury.blocker.core.model.component.UserId
import com.merxury.blocker.core.model.data.GeneralRule
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import javax.inject.Inject

/**
 * The general rules whose name contains a keyword, each with the number of
 * apps of one user it matches, see [GeneralRuleMatcher], and its icon as a file path. The rules
 * that match the most apps come first.
 */
class SearchGeneralRuleUseCase @Inject constructor(
    private val generalRuleRepository: GeneralRuleRepository,
    private val ruleMatcher: GeneralRuleMatcher,
) {
    /** The rules whose name contains [keyword], all of them when it is empty, with their matches among the apps of [user]. */
    operator fun invoke(user: UserId, keyword: String = ""): Flow<GeneralRuleSearchResults> = combine(generalRuleRepository.searchGeneralRule(keyword), ruleMatcher.matches(user)) { list, matches ->
        val rules = list.map { rule ->
            rule.copy(
                matchedAppCount = matches.appCountOf(rule.id),
            )
        }.sortedByDescending { it.matchedAppCount }
        GeneralRuleSearchResults(rules, matches)
    }
}

/** Rule counts and the component matches used to calculate them. */
data class GeneralRuleSearchResults(val rules: List<GeneralRule>, val matches: GeneralRuleMatches)
